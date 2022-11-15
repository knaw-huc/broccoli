package nl.knaw.huc.broccoli.resources

import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.api.ResourcePaths.REPUBLIC
import nl.knaw.huc.broccoli.service.IIIFStore
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.anno.AnnoRepo.TextSelector
import org.slf4j.LoggerFactory
import javax.ws.rs.*
import javax.ws.rs.client.Client
import javax.ws.rs.core.GenericType
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path(REPUBLIC)
@Produces(MediaType.APPLICATION_JSON)
class RepublicResource(
    private val volumeMapper: RepublicVolumeMapper,
    private val annoRepo: AnnoRepo,
    private val iiifStore: IIIFStore,
    private val client: Client
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GET
    @Path("/v3/volumes/{volumeId}/openings/{openingNr}")
    fun getVolumeOpening(
        @PathParam("volumeId") volumeId: String,
        @PathParam("openingNr") openingNr: Int
    ): Response {
        log.info("volumeId: $volumeId, openingNr: $openingNr")

        if (openingNr < 1) {
            throw BadRequestException("Path parameter 'openingNr' must be >= 1")
        }

        val volume = volumeMapper.byVolumeName(volumeId)
        val containerName = volumeMapper.buildContainerName(volume.name)
        val bodyId = volumeMapper.buildBodyId(volume, openingNr)

        val anno = annoRepo.findByBodyId(containerName, bodyId)
        val textTargets = anno.target<Any>("Text")
        log.info("textTargets: $textTargets")

        val textLines = ArrayList<String>()
        val annotations = ArrayList<Map<String, Any>>()
        textTargets.forEach {
            val sourceUrl = it["source"] as String
            if (it["selector"] == null) {
                textLines.addAll(fetchTextLines(sourceUrl))
            } else {
                @Suppress("UNCHECKED_CAST") val selector = it["selector"] as Map<String, Any>
                val start = selector["start"] as Int
                val end = selector["end"] as Int
                log.info("start: $start, end: $end")
                annotations.addAll(annoRepo.fetchOverlappingAnnotations(containerName, sourceUrl, start, end))
            }
        }

        return Response.ok(
            mapOf(
                "type" to "AnnoTextResult",
                "request" to mapOf(
                    "volumeId" to volumeId,
                    "openingNr" to openingNr
                ),
                "anno" to annotations,
                "text" to mapOf(
                    "location" to mapOf(
                        "relativeTo" to "TODO",
                        "start" to TextMarker(-1, -1, -1),
                        "end" to TextMarker(-1, -1, -1)
                    ),
                    "lines" to textLines
                ),
                "iiif" to mapOf(
                    "manifest" to iiifStore.manifest(volume.imageset),
                    "canvasIds" to listOf(iiifStore.getCanvasId(volume.name, openingNr))
                )
            )
        ).build()
    }

    @GET
    @Path("/v3/bodies/{bodyId}")
    // Both.../v3/bodies/urn:republic:session-1728-06-19-ordinaris-num-1-resolution-11?relativeTo=Session
    // and /v3/bodies/urn:republic:NL-HaNA_1.01.02_3783_0331 (Either NO ?relativeTo or MUST BE: relativeTo=Volume)
    fun getBodyIdRelativeTo(
        @PathParam("bodyId") bodyId: String, // could be resolutionId, sessionId, ...
        @QueryParam("relativeTo") @DefaultValue("Origin") relativeTo: String // e.g., "Scan", "Session" -> Enum? Generic?
    ): Response {
        val volume = volumeMapper.byBodyId(bodyId)
        val containerName = volumeMapper.buildContainerName(volume.name)
        val annoPage = annoRepo.findByBodyId(containerName, bodyId)
        val textTargets = annoPage.target<Any>("Text")

        val textTargetWithoutSelector = textTargets.find { it["selector"] == null }
            ?: throw WebApplicationException("annotation body $bodyId has no 'Text' target without selector")
        log.info("textTargetWithoutSelector: $textTargetWithoutSelector")
        val textLinesSource = textTargetWithoutSelector["source"] as String
        val textLines = fetchTextLines(textLinesSource)

        val textTargetWithSelector = textTargets.find { it["selector"] != null }
            ?: throw WebApplicationException("annotation body $bodyId has no 'Text' target with selector")

        log.info("textTargetWithSelector: $textTargetWithSelector")
        val textSegmentsSource = textTargetWithSelector["source"] as String
        @Suppress("UNCHECKED_CAST") val selector =
            TextSelector(textTargetWithSelector["selector"] as Map<String, Any>)

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
            "location" to if (relativeTo == "Origin") {
                mapOf("type" to "Origin", "id" to "")
            } else {
                val (offset, offsetId) = annoRepo.findOffsetRelativeTo(
                    containerName,
                    textSegmentsSource,
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

        return Response.ok(
            mapOf(
                "type" to "AnnoTextResult",
                "request" to mapOf(
                    "resolutionId" to bodyId,
                    "relativeTo" to relativeTo
                ),
                "anno" to annoPage.items(),
                "text" to mapOf(
                    "location" to location,
                    "lines" to getTextLines(annoPage),
                ),
                "iiif" to mapOf(
                    "manifest" to iiifStore.manifest(volume.imageset),
                    "canvasIds" to extractCanvasIds(annoPage)
                )
            )
        ).build()
    }

    private fun extractCanvasIds(annoPage: WebAnnoPage) = annoPage.targetField<String>("Canvas", "source")

    private fun getTextLines(annoPage: WebAnnoPage): List<String> {
        val textTargets = annoPage.target<String>("Text").filter { !it.containsKey("selector") }
        if (textTargets.size > 1) {
            log.warn("Multiple text targets (without selector) found, arbitrarily using the first: $textTargets")
        }
        return textTargets[0]["source"]?.let { fetchTextLines(it) }.orEmpty()
    }

    private fun fetchTextLines(textSourceUrl: String): List<String> {
        log.info("Fetching relevant text segments: $textSourceUrl")
        val startTime = System.currentTimeMillis()
        val resp = client.target(textSourceUrl).request().get()
        val result = resp.readEntity(object : GenericType<List<String>>() {})
        log.info("fetching took ${System.currentTimeMillis() - startTime} ms")
        return result
    }

    data class TextMarkers(val start: TextMarker, val end: TextMarker) {
        fun relativeTo(offset: Int): TextMarkers {
            return TextMarkers(start.relativeTo(offset), end.relativeTo(offset))
        }
    }


}

