package nl.knaw.huc.broccoli.resources.projects;

import TestUtils.*
import com.google.gson.Gson
import com.google.gson.JsonParser
import io.dropwizard.testing.ResourceHelpers.resourceFilePath
import io.dropwizard.testing.junit5.DropwizardAppExtension
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.BroccoliApplication
import nl.knaw.huc.broccoli.api.IndexQuery
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.api.ResourcePaths.V2
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.service.readEntityAsJsonString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody.json

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport::class)
class ProjectsResourceTest {

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
    fun `ProjectsResource should list projects at root`() {
        val response: Response =
            application.client().target(toUrl(port, "/$PROJECTS"))
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("[\"${projectId}\"]")
    }

    @Test
    fun `ProjectsResource should list projects at v1`() {
        val response: Response =
            application.client().target(toUrl(port, "/$V1/$PROJECTS"))
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("[\"${projectId}\"]")
    }

    @Test
    fun `ProjectsResource should list projects at v2`() {
        val response: Response =
            application.client().target(toUrl(port, "/$V2/$PROJECTS"))
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("[\"${projectId}\"]")
    }

    @Test
    fun `ProjectsResource does not list projects at v3`() {
        val response: Response =
            application.client().target(toUrl(port ,"/v3/$PROJECTS"))
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.NOT_FOUND.statusCode)
    }

    @Test
    fun `ProjectsResource should search`() {
        val request =
            resourceAsString("./projects/search/request.json")

        val esRequest =
            resourceAsString("./projects/search/esRequest.json")
        val esResponse =
            resourceAsString("./projects/search/esResponse.json")
        val exp = mockServer.`when`(
            request()
                .withBody(json(esRequest))
                .withPath("/dummy-index/_search")
        ).respond(
            response()
                .withStatusCode(200)
                .withBody(esResponse)
                .withHeader("Content-Type", "application/json")
        )

        val query: IndexQuery = Gson().fromJson(request, IndexQuery::class.java)
        val target = toUrl(port, "/$V2/$PROJECTS/${projectId}/search")
        val response: Response =
            application.client()
                .target(target)
                .request()
                .post(Entity.entity(query, APPLICATION_JSON_TYPE))
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        mockServer.verify(exp[0].id)
        val expectedJson =
            JsonParser.parseString(resourceAsString("./projects/search/response.json"))
        val receivedJson =
            JsonParser.parseString(response.readEntityAsJsonString())
        assertThat(receivedJson)
            .isEqualTo(expectedJson)
    }

}
