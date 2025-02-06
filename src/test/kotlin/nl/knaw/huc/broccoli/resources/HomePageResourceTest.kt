import TestUtils.mockAnnoRepo
import TestUtils.toUrl
import io.dropwizard.testing.ResourceHelpers.resourceFilePath
import io.dropwizard.testing.junit5.DropwizardAppExtension
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.BroccoliApplication
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.api.ResourcePaths.V2
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport::class)
class HomePageResourceTest {

    lateinit var application: DropwizardAppExtension<BroccoliConfiguration>
    lateinit var mockServer: ClientAndServer
    var port: Int = -1

    @BeforeAll
    fun setup() {
        mockServer =
            ClientAndServer.startClientAndServer(9200)

        mockAnnoRepo(mockServer)

        application = DropwizardAppExtension(
            BroccoliApplication::class.java,
            resourceFilePath("./broccoliConfig.json")
        )
    }

    @BeforeEach
    fun findPort() {
        // Port not yet available during setup:
        port = application.localPort
    }

    @AfterAll
    fun teardown() {
        mockServer.stop()
    }

    @Test
    fun `HomePageResource should provide index html at root`() {
        val response: Response =
            application.client().target(toUrl(port, "/"))
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
            application.client().target(toUrl(port, "/$V1/"))
                .request()
                .get()

        assertThat(response.status).isEqualTo(404)
    }

    @Test
    fun `HomePageResource should not provide index html at v2`() {
        val response: Response =
            application.client().target(toUrl(port, "/$V2/"))
                .request()
                .get()

        assertThat(response.status).isEqualTo(404)
    }
}
