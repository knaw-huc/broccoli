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

    private fun buildResult(canvasId: String) = AnnoTextResult(
        id = canvasId,
        anno = loadMockAnno(),
        text = listOf(
            "line1",
            "line2",
            "line3",
            "line4",
        )
    )

    @Suppress("UNCHECKED_CAST")
    private fun loadMockAnno(): List<Any> {
        var mockedAnnos = ArrayList<Any>()
        for (i in 0..3) {
            val reader = ResourceLoader.asReader("mock/anno-page-$i.json")
            val annoPage = JSON.parse(reader)
            if (annoPage is HashMap<*, *>) {
                val items = annoPage["items"]
                if (items is Array<*>) {
                    mockedAnnos.addAll(items as Array<Any>)
                } else {
                    log.info("AnnotationPage[\"items\"] not a list, skipping: $items.")
                }
            }
        }
        return mockedAnnos
    }
}