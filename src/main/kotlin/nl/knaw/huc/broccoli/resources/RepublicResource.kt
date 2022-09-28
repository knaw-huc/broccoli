package nl.knaw.huc.broccoli.resources

import io.swagger.v3.oas.annotations.Operation
import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.api.ResourcePaths.REPUBLIC
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import nl.knaw.huc.broccoli.service.IIIFStore
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import org.slf4j.LoggerFactory
import java.net.URI
import javax.ws.rs.*
import javax.ws.rs.client.Client
import javax.ws.rs.core.GenericType
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

private const val REPUBLIC_SESSION_PREFIX = "urn:republic:session"

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
            start = annoDetail.start,
            end = annoDetail.end,
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
                        "start" to annoDetail.start,
                        "end" to annoDetail.end
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
        val volume = volumeMapper.byResolutionId(resolutionId)
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
    @Path("/v3/volumes/{volumeId}/openings/{openingId}")
    fun getVolumeOpening(
        @PathParam("volumeId") volumeId: String,
        @PathParam("openingId") openingId: Int
    ): Response = TODO()

    @GET
    @Path("/v3/bodies/{bodyId}")
    // Both.../v3/bodies/urn:republic:session-1728-06-19-ordinaris-num-1-resolution-11?relativeTo=Session
    // and /v3/bodies/urn:republic:NL-HaNA_1.01.02_3783_0331 (Either NO ?relativeTo or MUST BE: relativeTo=Volume)
    fun getGenericAnnotationRelativeToContext(
        @PathParam("bodyId") bodyId: String, // could be resolutionId, sessionId, ...
        @QueryParam("relativeTo") relativeTo: String? // e.g., "Scan", "Session" -> Enum? Generic?
    ): Response = TODO()

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

class VolumeMapper(private val config: RepublicConfiguration) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun byResolutionId(resolutionId: String): RepublicVolume {
        return byVolumeId(deriveVolumeId(resolutionId))
    }

    fun byVolumeId(volumeId: String): RepublicVolume {
        return config.volumes.find { it.name == volumeId }
            ?: throw NotFoundException("Volume [$volumeId] not found in republic configuration")
    }

    private fun deriveVolumeId(resolutionId: String): String {
        if (!resolutionId.startsWith(REPUBLIC_SESSION_PREFIX)) {
            throw BadRequestException(
                "invalid resolutionId [$resolutionId]: expecting startsWith($REPUBLIC_SESSION_PREFIX)"
            )
        }

        val volumeId = resolutionId
            .substringAfter("$REPUBLIC_SESSION_PREFIX-")
            .substringBefore('-')
        log.info("resolutionId=[$resolutionId] -> derivedVolumeId=[$volumeId]")

        return volumeId
    }
}
