import TestUtils.*
import com.jayway.jsonpath.JsonPath.parse
import io.dropwizard.testing.ResourceHelpers.resourceFilePath
import io.dropwizard.testing.junit5.DropwizardAppExtension
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import io.dropwizard.testing.junit5.ResourceExtension
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.BroccoliApplication
import nl.knaw.huc.broccoli.api.Constants.APP_NAME
import nl.knaw.huc.broccoli.api.ResourcePaths.ABOUT
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.api.ResourcePaths.V2
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.resources.AboutResource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody.json

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport::class)
class AboutResourceTest {

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
    fun `AboutResource should provide app name at root`() {
        val response: Response =
            application.client().target(toUrl(port, "/$ABOUT"))
                .request()
                .get()

        assertThat(response.status).isEqualTo(200)
        val body = response.readEntity(String::class.java)
        val json = parse(body)
        assertThat(json.read<String>("$.appName"))
            .isEqualTo(APP_NAME)
    }

    @Test
    fun `AboutResource should provide not run at v1`() {
        val response: Response =
            application.client().target(toUrl(port, "/$V1/$ABOUT"))
                .request()
                .get()

        assertThat(response.status).isEqualTo(404)
    }

    @Test
    fun `AboutResource should not run at v2`() {
        val response: Response =
            application.client().target(toUrl(port, "/$V2/$ABOUT"))
                .request()
                .get()

        assertThat(response.status).isEqualTo(404)
    }
}
