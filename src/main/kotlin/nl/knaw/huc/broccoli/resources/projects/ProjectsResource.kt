package nl.knaw.huc.broccoli.resources.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.swagger.v3.oas.annotations.Operation
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.api.TextMarker
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepo.TextSelector
import nl.knaw.huc.broccoli.service.text.TextRepo
import org.slf4j.LoggerFactory
import javax.ws.rs.*
import javax.ws.rs.client.Client
import javax.ws.rs.core.GenericType
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path(PROJECTS)
@Produces(MediaType.APPLICATION_JSON)
class ProjectsResource(
    private val projects: Map<String, Project>,
    private val client: Client,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val objectMapper = ObjectMapper()

    init {
        log.debug("init: projects=$projects, client=$client")
    }

    @GET
    @Path("")
    @Operation(summary = "Get configured projects")
    fun listProjects(): Set<String> {
        return projects.keys
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
        val interestedIn = parseIncludeResults(includeResultsParam)
        val overlapTypes = parseOverlapTypes(overlapTypesParam)

        val annoTimings = mutableMapOf<String, Any>()
        val textTimings = mutableMapOf<String, Any>()
        val selfTimings = mutableMapOf<String, Any>()
        val profile = mapOf("anno" to annoTimings, "text" to textTimings, "self" to selfTimings)

        val result = mutableMapOf<String, Any>(
            "profile" to profile,
            "request" to mapOf(
                "projectId" to projectId,
                "bodyId" to bodyId,
                "includeResults" to interestedIn,
                "overlapTypes" to overlapTypesParam,
                "relativeTo" to relativeTo
            )
        )

        val searchResult = timeExecution { project.annoRepo.findByBodyId(bodyId) }
            .also { annoTimings["findByBodyId"] = it.first }
            .second
        log.info("searchResult: ${searchResult.items()}")

        if (interestedIn.contains("anno")) {
            result["anno"] = if (overlapTypesParam == null) {
                searchResult.items()
            } else {
                searchResult.withField<Any>("Text", "selector")
                    .also { if (it.size > 1) log.warn("multiple Text with selector: $it") }
                    .first()
                    .let { it ->
                        val selector = it["selector"] as Map<*, *>
                        val sourceUrl = it["source"] as String
                        val start = selector["start"] as Int
                        val end = selector["end"] as Int
                        val bodyTypes = Constants.isIn(overlapTypes)
                        timeExecution { project.annoRepo.fetchOverlap(sourceUrl, start, end, bodyTypes) }
                            .also { annoTimings["fetchOverlap"] = it.first }
                            .second
                    }
                    .also {
                        log.info("adding overlap anno: $it")
                    }
            }
        }

        if (interestedIn.contains("text")) {
            val withoutSelectorTargets = searchResult.withoutField<String>("Text", "selector")
            val withoutSelector = when {
                withoutSelectorTargets.isEmpty() -> throw NotFoundException("no text targets without 'selector' found")
                withoutSelectorTargets.size == 1 -> withoutSelectorTargets[0]
                else -> {
                    log.warn("multiple 'Text' targets without selector, arbitrarily picking the first")
                    withoutSelectorTargets[0]
                }
            }
            log.info("textTarget WITHOUT 'selector': $withoutSelector")
            val textLinesSource = withoutSelector["source"]
            val textLines = timeExecution { textLinesSource?.let { fetchTextLines(project.textRepo, it) }.orEmpty() }
                .also { textTimings["fetchTextLines"] = it.first }
                .second

            val withSelectorTargets = searchResult.withField<Any>("Text", "selector")
            val withSelector = when {
                withSelectorTargets.isEmpty() -> throw NotFoundException("no text target with 'selector' found")
                withSelectorTargets.size == 1 -> withSelectorTargets[0]
                else -> {
                    log.warn("multiple 'Text' targets with selector, arbitrarily picking the first")
                    withSelectorTargets[0]
                }
            }
            log.info("textTarget WITH 'selector': $withSelector")
            val segmentsSource = withSelector["source"] as String

            @Suppress("UNCHECKED_CAST")
            val selector = TextSelector(withSelector["selector"] as Map<String, Any>)

            val beginCharOffset = selector.beginCharOffset() ?: 0
            val start = TextMarker(selector.start(), beginCharOffset, textLines[0].length)
            log.info("start: $start")

            val lengthOfLastLine = textLines.last().length
            val endCharOffset = selector.endCharOffset() ?: (lengthOfLastLine - 1)
            val end = TextMarker(selector.end(), endCharOffset, lengthOfLastLine)
            log.info("end: $end")

            var markers = TextMarkers(start, end)
            log.info("markers (absolute): $markers")

            val location = mapOf(
                "relativeTo" to
                        if (relativeTo == "Origin") {
                            mapOf("type" to "Origin", "bodyId" to bodyId)
                        } else {
                            val (offset, offsetId) = timeExecution {
                                project.annoRepo.findOffsetRelativeTo(segmentsSource, selector, relativeTo)
                            }.also {
                                textTimings["findOffsetRelativeTo"] = it.first
                            }.second
                            markers = markers.relativeTo(offset)
                            log.info("markers (relative to $offsetId): $markers")
                            mapOf("type" to relativeTo, "bodyId" to offsetId)
                        },
                "start" to markers.start,
                "end" to markers.end
            )

            result["text"] = mapOf(
                "location" to location,
                "lines" to textLines
            )
        }

        if (interestedIn.contains("iiif")) {
            result["iiif"] = mapOf(
                "manifest" to "todo://get.manifest",
                "canvasIds" to searchResult.targetField<String>("Canvas", "source")
            )
        }

        val after = System.currentTimeMillis()
        selfTimings["total"] = after - before

        return Response.ok(result).build()
    }

    private fun <R> timeExecution(fn: () -> R): Pair<Long, R> {
        val before = System.currentTimeMillis()
        val result = fn()
        val after = System.currentTimeMillis()
        return Pair(after - before, result)
    }

    private fun getProject(projectId: String): Project {
        return projects[projectId]
            ?: throw NotFoundException("Unknown project: $projectId. See /projects for known projects")
    }

    private fun parseIncludeResults(includeResultString: String?): Set<String> {
        val all = setOf("anno", "text", "iiif")

        if (includeResultString == null) {
            return all
        }

        val requested = if (includeResultString.startsWith('[')) {
            objectMapper.readValue(includeResultString)
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
            objectMapper.readValue(overlapTypes)
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
