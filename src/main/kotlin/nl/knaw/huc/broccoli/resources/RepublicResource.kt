package nl.knaw.huc.broccoli.resources

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.swagger.v3.oas.annotations.Operation
import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.api.ResourcePaths.REPUBLIC
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import nl.knaw.huc.broccoli.service.IIIFStore
import nl.knaw.huc.broccoli.service.ResourceLoader
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import org.eclipse.jetty.util.ajax.JSON
import org.glassfish.jersey.client.ClientProperties
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

//    private val cache = HashMap<Pair<String, Int>, AnnoTextResult>()

    @GET
    @Path("v0")
    @Operation(
        deprecated = true,
        description = "Get mock text, annotations and iiif details for given volume, opening and bodyId (opt)"
    )
    fun getVolumeOpeningMocked(
        @QueryParam("volume") _volume: String?,
        @QueryParam("opening") _opening: Int?,
        @QueryParam("bodyId") _bodyId: String?
    ): Response {
        val volume = _volume ?: configuration.republic.defaultVolume
        val opening = _opening ?: configuration.republic.defaultOpening

        log.info("volume: $volume, opening: $opening, bodyId: $_bodyId")

        if (_bodyId == null) {
            return Response.ok(buildResult(volume, opening)).build()
        }

        val bodyId = _bodyId.removePrefix("urn:example:republic:")
        val path = "mock/$bodyId.json"
        log.info("path: $path")
        val reader = ResourceLoader.asStream(path)
        val body = jacksonObjectMapper().readValue(reader, AnnoTextBody::class.java)
        return Response.ok(body).build()
    }

    @GET
    @Path("v1")
    @Operation(description = "Get text, annotations and iiif details using AnnoRepo and TextRepo")
    fun getVolumeOpeningFromServers(
        @QueryParam("volume") _volumeId: String?,
        @QueryParam("opening") _openingNo: Int?,
        @QueryParam("bodyId") _bodyId: String?
    ): Response {
        val volumeId = _volumeId ?: configuration.republic.defaultVolume
        val openingNo = _openingNo ?: configuration.republic.defaultOpening

        log.info("volumeId: $volumeId, openingNo: $openingNo, bodyId: $_bodyId")

        val volume = configuration.republic.volumes.find { it.name == volumeId }
            ?: throw NotFoundException("Volume $volumeId not found in republic configuration")

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

        val annoDetail = annoRepo.getBodyId(volume, openingNo, _bodyId)
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

        val volume = configuration.republic.volumes.find { it.name == volumeId }
            ?: throw NotFoundException("Volume $volumeId not found in republic configuration")

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

        val annoDetail = annoRepo.getBodyId(volume, openingNo, _bodyId)
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
        val volumeId = resolutionId
            .substringAfter("urn:republic:session-")
            .substringBefore('-')

        log.info("getResolution: derivedVolumeId=[$volumeId], resolutionId=[$resolutionId]")

        val volume = configuration.republic.volumes.find { it.name == volumeId }
            ?: throw NotFoundException("Volume $volumeId not found in republic configuration")

        val resolution = annoRepo.getResolution(volume, resolutionId)

        val canvasIds = resolution.read<List<String>>("$.items[0].target[?(@.type == 'Canvas')].source")
        log.info("canvasIds: $canvasIds")

        val textTargets = resolution.read<List<Map<String, *>>>("$.items[0].target[?(@.type == 'Text')]")
        val textLines = getText(textTargets)
        log.info("textLines: $textLines")

        val anno = resolution.read<List<Map<String, Any>>>("$.items")
        return Response.ok(
            mapOf(
                "type" to "AnnoTextResult",
                "request" to mapOf(
                    "resolutionId" to resolutionId
                ),
                "anno" to anno,
                "text" to mapOf(
                    "location" to mapOf(
                        "relativeTo" to "TODO", // for later
                        "start" to TextMarker(-1, -1, -1),
                        "end" to TextMarker(-1, -1, -1)
                    ),
                    "lines" to textLines,
                ),
                "iiif" to mapOf(
                    "manifest" to manifest(volume),
                    "canvasIds" to canvasIds
                )
            )
        ).build()
    }

    @GET
    @Path("/v3/{bodyId}")
    // E.g., .../v3/urn:republic:session-1728-06-19-ordinaris-num-1-resolution-11?relativeTo=Session
    fun getGenericAnnotationRelativeToContext(
        @PathParam("bodyId") bodyId: String, // could be resolutionId, sessionId, ...
        @QueryParam("relativeTo") relativeTo: String // e.g., "Scan", "Session"
    ): Response = TODO()

    private fun getText(annoTargets: List<Map<String, *>>): List<String> {
        annoTargets.forEach {
            if (it["selector"] == null) {
                return fetchTextLines(it["source"] as String)
            }
        }
        log.info("No text found!")
        return emptyList()
    }

    private fun fetchTextLines(textSourceUrl: String): List<String> {
        log.info("Fetching relevant text segments: $textSourceUrl")
        val startTime = System.currentTimeMillis()
        val resp = client.target(textSourceUrl).request().get()
        val result = resp.readEntity(object : GenericType<List<String>>() {})
        log.info("fetching took ${System.currentTimeMillis() - startTime} ms")
        return result
    }

    private fun buildResult(volumeId: String, opening: Int, bodyId: String? = null): AnnoTextResult {
        if (opening < 1) {
            throw BadRequestException("Opening count starts at 1 (but got: $opening)")
        }

        val volume = configuration.republic.volumes.find { it.name == volumeId }
            ?: throw NotFoundException("Volume $volumeId not found in republic configuration")

        log.info("client.timeout (before call): ${client.configuration.getProperty(ClientProperties.READ_TIMEOUT)}")

        val canvasId = iiifStore.getCanvasId(volumeId, opening)
        val mockedAnnotations = loadMockAnnotations()

        val request = mapOf(
            "volumeId" to volumeId,
            "openingNo" to opening.toString()
        ).toMutableMap()

        if (bodyId != null) request["bodyId"] = bodyId

        return AnnoTextResult(
            request = request,
            anno = mockedAnnotations.filter { !setOf("line", "column", "textregion").contains(getBodyValue(it)) },
            text = getMockedText(mockedAnnotations),
            iiif = IIIFContext(
                manifest = manifest(volume),
                canvasId = canvasId
            )
        )
    }

    private fun getMockedText(annos: List<Map<String, Any>>): List<String> {
        val anno = annos[0]
        val id = anno["id"]
        log.info("anno[0].id = $id")

        val scanpage = annos.find { getBodyValue(it) == "scanpage" }.orEmpty()
        log.info("scanpage: $scanpage")

        val target = scanpage["target"] as Array<*>
        val textTarget = target.find { (it as HashMap<*, *>)["type"] == "Text" } as HashMap<*, *>
        val textSelector = textTarget["selector"] as HashMap<*, *>
        val start = textSelector["start"]
        val end = textSelector["end"]
        log.info("start=$start, end=$end")

        val mockedText = ArrayList<String>()

        val reader = ResourceLoader.asReader("mock/vol_1728-opening_285.json")
        val json = JSON.parse(reader)
        log.info("json: ${JSON.toString(json)}")
        if (json is Array<*>) {
            for (line in json) {
                if (line is String) {
                    mockedText.add(line)
                }
            }
        }
        return mockedText
    }

    private fun getBodyValue(anno: Map<String, Any>): String? {
        val body = anno["body"]
        return if (body is HashMap<*, *>) body["value"] as String else null
    }

    private fun loadMockAnnotations(): List<Map<String, Any>> {
        val mockedAnnos = ArrayList<Map<String, Any>>()
        for (i in 0..3) {
            val reader = ResourceLoader.asReader("mock/anno-page-$i.json")
            val annoPage = JSON.parse(reader)
            if (annoPage is HashMap<*, *>) {
                val items = annoPage["items"]
                if (items is Array<*>) {
                    @Suppress("UNCHECKED_CAST")
                    items.forEach { if (it is Map<*, *>) mockedAnnos.add(it as Map<String, Any>) }
                } else {
                    log.info("AnnotationPage[\"items\"] not a JSON array, skipping: $items.")
                }
            }
        }
        return mockedAnnos
    }
}