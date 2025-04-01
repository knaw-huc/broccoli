import TestUtils.createTestResources
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import io.dropwizard.testing.junit5.ResourceExtension
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.api.ResourcePaths.V2
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport::class)
class HomePageResourceTest {

    lateinit var resource: ResourceExtension

    @BeforeAll
    fun setup() {
        resource = createTestResources("dummy")
    }

    @Test
    fun `HomePageResource should provide index html at root`() {
        val response: Response =
            resource.target("/")
                .request()
                .get()

        assertThat(response.status).isEqualTo(200)
        val body = response.readEntity(String::class.java)
        assertThat(body)
            .contains("Broccoli")
            .contains("API")
            .contains("about")
    }

    @Test
    fun `HomePageResource should not provide index html at v1`() {
        val response: Response =
            resource.target("/$V1/")
                .request()
                .get()

        assertThat(response.status).isEqualTo(404)
    }

    @Test
    fun `HomePageResource should not provide index html at v2`() {
        val response: Response =
            resource.target("/$V2/")
                .request()
                .get()

        assertThat(response.status).isEqualTo(404)
    }
}
