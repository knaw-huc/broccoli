package nl.knaw.huc.broccoli.resources.projects

import ElasticSearchClient
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.constraints.Min
import jakarta.ws.rs.*
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.core.GenericType
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.api.Constants.AR_BODY_TYPE
import nl.knaw.huc.broccoli.api.Constants.isIn
import nl.knaw.huc.broccoli.api.IndexQuery
import nl.knaw.huc.broccoli.api.TextMarker
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepo.Offset
import nl.knaw.huc.broccoli.service.anno.AnnoRepoSearchResult
import nl.knaw.huc.broccoli.service.anno.AnnoSearchResultInterpreter
import nl.knaw.huc.broccoli.service.anno.TextSelector
import nl.knaw.huc.broccoli.service.text.TextRepo
import org.slf4j.LoggerFactory
import org.slf4j.MarkerFactory

@Produces(MediaType.APPLICATION_JSON)
open class ProjectsResource(
    private val projects: Map<String, Project>,
    private val client: Client,
    private val jsonWriter: ObjectMapper,
    private val esClient: ElasticSearchClient
) {
    init {
        logger.info("init: projects=$projects, client=$client")
    }

    @GET
    @Operation(summary = "Get configured projects")
    open fun listProjects(): Set<String> = projects.keys

    @GET
    @Path("{projectId}")
    fun showDistinctBodyTypes(
        @PathParam("projectId") projectId: String
    ): Response = getProject(projectId).let {
        logger.info("Find bodyTypes in use in [${it.name}]: ")
        Response.ok(it.annoRepo.findDistinct(AR_BODY_TYPE)).build()
    }

    @GET
    @Path("{projectId}/views")
    fun getViews(@PathParam("projectId") projectId: String) = getProject(projectId).views


    @POST
    @Path("{projectId}/search")
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

        logger.atDebug()
            .setMessage("searchIndex")
            .addKeyValue("projectId", projectId)
            .addKeyValue("queryString", queryString)
            .addKeyValue("indexName", indexParam)
            .addKeyValue("from", from)
            .addKeyValue("size", size)
            .addKeyValue("fragmentSize", fragmentSize)
            .addKeyValue("sortBy", sortBy)
            .addKeyValue("sortOrder", sortOrder)
            .log()

        index.fields.map { it.name }
            .plus("_doc")
            .plus("_score")
            .apply {
                find { it == sortBy } ?: throw BadRequestException("query param sortBy must be one of ${this.sorted()}")
            }

        logQuery(queryString, from, size)

        val result = this.esClient.search(
            index,
            project.brinta.uri,
            queryString,
            Params(from, size, fragmentSize, sortBy, sortOrder)
        )
        return Response.ok(result).build()
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

    @Suppress("unused")
    enum class SortOrder {
        ASC, DESC;

        override fun toString() = name.lowercase()
    }

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

        val before = System.currentTimeMillis()

        val project = getProject(projectId)

        val annoRepo = project.annoRepo
        val textRepo = project.textRepo
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

        val textInterpreter = AnnoSearchResultInterpreter(searchResult, "Text")
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

        val views = mutableMapOf<String, Any>()
        interestedViews(project, requestedViews).forEach { (viewName, viewConf) ->
            val constraints = viewConf.anno.associate { it.path to it.value }
            annos.find { it.satisfies(constraints) }
                ?.let { viewAnno ->
                    val viewResult = mutableMapOf<String, Any>()

                    // fetch matching view anno's text based on project textType (meaning: do use LogicalText if needed)
                    viewResult["lines"] = fetchTextLines(
                        project.textRepo,
                        AnnoSearchResultInterpreter(viewAnno, project.textType).findTextSource()
                    )

                    val relocatedAnnotations = mutableListOf<Map<String, Any>>()

                    // now find included annos; must be based on 'Text' location (meaning: ignore LogicalText)
                    with(AnnoSearchResultInterpreter(viewAnno, "Text").findSelector()) {
                        // but: relocate anno's relative to the view's base anno (meaning: DO use LogicalText)
                        val baseSelector = AnnoSearchResultInterpreter(viewAnno, project.textType).findSelector()
                        annos.filter { it.bodyId() != viewAnno.bodyId() && it.liesWithin(start()..end()) }
                            .forEach { anno ->
                                val interpreter = AnnoSearchResultInterpreter(anno, project.textType)
                                val selector = interpreter.findSelector()
                                val annoStart = TextMarker(selector.start(), selector.beginCharOffset())
                                val annoEnd = TextMarker(selector.end(), selector.endCharOffset())
                                val annoMarkers = TextMarkers(annoStart, annoEnd).relativeTo(baseSelector.start())
                                relocatedAnnotations.add(
                                    mapOf(
                                        "bodyId" to anno.bodyId(),
                                        "start" to annoMarkers.start,
                                        "end" to annoMarkers.end
                                    )
                                )
                            }
                    }

                    if (relocatedAnnotations.isNotEmpty()) {
                        viewResult["locations"] = mapOf(
                            "relativeTo" to mapOf(
                                "bodyId" to viewAnno.bodyId(),
                                "bodyType" to viewAnno.bodyType()
                            ),
                            "annotations" to relocatedAnnotations
                        )
                    }

                    // store the view result we just built
                    views[viewName] = viewResult
                }
        }

        if (wanted.contains("text") && requestedViews.contains("self")) {
            val interpreter = AnnoSearchResultInterpreter(searchResult, project.textType)
            val textLines = timeExecution(
                { fetchTextLines(textRepo, interpreter.findTextSource()) },
                { timeSpent -> textTimings["fetchTextLines"] = timeSpent }
            )
            val textResult = mutableMapOf<String, Any>("lines" to textLines)

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

        return Response.ok(result).build()
    }

    private fun interestedViews(project: Project, interestedIn: Set<String>) =
        project.views.filterKeys { view -> interestedIn.contains(view) }


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

    private fun fetchTextLines(textRepo: TextRepo, textSourceUrl: String): List<String> {
        logger.info("GET {}", textSourceUrl)

        var builder = client.target(textSourceUrl).request()

        with(textRepo) {
            if (apiKey != null && canResolve(textSourceUrl)) {
                logger.info("with apiKey {}", apiKey)
                builder = builder.header(HttpHeaders.AUTHORIZATION, "Basic $apiKey")
            }
        }

        val resp = builder.get()

        if (resp.status == Response.Status.UNAUTHORIZED.statusCode) {
            logger.warn("Auth failed fetching $textSourceUrl")
            throw ClientErrorException("Need credentials for $textSourceUrl", Response.Status.UNAUTHORIZED)
        }

        return resp.readEntity(object : GenericType<List<String>>() {})
    }

    data class TextMarkers(val start: TextMarker, val end: TextMarker) {
        fun relativeTo(offset: Int): TextMarkers {
            return TextMarkers(start.relativeTo(offset), end.relativeTo(offset))
        }
    }

    companion object {
        const val ORIGIN = "Origin"
        private val logger = LoggerFactory.getLogger(ProjectsResource::class.java)
        private val queryMarker = MarkerFactory.getMarker("QRY")
    }

}
