package nl.knaw.huc.broccoli.resources.projects;

import TestUtils.mockAnnoRepo
import TestUtils.toUrl
import io.dropwizard.testing.ResourceHelpers.resourceFilePath
import io.dropwizard.testing.junit5.DropwizardAppExtension
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.BroccoliApplication
import nl.knaw.huc.broccoli.api.ResourcePaths.BRINTA
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.api.ResourcePaths.V2
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.service.readEntityAsJsonString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport::class)
class BrintaResourceTest {

    lateinit var mockServer: ClientAndServer
    var projectId = "dummy"
    lateinit var application: DropwizardAppExtension<BroccoliConfiguration>
    var port = -1

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
    fun `BrintaResource should show index at root`() {
        val response: Response =
            application.client().target(toUrl(port, "/$BRINTA/${projectId}/indices"))
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("{\"dummy-index\":{\"bodyType\":\"keyword\"}}")
    }

    @Test
    fun `BrintaResource should show index at v1`() {
        val response: Response =
            application.client().target(toUrl(port, "/$V1/$BRINTA/${projectId}/indices"))
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("{\"dummy-index\":{\"bodyType\":\"keyword\"}}")
    }

    @Test
    fun `BrintaResource should show index at v2`() {
        val response: Response =
            application.client().target(toUrl(port, "/$V2/$BRINTA/${projectId}/indices"))
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("{\"dummy-index\":{\"bodyType\":\"keyword\"}}")
    }

    @Test
    fun `BrintaResource does not show index at v3`() {
        val response: Response =
            application.client().target(toUrl(port, "/v3/$BRINTA/indices"))
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.NOT_FOUND.statusCode)
    }


}
