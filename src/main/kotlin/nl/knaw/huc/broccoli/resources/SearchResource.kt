package nl.knaw.huc.broccoli.resources

import io.swagger.v3.oas.annotations.Operation
import nl.knaw.huc.broccoli.api.AnnoTextResult
import nl.knaw.huc.broccoli.api.ResourcePaths.SEARCH
import nl.knaw.huc.broccoli.service.ResourceLoader
import org.eclipse.jetty.util.ajax.JSON
import org.slf4j.LoggerFactory
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path(SEARCH)
@Produces(MediaType.APPLICATION_JSON)
class SearchResource {
    private val log = LoggerFactory.getLogger(javaClass)

    @GET
    @Path("canvas/{canvasId}")
    @Operation(description = "Get text and annotations for a given canvas id")
    fun getByCanvasId(@PathParam("canvasId") canvasId: String): Response {
        log.info("canvasId: $canvasId")
        return Response.ok(buildResult(canvasId)).build()
    }

    private fun buildResult(canvasId: String): AnnoTextResult {
        val anno = loadMockAnno()

        @Suppress("UNCHECKED_CAST")
        val text = getMockedText(anno as List<Map<String, Any>>)

        return AnnoTextResult(
            id = canvasId,
            anno = anno,
            text = text
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

    private fun loadMockAnno(): List<Any> {
        val mockedAnnos = ArrayList<Any>()
        for (i in 0..3) {
            val reader = ResourceLoader.asReader("mock/anno-page-$i.json")
            val annoPage = JSON.parse(reader)
            if (annoPage is HashMap<*, *>) {
                val items = annoPage["items"]
                if (items is Array<*>) {
                    @Suppress("UNCHECKED_CAST")
                    mockedAnnos.addAll(items as Array<Any>)
                } else {
                    log.info("AnnotationPage[\"items\"] not a list, skipping: $items.")
                }
            }
        }
        return mockedAnnos
    }
}