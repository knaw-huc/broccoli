package nl.knaw.huc.broccoli.service.mock

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import javax.ws.rs.NotFoundException

internal class MockIIIFStoreTest {
    private val sut = MockIIIFStore()

    @Test
    fun `mock store should return mocked content`() {
        assertThat(sut.getCanvasId("_", 285))
            .startsWith("https://images.diginfra.net/api/pim/iiif")
    }

    @Test
    fun `mock store should throw NotFound when volume not found`() {
        assertThrows<NotFoundException> {
            sut.getCanvasId("_", 999999)
        }
    }
}