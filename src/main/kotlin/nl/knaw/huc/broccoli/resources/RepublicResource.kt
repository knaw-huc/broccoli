package nl.knaw.huc.broccoli.resources

import io.swagger.v3.oas.annotations.Operation
import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.api.ResourcePaths.REPUBLIC
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import nl.knaw.huc.broccoli.service.IIIFStore
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.anno.FetchingAnnoRepo.TextMarkers
import nl.knaw.huc.broccoli.service.anno.FetchingAnnoRepo.TextSelector
import org.slf4j.LoggerFactory
import java.net.URI
import javax.ws.rs.*
import javax.ws.rs.client.Client
import javax.ws.rs.core.GenericType
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path(REPUBLIC)
@Produces(MediaType.APPLICATION_JSON)
class RepublicResource(
    private val configuration: BroccoliConfiguration,
    private val annoRepo: AnnoRepo,
    private val iiifStore: IIIFStore,
    private val client: Client
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val volumeMapper = VolumeMapper(configuration.republic)

    @GET
    @Path("v1")
    @Operation(
        deprecated = true,
        description = "Get text, annotations and iiif details using AnnoRepo and TextRepo"
    )
    fun getVolumeOpeningFromServers(
        @QueryParam("volume") _volumeId: String?,
        @QueryParam("opening") _openingNo: Int?,
        @QueryParam("bodyId") _bodyId: String?
    ): Response {
        val volumeId = _volumeId ?: configuration.republic.defaultVolume
        val openingNo = _openingNo ?: configuration.republic.defaultOpening
        log.info("volumeId: $volumeId, openingNo: $openingNo, bodyId: $_bodyId")

        val volume = volumeMapper.byVolumeId(volumeId)

        if (_bodyId == null) {
            val scan = annoRepo.getScanAnno(volume, openingNo)
            val canvasId = iiifStore.getCanvasId(volume.name, openingNo)

            val result = AnnoTextResult(
                request = mapOf(
                    "volume" to volumeId,
                    "opening" to openingNo.toString()
                ),
                anno = scan.anno,
                text = scan.text,
                iiif = IIIFContext(
                    manifest = manifest(volume),
                    canvasId = canvasId
                )
            )
            return Response.ok(result).build()
        }

        val annoDetail = annoRepo.getRepublicBodyId(volume, openingNo, _bodyId)
        val result = AnnoTextBody(
            request = Request(volumeId, openingNo, _bodyId),
            start = annoDetail.markers.start,
            end = annoDetail.markers.end,
            text = annoDetail.text,
        )
        return Response.ok(result).build()
    }

    @GET
    @Path("v2")
    @Operation(description = "Get text, annotations and iiif details using AnnoRepo and TextRepo")
    fun getVolumeOpeningBodyId(
        @QueryParam("volume") _volumeId: String?,
        @QueryParam("opening") _openingNo: Int?,
        @QueryParam("bodyId") _bodyId: String?
    ): Response {
        val volumeId = _volumeId ?: configuration.republic.defaultVolume
        val openingNo = _openingNo ?: configuration.republic.defaultOpening
        log.info("volumeId: $volumeId, openingNo: $openingNo, bodyId: $_bodyId")

        val volume = volumeMapper.byVolumeId(volumeId)

        if (_bodyId == null) {
            val scan = annoRepo.getScanAnno(volume, openingNo)

            return Response.ok(
                mapOf(
                    "request" to mapOf(
                        "volumeId" to volumeId,
                        "opening" to openingNo
                    ),
                    "anno" to scan.anno,
                    "text" to mapOf(
                        "location" to mapOf(
                            "relativeTo" to "TODO", // for later
                            "start" to TextMarker(-1, -1, -1),
                            "end" to TextMarker(-1, -1, -1)
                        ),
                        "lines" to scan.text,
                    ),
                    "iiif" to mapOf(
                        "manifest" to manifest(volume),
                        "canvasIds" to listOf(iiifStore.getCanvasId(volume.name, openingNo))
                    )
                )
            ).build()
        }

        val annoDetail = annoRepo.getRepublicBodyId(volume, openingNo, _bodyId)
        return Response.ok(
            mapOf(
                "request" to mapOf(
                    "volumeId" to volumeId,
                    "opening" to openingNo,
                    "bodyId" to _bodyId
                ),
                "anno" to emptyList<String>(),
                "text" to mapOf(
                    "location" to mapOf(
                        "relativeTo" to "Scan",
                        "start" to annoDetail.markers.start,
                        "end" to annoDetail.markers.end
                    ),
                    "lines" to annoDetail.text
                ),
                "iiif" to mapOf(
                    "manifest" to manifest(volume),
                    "canvasIds" to listOf(iiifStore.getCanvasId(volume.name, openingNo))
                )
            )
        ).build()
    }

    private fun manifest(volume: RepublicVolume): URI =
        client.target(configuration.iiifUri)
            .path("imageset")
            .path(volume.imageset)
            .path("manifest").uri

    @GET
    @Path("v2/resolutions/{resolutionId}")
    @Operation(description = "Get text, annotations and iiif details for a given resolution")
    fun getResolution(
        @PathParam("resolutionId") resolutionId: String
    ): Response {
        val volume = volumeMapper.byBodyId(resolutionId)
        val annoPage = annoRepo.getBodyId(volume.name, resolutionId)

        return Response.ok(
            mapOf(
                "type" to "AnnoTextResult",
                "request" to mapOf(
                    "resolutionId" to resolutionId
                ),
                "anno" to annoPage.items(),
                "text" to mapOf(
                    "location" to mapOf(
                        "relativeTo" to "TODO", // for later
                        "start" to TextMarker(-1, -1, -1),
                        "end" to TextMarker(-1, -1, -1)
                    ),
                    "lines" to getTextLines(annoPage),
                ),
                "iiif" to mapOf(
                    "manifest" to manifest(volume),
                    "canvasIds" to extractCanvasIds(annoPage)
                )
            )
        ).build()
    }

    @GET
    @Path("/v3/volumes/{volumeId}/openings/{openingNr}")
    fun getVolumeOpening(
        @PathParam("volumeId") volumeId: String,
        @PathParam("openingNr") openingNr: Int
    ): Response {
        log.info("volumeId: $volumeId, openingNo: $openingNr")

        val volume = volumeMapper.byVolumeId(volumeId)

        if (openingNr < 1) {
            throw BadRequestException("Path parameter 'openingNr' must be >= 1")
        }

        val bodyId = volumeMapper.buildBodyId(volume, openingNr)
        log.info("constructed bodyId: $bodyId")

        val annoPage = annoRepo.getBodyId(volume.name, bodyId)
        return Response.ok(
            mapOf(
                "type" to "AnnoTextResult",
                "request" to mapOf(
                    "volumeId" to volumeId,
                    "openingNo" to openingNr
                ),
                "anno" to annoPage.items(),
                "text" to mapOf(
                    "location" to mapOf(
                        "relativeTo" to "TODO",
                        "start" to TextMarker(-1, -1, -1),
                        "end" to TextMarker(-1, -1, -1)
                    ),
                    "lines" to getTextLines(annoPage)
                ),
                "iiif" to mapOf(
                    "manifest" to manifest(volume),
                    "canvasIds" to extractCanvasIds(annoPage)
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
        val annoPage = annoRepo.getBodyId(volume.name, bodyId)
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

        val location = HashMap<String, Any>()
        if (relativeTo == "Origin") {
            location["relativeTo"] = mapOf("type" to "Origin", "id" to "")
        } else {
            val (offset, offsetId) = annoRepo.findOffsetRelativeTo(
                volume.name,
                textSegmentsSource,
                selector,
                relativeTo
            )
            markers = markers.relativeTo(offset)
            log.info("markers (relative to $offsetId): $markers")
            location["relativeTo"] = mapOf("type" to relativeTo, "bodyId" to offsetId)
        }
        location["start"] = markers.start
        location["end"] = markers.end

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
                    "manifest" to manifest(volume),
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
}

