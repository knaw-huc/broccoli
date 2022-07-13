package nl.knaw.huc.broccoli.resources

import io.swagger.v3.oas.annotations.Operation
import nl.knaw.huc.broccoli.api.AnnoTextResult
import nl.knaw.huc.broccoli.api.IIIFContext
import nl.knaw.huc.broccoli.api.Request
import nl.knaw.huc.broccoli.api.ResourcePaths.REPUBLIC
import nl.knaw.huc.broccoli.service.ResourceLoader
import org.eclipse.jetty.util.ajax.JSON
import org.slf4j.LoggerFactory
import javax.ws.rs.*
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path(REPUBLIC)
@Produces(MediaType.APPLICATION_JSON)
class RepublicResource {
    private val log = LoggerFactory.getLogger(javaClass)

    @GET
    @Path("v0")
    @Operation(description = "Get text, annotations and iiif details for a given volume and opening")
    fun getByCanvasId(
        @DefaultValue(defaultVolume) @QueryParam("volume") volume: String,
        @DefaultValue(defaultOpening) @QueryParam("opening") opening: Int
    ): Response {
        if (opening <= 0) {
            throw BadRequestException("Query parameter 'opening' must be >= 1")
        }
        log.info("volume: $volume, opening: $opening")
        return Response.ok(buildResult(volume, opening)).build()
    }

    private fun buildResult(volume: String, opening: Int): AnnoTextResult {
        val mockedAnnotations = loadMockAnnotations()

        return AnnoTextResult(
            request = Request(volume, opening),
            anno = mockedAnnotations.filter { !setOf("line", "column", "textregion").contains(getBodyValue(it)) },
            text = getMockedText(mockedAnnotations),
            iiif = IIIFContext(
                manifest = mockedManifestURL,
                canvasId = mockedCanvasId
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

    companion object {
        private const val defaultVolume = "1728"
        private const val defaultOpening = "285"

        private const val pimURL = "https://images.diginfra.net/api/pim"
        private const val imageSet = "67533019-4ca0-4b08-b87e-fd5590e7a077"
        private const val mockedManifestURL = "$pimURL/imageset/$imageSet/manifest"
        private const val canvasId = "75718d0a-5441-41fe-94c1-db773e0848e7"
        private const val mockedCanvasId = "$pimURL/iiif/$imageSet/canvas/$canvasId"
    }
}