import TestUtils.createTestResources
import io.dropwizard.testing.junit5.DropwizardAppExtension
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import io.dropwizard.testing.junit5.ResourceExtension
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.BroccoliApplication
import nl.knaw.huc.broccoli.api.ResourcePaths.ABOUT
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.api.ResourcePaths.V2
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport::class)
class AboutResourceTest {

    lateinit var mockServer: ClientAndServer
    lateinit var resource: ResourceExtension
    lateinit var application: DropwizardAppExtension<BroccoliConfiguration>

    @BeforeAll
    fun setup() {
        mockServer =
            ClientAndServer.startClientAndServer(9200)

        application = DropwizardAppExtension(
            BroccoliApplication::class.java
        )

        resource = createTestResources("dummy")

    }

    @AfterAll
    fun teardown() {
        mockServer.stop()
    }

    @Test
    fun `AboutResource should provide app name at root`() {
        val response: Response =
            resource
                .target("/$ABOUT")
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
    }

    @Test
    fun `AboutResource should provide not run at v1`() {
        val response: Response =
            resource.target("/$V1/$ABOUT")
                .request()
                .get()

        assertThat(response.status).isEqualTo(404)
    }

    @Test
    fun `AboutResource should not run at v2`() {
        val response: Response =
            resource.target("/$V2/$ABOUT")
                .request()
                .get()

        assertThat(response.status).isEqualTo(404)
    }
}
