package nl.knaw.huc.broccoli.resources.projects

import io.swagger.v3.oas.annotations.Operation
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.api.TextMarker
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepo.TextSelector
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

    private val apiKey = "*REDACTED*" //TODO: from config

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
    @Path("{projectId}/bodies/{bodyId}")
    @Operation(summary = "Get project's annotations by bodyId")
    fun getProjectBodyId(
        @PathParam("projectId") projectId: String,
        @PathParam("bodyId") bodyId: String,
        @QueryParam("relativeTo") @DefaultValue("Origin") relativeTo: String,
        @QueryParam("include") includeString: String?,
    ): Response {
        log.info("prj=$projectId, bodyId=$bodyId, relativeTo=$relativeTo, include=$includeString")

        val project = getProject(projectId)

        val includes = parseIncludes(includeString)

        val result = mutableMapOf(
            "type" to "BodyIdResult",
            "request" to mapOf(
                "bodyId" to bodyId,
                "include" to includes,
                "relativeTo" to relativeTo
            )
        )

        val searchResult = project.annoRepo.findByBodyId(bodyId)
        log.info("searchResult: ${searchResult.items()}")

        if (includes.contains("anno")) {
            result["anno"] = searchResult.items()
        }

        if (includes.contains("text")) {
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
            val textLines = textLinesSource?.let { fetchTextLines(it) }.orEmpty()

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
                "location" to
                        if (relativeTo == "Origin") {
                            mapOf("type" to "Origin", "bodyId" to bodyId)
                        } else {
                            val (offset, offsetId) = project.annoRepo.findOffsetRelativeTo(
                                segmentsSource,
                                selector,
                                relativeTo
                            )
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

        if (includes.contains("iiif")) {
            result["iiif"] = mapOf(
                "manifest" to "todo://get.manifest",
                "canvasIds" to searchResult.targetField<String>("Canvas", "source")
            )
        }

        return Response.ok(result).build()
    }

    private fun getProject(projectId: String): Project {
        return projects[projectId]
            ?: throw NotFoundException("Unknown project: $projectId. See /projects for known projects")
    }

    private fun parseIncludes(includeString: String?): Set<String> {
        val all = setOf("anno", "text", "iiif")

        if (includeString == null) {
            return all
        }

        val requested = includeString
            .removeSurrounding("\"")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        val includes = all.intersect(requested)

        val undefined = requested.minus(all)
        if (undefined.isNotEmpty()) {
            throw BadRequestException("Undefined include: $undefined not in $all")
        }

        return includes
    }

    private fun fetchTextLines(textSourceUrl: String): List<String> {
        log.info("GET {}", textSourceUrl)
        var builder = client.target(textSourceUrl)
            .request()
        if (apiKey != null) {
            log.info("with apiKey {}", apiKey)
            builder = builder.header(HttpHeaders.AUTHORIZATION, "Basic $apiKey")
        }
        return builder
            .get()
            .readEntity(object : GenericType<List<String>>() {})
    }

    data class TextMarkers(val start: TextMarker, val end: TextMarker) {
        fun relativeTo(offset: Int): TextMarkers {
            return TextMarkers(start.relativeTo(offset), end.relativeTo(offset))
        }
    }

}
