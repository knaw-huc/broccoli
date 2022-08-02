package nl.knaw.huc.broccoli.service.mock

import com.jayway.jsonpath.JsonPath
import nl.knaw.huc.broccoli.service.IIIFStore
import nl.knaw.huc.broccoli.service.ResourceLoader
import org.slf4j.LoggerFactory
import javax.ws.rs.NotFoundException

class MockIIIFStore : IIIFStore {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun getCanvasId(volume: String, opening: Int): String {
        try {
            val json = JsonPath.parse(ResourceLoader.asText("mock/manifest-1728.json"))
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