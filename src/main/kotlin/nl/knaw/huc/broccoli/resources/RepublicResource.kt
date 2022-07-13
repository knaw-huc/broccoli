package nl.knaw.huc.broccoli.resources

import com.jayway.jsonpath.JsonPath
import io.swagger.v3.oas.annotations.Operation
import nl.knaw.huc.broccoli.api.AnnoTextResult
import nl.knaw.huc.broccoli.api.IIIFContext
import nl.knaw.huc.broccoli.api.Request
import nl.knaw.huc.broccoli.api.ResourcePaths.REPUBLIC
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.service.ResourceLoader
import org.eclipse.jetty.util.ajax.JSON
import org.slf4j.LoggerFactory
import javax.ws.rs.*
import javax.ws.rs.client.Client
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path(REPUBLIC)
@Produces(MediaType.APPLICATION_JSON)
class RepublicResource(private val configuration: BroccoliConfiguration, private val client: Client) {
    private val log = LoggerFactory.getLogger(javaClass)

    @GET
    @Path("v0")
    @Operation(description = "Get text, annotations and iiif details for a given volume and opening")
    fun getVolumeOpening(
        @QueryParam("volume") _volume: String?,
        @QueryParam("opening") _opening: Int?
    ): Response {
        val volume = _volume ?: configuration.republic.defaultVolume
        val opening = _opening ?: configuration.republic.defaultOpening

        log.info("volume: $volume, opening: $opening")
        return Response.ok(buildResult(volume, opening)).build()
    }

    private fun buildResult(volume: String, opening: Int): AnnoTextResult {
        val imageSet = configuration.republic.volumes.find { it.name == volume }
            ?.imageset
            ?: throw NotFoundException("volume $volume not found in republic configuration")

        val target = client
            .target(configuration.iiifUrl)
            .path("imageset").path(imageSet).path("manifest")
        val manifest = target.uri.toString()

        val response = target.request().get()

        if (response.status != 200) {
            throw RuntimeException("502 Bad Gateway: upstream iiif ${configuration.iiifUrl} failed")
        }

        val json = JsonPath.parse(response.readEntity(String::class.java))

        val canvasIndex = opening - 1
        val canvasId = json.read<String>("\$.sequences[0].canvases[$canvasIndex].@id")
        log.info("id: $canvasId")

        val mockedAnnotations = loadMockAnnotations()
        return AnnoTextResult(
            request = Request(volume, opening),
            anno = mockedAnnotations.filter { !setOf("line", "column", "textregion").contains(getBodyValue(it)) },
            text = getMockedText(mockedAnnotations),
            iiif = IIIFContext(
                manifest = manifest,
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

        val reader = ResourceLoader.asReader("mock/text-lines.json")
        val json = JSON.parse(reader)
        log.info("json: $json")
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