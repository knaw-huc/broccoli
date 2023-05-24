package nl.knaw.huc.broccoli.resources.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jayway.jsonpath.ParseContext
import io.swagger.v3.oas.annotations.Operation
import nl.knaw.huc.broccoli.api.Constants.isIn
import nl.knaw.huc.broccoli.api.IndexQuery
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.api.TextMarker
import nl.knaw.huc.broccoli.config.IndexConfiguration
import nl.knaw.huc.broccoli.core.ElasticQueryBuilder
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoSearchResultInterpreter
import nl.knaw.huc.broccoli.service.anno.TextSelector
import nl.knaw.huc.broccoli.service.extractAggregations
import nl.knaw.huc.broccoli.service.text.TextRepo
import org.slf4j.LoggerFactory
import javax.validation.constraints.Min
import javax.ws.rs.*
import javax.ws.rs.client.Client
import javax.ws.rs.client.Entity
import javax.ws.rs.core.*

@Path(PROJECTS)
@Produces(MediaType.APPLICATION_JSON)
class ProjectsResource(
    private val projects: Map<String, Project>,
    private val client: Client,
    private val jsonParser: ParseContext,
    private val jsonWriter: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.info("init: projects=$projects, client=$client")
    }

    @GET
    @Path("")
    @Operation(summary = "Get configured projects")
    fun listProjects(): Set<String> = projects.keys

    @GET
    @Path("{projectId}")
    fun exploreIndex(
        @PathParam("projectId") projectId: String
    ): Response {
        val project = getProject(projectId)
        log.info("Find bodyTypes in use in [${project.name}]: ")
        return Response.ok(project.annoRepo.findDistinct().sorted()).build()
    }


    @POST
    @Path("{projectId}/search")
    fun searchIndex(
        @PathParam("projectId") projectId: String,
        queryString: IndexQuery,
        @QueryParam("indexName") indexParam: String?,
        @QueryParam("frag") @DefaultValue("scan") frag: FragOpts,
        @QueryParam("from") @Min(0) @DefaultValue("0") from: Int,
        @QueryParam("size") @Min(0) @DefaultValue("10") size: Int,
        @QueryParam("sortBy") @DefaultValue("_score") sortBy: String,
        @QueryParam("sortOrder") @DefaultValue("desc") sortOrder: SortOrder
    ): Response {
        val project = getProject(projectId)
        val index = getIndex(indexParam, project)

        log.info("sortBy=[$sortBy], sortOrder=[$sortOrder]")
        index.fields.map { it.name }
            .plus("_doc")
            .plus("_score")
            .apply {
                find { it == sortBy } ?: throw BadRequestException("query param sortBy must be one of ${this.sorted()}")
            }

        return queryString
            .also { log.info("queryString: ${jsonWriter.writeValueAsString(it)}") }
            .let {
                ElasticQueryBuilder(index)
                    .from(from)
                    .size(size)
                    .sortBy(sortBy)
                    .sortOrder(sortOrder.toString())
                    .frag(frag.toString())
                    .query(it)
                    .toElasticQuery()
            }
            .also { log.info("full ES query: ${jsonWriter.writeValueAsString(it)}") }
            .let { query ->
                client.target(project.brinta.uri).path(index.name).path("_search")
                    .request()
                    .post(Entity.json(query))
            }
            .also { log.info("response: $it") }
            .readEntityAsJsonString()
            .also { log.info("json: $it") }
            .let { json ->
                val result = mutableMapOf<String, Any>()
                jsonParser.parse(json).let { context ->
                    context.read<Map<String, Any>>("$.hits.total")
                        ?.let { result["total"] = it }

                    extractAggregations(context)?.let { result["aggs"] = it }

                    context.read<List<Map<String, Any>>>("$.hits.hits[*]")
                        ?.map { buildHitResult(index, it) }
                        ?.let { result["results"] = it }
                }
                Response.ok(result).build()
            }
    }

    private fun buildHitResult(index: IndexConfiguration, hit: Map<String, Any>) =
        mutableMapOf("_id" to hit["_id"]).apply {
            @Suppress("UNCHECKED_CAST")
            val source = hit["_source"] as Map<String, Any>

            // store all configured index fields with a result from elastic
            index.fields.forEach { field ->
                source[field.name]?.let { put(field.name, it) }
            }

            // store highlight if available
            hit["highlight"]
                ?.let { highlight ->
                    @Suppress("UNCHECKED_CAST")
                    val text = (highlight as Map<String, Any>)["text"]

                    @Suppress("UNCHECKED_CAST")
                    text?.let { buildPreviewAndLocations(source, it as List<String>) }
                }
                ?.let { previewAndLocations ->
                    mutableMapOf<String, MutableList<Map<String, TextMarker>>>()
                        .apply {
                            previewAndLocations.forEach { (preview, location) ->
                                getOrPut(preview) { mutableListOf() } += location
                            }
                        }
                }
                ?.asSequence()
                ?.map { (k, v) -> mapOf("preview" to k, "locations" to v) }
                ?.let { put("_hits", it) }
        }

    private fun buildPreviewAndLocations(
        source: Map<String, Any>,
        textLocations: List<String>
    ): List<Pair<String, Map<String, TextMarker>>> {
        // some running vars, updated as we visit each location to keep track of offsets
        var runningOffset = 0
        var curSegmentIndex = 0

        return textLocations
            .map { locationsAndPreviewExpr ->
                Pair(
                    locationsAndPreviewExpr.substringAfter('|'),
                    locationsAndPreviewExpr.substringBefore('|')
                )
            }
            .map { (preview, rangeAndLocationsExpr) ->
                Pair(preview, rangeAndLocationsExpr.substringBetweenOuter(':'))
            }
            .flatMap { (preview, locationsExpr) ->
                locationsExpr
                    .split(',')
                    .map { locationExpr -> Pair(preview, locationExpr) }
            }
            .map { (preview, locationExpr) -> Pair(preview, locationExpr.parseIntoCoordinates('-')) }
            .map { (preview, location) ->
                @Suppress("UNCHECKED_CAST")
                val segments = source["lengths"] as List<Int>

                var curSegmentLength = segments[curSegmentIndex]

                // skip lines entirely before start
                while (runningOffset + curSegmentLength < location.start) {
                    runningOffset += curSegmentLength + 1
                    curSegmentLength = segments[++curSegmentIndex]
                }
                val startMarker = TextMarker(curSegmentIndex, location.start - runningOffset)

                // skip lines entirely before end
                while (runningOffset + curSegmentLength < location.end) {
                    runningOffset += curSegmentLength + 1
                    curSegmentLength = segments[++curSegmentIndex]
                }
                val endMarker = TextMarker(curSegmentIndex, location.end - runningOffset - 1)

                Pair(preview, mapOf("start" to startMarker, "end" to endMarker))
            }
    }

    private fun String.substringBetweenOuter(delimiter: Char): String =
        substringAfter(delimiter).substringBeforeLast(delimiter)

    private data class Coordinates<T>(val start: T, val end: T)

    private fun String.parseIntoCoordinates(delimiter: Char): Coordinates<Int> =
        Coordinates(substringBefore(delimiter).toInt(), substringAfter(delimiter).toInt())

    @Suppress("unused")
    enum class FragOpts {
        NONE, SCAN, SENTENCE; // https://github.com/wikimedia/search-highlighter#elasticsearch-options

        override fun toString() = name.lowercase()
    }

    @Suppress("unused")
    enum class SortOrder {
        ASC, DESC;

        override fun toString() = name.lowercase()
    }


    private fun Response.readEntityAsJsonString(): String = readEntity(String::class.java) ?: ""

    @GET
    @Path("{projectId}/{bodyType}/{tiers: .*}")
    fun findByTiers(
        @PathParam("projectId") projectId: String,
        @PathParam("bodyType") bodyType: String,
        @PathParam("tiers") tierParams: String,
        @QueryParam("includeResults") @DefaultValue("bodyId") includeResultsParam: String,
    ): Response {
        val result = mutableMapOf<String, Any>()

        val project = getProject(projectId)

        val interestedIn = parseIncludeResults(setOf("anno", "bodyId"), includeResultsParam)

        val formalTiers = project.tiers
        val actualTiers = tierParams.split('/').filter { it.isNotBlank() }
        if (actualTiers.size != formalTiers.size) {
            throw BadRequestException("Must specify value for all formal tiers: $formalTiers, got $actualTiers instead")
        }

        result["request"] = mapOf(
            "projectId" to projectId,
            "bodyType" to bodyType,
            "tiers" to actualTiers,
            "includeResults" to interestedIn
        )

        val queryTiers = mutableListOf<Pair<String, Any>>()
        formalTiers.forEachIndexed { index, formalTier ->
            queryTiers.add(Pair(formalTier.name, formalTier.type.toAnnoRepoQuery(actualTiers[index])))
        }

        val searchResult = project.annoRepo.findByTiers(bodyType, queryTiers)
            .firstOrNull()
            ?: throw NotFoundException("Nothing found for $bodyType, $queryTiers")

        if (interestedIn.contains("bodyId")) {
            result["bodyId"] = searchResult.bodyId()
        }

        if (interestedIn.contains("anno")) {
            result["anno"] = searchResult.items()
        }

        return Response.ok(result)
            // TODO: add a Link header? -> which rel to use?
            //  https://www.iana.org/assignments/link-relations/link-relations.xhtml
            // .link(location, "canonical")
            .build()
    }

    @GET
    @Path("{projectId}/{bodyId}")
    @Operation(summary = "Get project's annotations by bodyId")
    fun getProjectBodyId(
        @PathParam("projectId") projectId: String,
        @PathParam("bodyId") bodyId: String,
        @QueryParam("includeResults") includeResultsParam: String?,
        @QueryParam("overlapTypes") overlapTypesParam: String?,
        @QueryParam("relativeTo") @DefaultValue("Origin") relativeTo: String,
    ): Response {
        log.info(
            "project=$projectId, bodyId=$bodyId, " +
                    "includeResults=$includeResultsParam, overlapTypes=$overlapTypesParam, relativeTo=$relativeTo"
        )

        val before = System.currentTimeMillis()

        val project = getProject(projectId)
        val annoRepo = project.annoRepo
        val textRepo = project.textRepo

        val interestedIn = parseIncludeResults(setOf("anno", "text", "iiif"), includeResultsParam)
        val overlapTypes = parseOverlapTypes(overlapTypesParam)

        val annoTimings = mutableMapOf<String, Any>()
        val textTimings = mutableMapOf<String, Any>()
        val selfTimings = mutableMapOf<String, Any>()
        val profile = mapOf("anno" to annoTimings, "text" to textTimings, "self" to selfTimings)

        val request = mutableMapOf(
            "projectId" to projectId,
            "bodyId" to bodyId,
            "includeResults" to interestedIn,
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

        if (interestedIn.contains("anno")) {
            result["anno"] = if (overlapTypes.isEmpty()) {
                searchResult.items()
            } else {
                searchResult.withField<Any>("Text", "selector")
                    .also { if (it.size > 1) log.warn("multiple Text with selector: $it") }
                    .first()
                    .let {
                        val selector = it["selector"] as Map<*, *>
                        val sourceUrl = it["source"] as String
                        val start = selector["start"] as Int
                        val end = selector["end"] as Int
                        val bodyTypes = isIn(overlapTypes)
                        timeExecution(
                            { annoRepo.fetchOverlap(sourceUrl, start, end, bodyTypes) },
                            { timeSpent -> annoTimings["fetchOverlap"] = timeSpent }
                        )
                    }
            }
        }

        if (interestedIn.contains("text")) {
            val textInterpreter = AnnoSearchResultInterpreter(searchResult)

            val textLines = timeExecution(
                { fetchTextLines(textRepo, textInterpreter.findTextSource()) },
                { timeSpent -> textTimings["fetchTextLines"] = timeSpent }
            )
            val textResult = mutableMapOf<String, Any>("lines" to textLines)

            val selector = textInterpreter.findSelector()
            val segmentsSource = textInterpreter.findSegmentsSource()

            if (interestedIn.contains("anno") && relativeTo != "Origin") {
                val offset = timeExecution(
                    { annoRepo.findOffsetRelativeTo(segmentsSource, selector, relativeTo) },
                    { timeSpent -> textTimings["findOffsetRelativeTo"] = timeSpent })

                val relocatedAnnotations = mutableListOf<Map<String, Any>>()
                (result["anno"] as List<*>).forEach { anno ->
                    if (anno is Map<*, *>) {
                        val annoBodyId = extractBodyId(anno)
                        val annoSelector = extractTextSelector(anno)

                        if (annoBodyId != null && annoSelector != null) {
                            val start = TextMarker(annoSelector.start(), annoSelector.beginCharOffset())
                            val end = TextMarker(annoSelector.end(), annoSelector.endCharOffset())
                            val markers = TextMarkers(start, end).relativeTo(offset.value)
                            relocatedAnnotations.add(
                                mapOf(
                                    "bodyId" to annoBodyId,
                                    "start" to markers.start,
                                    "end" to markers.end
                                )
                            )
                        }
                    }

                    if (relocatedAnnotations.isNotEmpty()) {
                        textResult["locations"] = mapOf(
                            "relativeTo" to mapOf("type" to relativeTo, "bodyId" to offset.id),
                            "annotations" to relocatedAnnotations
                        )
                    }
                }
            }

            result["text"] = textResult
        }

        if (interestedIn.contains("iiif")) {
            val manifest = searchResult.withField<Any>("Text", "selector")
                .also { if (it.size > 1) log.warn("multiple Text with selector: $it") }
                .first()
                .let {
                    val selector = it["selector"] as Map<*, *>
                    val sourceUrl = it["source"] as String
                    val start = selector["start"] as Int
                    val end = selector["end"] as Int
                    val tier0 = project.tiers[0].let { conf -> conf.anno ?: conf.name.capitalize() }
                    log.info("tier0: $tier0")
                    val bodyTypes = isIn(setOf(tier0))
                    timeExecution(
                        { annoRepo.fetchOverlap(sourceUrl, start, end, bodyTypes) },
                        { timeSpent -> annoTimings["fetchManifest"] = timeSpent }
                    )
                }
                .firstNotNullOfOrNull { extractManifest(it) }

            result["iiif"] = mapOf(
                "manifest" to manifest,
                "canvasIds" to searchResult.targetField<String>("Canvas", "source")
            )
        }

        val after = System.currentTimeMillis()
        selfTimings["total"] = after - before

        return Response.ok(result).build()
    }

    private fun String.capitalize(): String = replaceFirstChar(Char::uppercase)

    private fun extractManifest(anno: Map<*, *>): String? {
        if (anno.containsKey("body")) {
            val body = anno["body"]
            if (body is Map<*, *> && body.containsKey("metadata")) {
                val metadata = body["metadata"]
                if (metadata is Map<*, *> && metadata.containsKey("manifest")) {
                    return metadata["manifest"] as String
                }
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

    private fun extractTextSelector(anno: Map<*, *>): TextSelector? {
        if (anno.containsKey("target")) {
            (anno["target"] as List<*>).forEach { target ->
                if (target is Map<*, *> && target["type"] == "Text" && target.containsKey("selector")) {
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

    private fun parseIncludeResults(all: Set<String>, includeResultString: String?): Set<String> {
        if (includeResultString == null) {
            return all
        }

        val requested = if (includeResultString.startsWith('[')) {
            jsonWriter.readValue(includeResultString)
        } else {
            includeResultString
                .removeSurrounding("\"")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }

        val includes = all.intersect(requested)

        val undefined = requested.minus(all)
        if (undefined.isNotEmpty()) {
            throw BadRequestException("Undefined include: $undefined not in $all")
        }

        return includes
    }

    private fun parseOverlapTypes(overlapTypes: String?): Set<String> =
        if (overlapTypes == null) {
            emptySet()
        } else if (overlapTypes.startsWith('[')) {
            jsonWriter.readValue(overlapTypes)
        } else {
            overlapTypes
                .removeSurrounding("\"")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }

    private fun fetchTextLines(textRepo: TextRepo, textSourceUrl: String): List<String> {
        log.info("GET {}", textSourceUrl)

        var builder = client.target(textSourceUrl).request()

        with(textRepo) {
            if (apiKey != null && canResolve(textSourceUrl)) {
                log.info("with apiKey {}", apiKey)
                builder = builder.header(HttpHeaders.AUTHORIZATION, "Basic $apiKey")
            }
        }

        val resp = builder.get()

        if (resp.status == Response.Status.UNAUTHORIZED.statusCode) {
            log.warn("Auth failed fetching $textSourceUrl")
            throw ClientErrorException("Need credentials for $textSourceUrl", Response.Status.UNAUTHORIZED)
        }

        return resp.readEntity(object : GenericType<List<String>>() {})
    }

    data class TextMarkers(val start: TextMarker, val end: TextMarker) {
        fun relativeTo(offset: Int): TextMarkers {
            return TextMarkers(start.relativeTo(offset), end.relativeTo(offset))
        }
    }

}
