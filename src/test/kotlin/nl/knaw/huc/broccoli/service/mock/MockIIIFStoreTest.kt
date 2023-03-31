package nl.knaw.huc.broccoli.service.mock

import io.dropwizard.testing.ResourceHelpers
import io.dropwizard.testing.junit5.DropwizardAppExtension
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import jakarta.ws.rs.NotFoundException
import nl.knaw.huc.broccoli.BroccoliApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(DropwizardExtensionsSupport::class)
internal class MockIIIFStoreTest {
    private val sut = MockIIIFStore(iiifUri = "https://images.diginfra.net/api/pim", client = EXT.client())

    @Test
    fun `mock store should return mocked content`() {
        assertThat(sut.getCanvasId("_", 285)).startsWith("https://images.diginfra.net/api/pim/iiif")
    }

    @Test
    fun `mock store should throw NotFound when volume not found`() {
        assertThrows<NotFoundException> {
            sut.getCanvasId("_", 999999)
        }
    }

    companion object {
        val EXT = DropwizardAppExtension(
            BroccoliApplication::class.java,
            ResourceHelpers.resourceFilePath("config.yml")
        )

    }
}
