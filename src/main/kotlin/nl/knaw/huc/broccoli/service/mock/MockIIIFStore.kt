package nl.knaw.huc.broccoli.service.mock

import com.jayway.jsonpath.JsonPath
import nl.knaw.huc.broccoli.service.IIIFStore
import nl.knaw.huc.broccoli.service.ResourceLoader
import org.slf4j.LoggerFactory
import java.net.URI
import javax.ws.rs.NotFoundException
import javax.ws.rs.client.Client

class MockIIIFStore(private val iiifUri: String, private val client: Client) : IIIFStore {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun manifest(imageset: String): URI =
        client.target(iiifUri)
            .path("imageset")
            .path(imageset)
            .path("manifest").uri

    override fun getCanvasId(volume: String, opening: Int): String {
        try {
            val json = JsonPath.parse(ResourceLoader.asText("mock/republic/manifest-1728.json"))
            val index = opening - 1
            val canvasId: String = json.read("\$.sequences[0].canvases[$index].@id")
            log.info("id: $canvasId")
            return canvasId
        } catch (e: Exception) {
            log.info("Failed to read canvasId: $e")
            throw NotFoundException("Opening $opening not found in manifest")
        }
    }
}
