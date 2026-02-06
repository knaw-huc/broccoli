package nl.knaw.huc.broccoli.resources.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jayway.jsonpath.ParseContext
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.constraints.Min
import jakarta.ws.rs.*
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.api.Constants.AR_BODY_TYPE
import nl.knaw.huc.broccoli.api.Constants.TEXT_TOKEN_COUNT
import nl.knaw.huc.broccoli.api.Constants.isIn
import nl.knaw.huc.broccoli.api.ElasticQuery
import nl.knaw.huc.broccoli.api.IndexQuery
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.config.IndexConfiguration
import nl.knaw.huc.broccoli.config.NamedViewConfiguration
import nl.knaw.huc.broccoli.core.ElasticQueryBuilder
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.log.RequestTraceLog
import nl.knaw.huc.broccoli.log.TraceLog
import nl.knaw.huc.broccoli.service.anno.AnnoRepo.Offset
import nl.knaw.huc.broccoli.service.anno.AnnoRepoSearchResult
import nl.knaw.huc.broccoli.service.anno.AnnoSearchResultInterpreter
import nl.knaw.huc.broccoli.service.anno.TextSelector
import nl.knaw.huc.broccoli.service.cache.LRUCache
import nl.knaw.huc.broccoli.service.extractAggregations
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory


@Path(PROJECTS)
@Produces(MediaType.APPLICATION_JSON)
class ProjectsResource(
    private val projects: Map<String, Project>,
    private val client: Client,
    private val jsonParser: ParseContext,
    private val jsonWriter: ObjectMapper,
    private val globalCache: LRUCache<Any, Any>? = null
) {
    init {
        logger.info("init: projects=$projects, client=$client")
    }

    @GET
    @Path("")
    @Operation(summary = "Get configured projects")
    fun listProjects(): Set<String> = projects.keys

    @GET
    @Path("{projectId}")
    fun showDistinctBodyTypes(
        @PathParam("projectId") projectId: String
    ): Response = getProject(projectId).let {
        logger.info("Find bodyTypes in use in [${it.name}]: ")
        Response.ok(it.annoRepo.findDistinct(AR_BODY_TYPE)).build()
    }

    @DELETE
    @Path("{projectId}/cache")
    fun invalidateCache(
        @PathParam("projectId") projectId: String
    ): Response {
        globalCache?.clear()
        getProject(projectId).annoRepo.invalidateCache()
        return Response.noContent().build()
    }

    @POST
    @Path("{projectId}/search")
    @RequestTraceLog
    @Consumes(MediaType.APPLICATION_JSON)
    fun searchIndex(
        queryString: IndexQuery,
        @PathParam("projectId") projectId: String,
        @QueryParam("indexName") indexParam: String?,
        @QueryParam("from") @Min(0) @DefaultValue("0") from: Int,
        @QueryParam("size") @Min(0) @DefaultValue("10") size: Int,
        @QueryParam("fragmentSize") @Min(0) @DefaultValue("100") fragmentSize: Int,
        @QueryParam("sortBy") @DefaultValue("_score") sortBy: String,
        @QueryParam("sortOrder") @DefaultValue("desc") sortOrder: SortOrder
    ): Response {
        val project = getProject(projectId)
        val index = getIndex(indexParam, project)

        validateSortByParam(index, sortBy)

        logQuery(queryString, from, size)

        val queryBuilder = ElasticQueryBuilder(index)
            .query(queryString)
            .from(from)
            .size(size)
            .sortBy(sortBy)
            .sortOrder(sortOrder.toString())
            .fragmentSize(fragmentSize)

        val baseQuery = queryBuilder.toElasticQuery()
        logger.atTrace().addKeyValue("ES query", jsonWriter.writeValueAsString(baseQuery)).log("base")

        val baseJson = runQuery(project.brinta.uri, index.name, baseQuery, queryString)

        val result: MutableMap<String, Any> = mutableMapOf()
        val aggs: MutableMap<String, Any> = mutableMapOf()
        jsonParser.parse(baseJson).let { context ->
            context.read<Map<String, Any>>("$.hits.total")
                ?.let { result["total"] = it }

            extractAggregations(index, context)?.let { aggsFromElastic ->
                aggsFromElastic.forEach { (aggKey, aggValues) ->
                    aggs[aggKey] = queryString.terms?.get(aggKey)?.let { askedFor ->
                        (aggValues as Map<*, *>).filterKeys { it in (askedFor as List<*>) }
                    } ?: aggValues
                }
            }
            logger.atTrace().addKeyValue("aggs", aggs).log("base")

            context.read<List<Map<String, Any>>>("$.hits.hits[*]")
                ?.map { buildHitResult(index, it) }
                ?.let { result["results"] = it }
        }

        val auxQueries = queryBuilder.toMultiFacetCountQueries()
        auxQueries.forEachIndexed { auxIndex, auxQuery ->
            logger.atTrace().addKeyValue("query[$auxIndex]", jsonWriter.writeValueAsString(auxQuery)).log("aux")

            val auxJson = runQuery(project.brinta.uri, index.name, auxQuery, queryString)

            jsonParser.parse(auxJson).let { context ->
                extractAggregations(index, context)
                    ?.forEach { entry ->
                        aggs[entry.key]?.let { agg ->
                            @Suppress("UNCHECKED_CAST")
                            (agg as MutableMap<String, Any>).putAll(entry.value as Map<String, Any>)
                        }
                    }
            }
        }

        // use LinkedHashMap to fix aggregation order
        result["aggs"] = LinkedHashMap<String, Any?>().apply {
            queryString.aggregations?.keys?.forEach { name ->
                val nameAndOrder = "$name@${queryString.aggregations[name]?.get("order")}"
                if (!aggs.containsKey(name) && aggs.containsKey(nameAndOrder)) {
                    aggs[name] = aggs[nameAndOrder] as Any
                }
                (aggs[name] as MutableMap<*, *>?)?.apply {
                    val desiredAmount: Int = (queryString.aggregations[name]?.get("size") as Int?) ?: size
                    if (desiredAmount < entries.size) {
                        val keep = LinkedHashMap<Any, Any>()
                        entries.take(desiredAmount).forEach {
                            keep[it.key as Any] = it.value as Any
                        }
                        aggs[name] = keep
                    }
                }
            }
            // prefer query string order; default to order from config
            (queryString.aggregations?.keys ?: index.fields.map { it.name }).forEach { name ->
                aggs[name]?.let { aggregationResult -> put(name, aggregationResult) }
            }
        }

        return Response.ok(result).build()
    }

    private fun validateSortByParam(index: IndexConfiguration, sortBy: String) {
        // sortBy must be one of the explicitly configured fields, or implicit '_doc' / '_score'
        index.fields.map { it.name }
            .plus("_doc")
            .plus("_score")
            .apply {
                find { it == sortBy }
                    ?: throw BadRequestException("query param sortBy must be one of ${sorted()}")
            }
    }

    @GET
    @Path("{projectId}/views")
    fun getViews(@PathParam("projectId") projectId: String) = getProject(projectId).views

    @TraceLog
    private fun runQuery(
        esUrl: String,
        indexName: String,
        baseQuery: ElasticQuery,
        queryString: IndexQuery
    ): String {
        val baseResult = client
            .target(esUrl)
            .path(indexName)
            .path("_search")
            .request()
            .post(Entity.json(baseQuery))
        validateElasticResult(baseResult, queryString)
        return baseResult.readEntityAsString()
    }

    private fun validateElasticResult(result: Response, queryString: IndexQuery) {
        if (result.status != 200) {
            logger.atWarn()
                .addKeyValue("status", result.status)
                .addKeyValue("query", queryString)
                .addKeyValue("result", result.readEntityAsString())
                .log("ElasticSearch failed")
            throw BadRequestException("Query not understood: $queryString")
        }
    }

    private fun logQuery(query: IndexQuery, from: Int, size: Int) {
        if (query.text.isNullOrBlank() && query.terms.isNullOrEmpty()) {
            // nothing useful to log
            return
        }

        logger.atInfo()
            .addMarker(queryMarker)
            .log("${query}from=$from|size=$size")
    }

    private fun buildHitResult(index: IndexConfiguration, hit: Map<String, Any>) =
        mutableMapOf("_id" to hit["_id"]).apply {
            // store highlight if available
            hit["highlight"]?.let { put("_hits", it) }

            @Suppress("UNCHECKED_CAST")
            val source = hit["_source"] as Map<String, Any>

            hit["fields"]?.let { fields ->
                @Suppress("UNCHECKED_CAST")
                (fields as Map<String, Any>)[TEXT_TOKEN_COUNT]?.let {
                    put("textTokenCount", (it as List<*>).first())
                }
            }

            // store all configured index fields with their search result, if any
            index.fields
                .filter { field -> field.type != "text" } // exclude text fields, can be very large, e.g. Translatin
                .forEach { field ->
                source[field.name]?.let { put(field.name, it) }
            }
        }

    @Suppress("unused")
    enum class SortOrder {
        ASC, DESC;

        override fun toString() = name.lowercase()
    }

    private fun Response.readEntityAsString(): String = readEntity(String::class.java) ?: ""

    data class ParamsAsKey(
        val projectId: String,
        val bodyId: String,
        val includeResults: String?,
        val views: String?,
        val overlapTypes: String?,
        val relevanceTypes: String,
    )

    @GET
    @Path("{projectId}/{bodyId}")
    @Operation(summary = "Get project's annotations by bodyId")
    fun getProjectBodyId(
        @PathParam("projectId") projectId: String,
        @PathParam("bodyId") bodyId: String,
        @QueryParam("includeResults") includesParam: String?,
        @QueryParam("views") viewsParam: String?,
        @QueryParam("overlapTypes") overlapTypesParam: String?,
        @QueryParam("relativeTo") @DefaultValue(ORIGIN) relativeTo: String,
    ): Response {

        logger.atInfo()
            .setMessage("getProjectBodyId")
            .addKeyValue("projectId", projectId)
            .addKeyValue("bodyId", bodyId)
            .addKeyValue("views", viewsParam)
            .addKeyValue("includeResults", includesParam)
            .addKeyValue("overlapTypes", overlapTypesParam)
            .addKeyValue("relativeTo", relativeTo)
            .log()

        val paramsAsKey = ParamsAsKey(projectId, bodyId, includesParam, viewsParam, overlapTypesParam, relativeTo)

        globalCache?.run {
            get(paramsAsKey)?.run {
                logger.atDebug().addKeyValue("key", paramsAsKey).log("cache hit")
                return Response.ok(this).build()
            }
            logger.atDebug().addKeyValue("key", paramsAsKey).log("cache miss")
        }

        val before = System.currentTimeMillis()

        val project = getProject(projectId)
        val annoRepo = project.annoRepo
        val textFetcher = project.textFetcher
        val allViews = project.views.keys.plus("self")
        val allIncludes = setOf("anno", "text", "iiif")

        val wanted = if (includesParam == null) allIncludes else parseRestrictedSubset(allIncludes, includesParam)
        val requestedViews = if (viewsParam == null) allViews else parseRestrictedSubset(allViews, viewsParam)
        val overlapTypes = if (overlapTypesParam == null) emptySet() else parseSet(overlapTypesParam)

        val annoTimings = mutableMapOf<String, Any>()
        val textTimings = mutableMapOf<String, Any>()
        val selfTimings = mutableMapOf<String, Any>()
        val profile = mapOf("anno" to annoTimings, "text" to textTimings, "self" to selfTimings)

        val request = mutableMapOf(
            "projectId" to projectId,
            "bodyId" to bodyId,
            "views" to requestedViews,
            "include" to wanted,
        )
        if (overlapTypes.isNotEmpty()) {
            request["overlapTypes"] = overlapTypes
        }
        request["relativeTo"] = relativeTo

        val result = mutableMapOf<String, Any>(
            "profile" to profile,
            "request" to request
        )

        val searchResult = timeExecution(
            { annoRepo.findByBodyId(bodyId) },
            { timeSpent -> annoTimings["findByBodyId"] = timeSpent }
        )

        val textInterpreter = AnnoSearchResultInterpreter(searchResult, TEXT_TYPE)
        val textSource = textInterpreter.findSegmentsSource()
        val textSelector = textInterpreter.findSelector()

        val annos: List<AnnoRepoSearchResult> = if (wanted.contains("anno")) {
            if (overlapTypes.isEmpty()) {
                listOf(searchResult)
            } else {
                timeExecution({
                    annoRepo.fetchOverlap(textSource, textSelector.start(), textSelector.end(), isIn(overlapTypes))
                        .map { AnnoRepoSearchResult(it) }
                }, { timeSpent -> annoTimings["fetchOverlap[text]"] = timeSpent })
            }
        } else emptyList()

        if (annos.isNotEmpty()) {
            result["anno"] = annos.map { it.root() }
        }

        val views: MutableMap<String, Any> = mutableMapOf()
        interestedViews(project, requestedViews).forEach { (viewName, viewConf) ->
            val constraints = viewConf.anno.associate { it.path to it.values }
            annos.filter { it.satisfies(constraints) }
                .forEach { viewAnno ->
                    logger.atInfo()
                        .addKeyValue("annoId", viewAnno.bodyId())
                        .addKeyValue("type", viewAnno.bodyType())
                        .addKeyValue("items", viewAnno.items())
                        .log("satisfies constraints")

                    val viewResult: MutableMap<String, Any> = mutableMapOf()

                    val findWithin = viewConf.findWithin
                    if (findWithin == null) {
                        // fetch matching view anno's text based on project textType (meaning: do use LogicalText if needed)
                        viewResult[BODY] = textFetcher.fetchText(
                            AnnoSearchResultInterpreter(viewAnno, project.textType).findTextSource()
                        )

                        val relocatedAnnotations: MutableList<Map<String, Any>> = mutableListOf()

                        // now find included annos; must be based on 'Text' location (meaning: ignore LogicalText)
                        with(AnnoSearchResultInterpreter(viewAnno, TEXT_TYPE).findSelector()) {
                            // but: relocate anno's relative to the view's base anno (meaning: DO use LogicalText)
                            val baseSelector = AnnoSearchResultInterpreter(viewAnno, project.textType).findSelector()
                            annos.filter { it.bodyId() != viewAnno.bodyId() && it.liesWithin(start()..end()) }
                                .forEach { anno ->
                                    val interpreter = AnnoSearchResultInterpreter(anno, project.textType)
                                    val selector = interpreter.findSelector()
                                    relocatedAnnotations.add(
                                        mapOf(
                                            "bodyId" to anno.bodyId(),
                                            "begin" to selector.start() - baseSelector.start(),
                                            "end" to selector.end() - baseSelector.start(),
                                        )
                                    )
                                }
                        }

                        viewResult["locations"] = mapOf(
                            "relativeTo" to mapOf(
                                "bodyId" to viewAnno.bodyId(),
                                "bodyType" to viewAnno.bodyType()
                            ),
                            "annotations" to relocatedAnnotations
                        )
                    } else {
                        with(AnnoSearchResultInterpreter(viewAnno, TEXT_TYPE).findSelector()) {
                            annos
                                .filter { it.read("$.${findWithin.path}") == findWithin.value }
                                .filter { it.liesWithin(start()..end()) }
                                .forEach { innerNote ->
                                    val noteResult: MutableMap<String, Any> = mutableMapOf()
                                    val noteGroup = innerNote.read(findWithin.groupBy).toString()
                                    noteResult[BODY] = textFetcher.fetchText(
                                        AnnoSearchResultInterpreter(innerNote, project.textType).findTextSource()
                                    )

                                    val relocated: MutableList<Map<String, Any>> = mutableListOf()
                                    with(AnnoSearchResultInterpreter(innerNote, TEXT_TYPE).findSelector()) {
                                        val base = AnnoSearchResultInterpreter(innerNote, project.textType)
                                            .findSelector()
                                        annos
                                            .filter { it.bodyId() != innerNote.bodyId() }
                                            .filter { it.liesWithin(start()..end()) }
                                            .forEach { a ->
                                                val interpreter = AnnoSearchResultInterpreter(a, project.textType)
                                                val selector = interpreter.findSelector()
                                                relocated.add(
                                                    mapOf(
                                                        "bodyId" to a.bodyId(),
                                                        "begin" to selector.start() - base.start(),
                                                        "end" to selector.end() - base.start()
                                                    )
                                                )
                                            }

                                        noteResult["locations"] = mapOf(
                                            "relativeTo" to mapOf(
                                                "bodyId" to innerNote.bodyId(),
                                                "bodyType" to innerNote.bodyType()
                                            ),
                                            "annotations" to relocated
                                        )
                                    }

                                    viewResult[noteGroup] = noteResult
                                }
                        }
                    }

                    val groupBy = viewAnno.read(viewConf.groupBy ?: "body.id").toString().let {
                        if (it == "null" || it.isEmpty() || it.isBlank()) { // wing it
                            "${viewAnno.bodyId()}_has_unusable_${viewConf.groupBy}".also { str -> logger.warn(str) }
                        } else it
                    }

                    // store the view result we just built
                    val view = views.getOrPut(viewName) { mutableMapOf<String, Any>() }
                    @Suppress("UNCHECKED_CAST")
                    (view as MutableMap<String, MutableMap<String, Any>>)
                        .merge(groupBy, viewResult) { base, more -> base.plus(more).toMutableMap() }
                }
        }

        if (wanted.contains("text") && requestedViews.contains("self")) {
            val interpreter = AnnoSearchResultInterpreter(searchResult, project.textType)
            val textLines = timeExecution(
                { textFetcher.fetchText(interpreter.findTextSource()) },
                { timeSpent -> textTimings["fetchTextLines"] = timeSpent }
            )
            val textResult = mutableMapOf<String, Any>(BODY to textLines)

            if (wanted.contains("anno")) {
                val offset = when (relativeTo) {
                    ORIGIN -> Offset(interpreter.findSelector().start(), interpreter.bodyId())
                    else -> {
                        timeExecution({
                            annoRepo.findOffsetRelativeTo(
                                textInterpreter.findSegmentsSource(),
                                textInterpreter.findSelector(),
                                relativeTo
                            )
                        }, { timeSpent -> textTimings["findOffsetRelativeTo"] = timeSpent })
                    }
                }

                val relocatedAnnotations = mutableListOf<Map<String, Any>>()
                (result["anno"] as List<*>).forEach { anno ->
                    if (anno is Map<*, *>) {
                        val annoBodyId = extractBodyId(anno)
                        val annoSelector = extractTextSelector(project.textType, anno)

                        if (annoBodyId != null && annoSelector != null) {
                            relocatedAnnotations.add(
                                mapOf(
                                    "bodyId" to annoBodyId,
                                    "begin" to annoSelector.start() - offset.value,
                                    "end" to annoSelector.end() - offset.value
                                )
                            )
                        }
                    }
                }

                if (relocatedAnnotations.isNotEmpty()) {
                    textResult["locations"] = mapOf(
                        "relativeTo" to mapOf("type" to relativeTo, "bodyId" to offset.id),
                        "annotations" to relocatedAnnotations
                    )
                }
            }
            views["self"] = textResult
        }

        if (views.isNotEmpty()) result["views"] = views

        if (wanted.contains("iiif")) {
            val bodyTypes = isIn(setOf(project.topTierBodyType))
            val manifest = timeExecution({
                annoRepo.fetchOverlap(textSource, textSelector.start(), textSelector.end(), bodyTypes)
                    .map { it.read<Map<String, Any>>("$") }.toList()
            }, { timeSpent -> annoTimings["fetchManifest"] = timeSpent }
            ).firstNotNullOfOrNull { extractManifest(it) }

            result["iiif"] = mapOf(
                "manifest" to manifest,
                "canvasIds" to searchResult.targetField<String>("Canvas", "source")
            )
        }

        val after = System.currentTimeMillis()
        selfTimings["total"] = after - before

        globalCache?.run {
            logger.atDebug().addKeyValue("key", paramsAsKey).log("caching")
            put(paramsAsKey, result)
        }

        return Response.ok(result).build()
    }

    private fun interestedViews(project: Project, interestedIn: Set<String>): Map<String, NamedViewConfiguration> =
        project.views.filterKeys { view -> interestedIn.contains(view) }


    private fun extractManifest(anno: Map<*, *>): String? {
        if (anno.containsKey("body")) {
            val body = anno["body"]
            if (body is Map<*, *> && body.containsKey("manifest")) {
                return body["manifest"] as String
            }
        }
        return null
    }

    private fun extractBodyId(anno: Map<*, *>): String? {
        if (anno.containsKey("body")) {
            val body = anno["body"]
            if (body is Map<*, *> && body.containsKey("id")) {
                return body["id"] as String
            }
        }
        return null
    }

    private fun extractTextSelector(textType: String, anno: Map<*, *>): TextSelector? {
        if (anno.containsKey("target")) {
            (anno["target"] as List<*>).forEach { target ->
                if (target is Map<*, *> && target["type"] == textType && target.containsKey("selector")) {
                    @Suppress("UNCHECKED_CAST")
                    val selector = target["selector"] as Map<String, Any>
                    return TextSelector(selector)
                }
            }
        }
        return null
    }

    private fun <R> timeExecution(workToBeTimed: () -> R, storeTimeSpent: (Long) -> Unit): R {
        val before = System.currentTimeMillis()
        val result = workToBeTimed()
        storeTimeSpent(System.currentTimeMillis() - before)
        return result
    }

    private fun getProject(projectId: String): Project {
        return projects[projectId]
            ?: throw NotFoundException("Unknown project: $projectId. See /projects for known projects")
    }

    private fun getIndex(indexParam: String?, project: Project) =
        indexParam
            ?.let { indexName ->
                project.brinta.indices.find { it.name == indexName }
                    ?: throw NotFoundException(
                        "Unknown index: $indexParam. See /brinta/${project.name}/indices for known indices"
                    )
            }
            ?: project.brinta.indices.first() // if unspecified, use first available index from config

    private fun parseRestrictedSubset(all: Set<String>, subsetString: String): Set<String> {
        val requested = parseSet(subsetString)

        val undefined = requested.minus(all)
        if (undefined.isNotEmpty()) {
            throw BadRequestException("Undefined parameter: $undefined not in $all")
        }

        return all.intersect(requested)
    }

    private fun parseSet(items: String): Set<String> =
        if (items.startsWith('[')) {
            jsonWriter.readValue(items)
        } else {
            items
                .removeSurrounding("\"")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }

    companion object {
        private const val TEXT_TYPE = "NormalText"
        private const val ORIGIN = "Origin"
        private const val BODY = "body"
        private val logger = LoggerFactory.getLogger(ProjectsResource::class.java)
        private val queryMarker = MarkerFactory.getMarker("QRY")
    }

}
