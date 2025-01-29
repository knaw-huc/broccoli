package nl.knaw.huc.broccoli.resources.projects;

import TestUtils
import com.google.gson.Gson
import io.dropwizard.testing.junit5.DropwizardAppExtension
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import io.dropwizard.testing.junit5.ResourceExtension
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE
import jakarta.ws.rs.core.Response
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.BroccoliApplication
import nl.knaw.huc.broccoli.BroccoliApplication.Companion.createJsonMapper
import nl.knaw.huc.broccoli.BroccoliApplication.Companion.createJsonParser
import nl.knaw.huc.broccoli.api.IndexQuery
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.config.BrintaConfiguration
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.readEntityAsJsonString
import nl.knaw.huc.broccoli.service.text.TextRepo
import org.assertj.core.api.Assertions.assertThat
import org.glassfish.jersey.client.JerseyClientBuilder.createClient
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.JsonBody.json

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport::class)
class ProjectsResourceTest {

    lateinit var resource: ResourceExtension
    lateinit var mockIndexServer: ClientAndServer
    var projectId = "dummy"
    var application: DropwizardAppExtension<BroccoliConfiguration>? = null


    @BeforeAll
    fun setup() {
        mockIndexServer =
            ClientAndServer.startClientAndServer(9200)

        val mockAnnoRepoClient = mock(AnnoRepoClient::class.java)
        val brintaConfigJson = TestUtils
            .getResourceAsString("./projects/brintaConfig.json")
        val brintaConfiguration =
            Gson().fromJson(brintaConfigJson, BrintaConfiguration::class.java)
        val project = Project(
            projectId,
            "textType",
            "topTierBodyType",
            emptyMap(),
            brintaConfiguration,
            TextRepo("uri", "apiKey"),
            AnnoRepo(
                mockAnnoRepoClient,
                "containerName", "textType"
            )
        )
        val projects = mapOf(projectId to project)
        val client = createClient()

        val jsonParser = createJsonParser()
        val jsonWriter = createJsonMapper()

        application = DropwizardAppExtension(
            BroccoliApplication::class.java,
        )

        resource = ResourceExtension
            .builder()
            .setMapper(createJsonMapper())
            .addResource(
                ProjectsResource(
                    projects,
                    client,
                    jsonParser,
                    jsonWriter,
                )
            )
            .build()
    }

    @Test
    fun `ProjectsResource should list projects`() {
        val response: Response =
            resource.client()
                .target("/$PROJECTS")
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("[\"${projectId}\"]")
    }

    @Test
    fun `ProjectsResource should search`() {
        val request = TestUtils.getResourceAsString("./projects/search/request.json")

        val esRequest =
            TestUtils.getResourceAsString("./projects/search/esRequest.json")
        val esResponse =
            TestUtils.getResourceAsString("./projects/search/esResponse.json")
        val exp = mockIndexServer.`when`(
            request()
                .withBody(json(esRequest))
                .withPath("/dummy-index/_search")
        ).respond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody(esResponse)
                .withHeader("Content-Type", "application/json")
        )

        val query: IndexQuery = Gson().fromJson(request, IndexQuery::class.java)
        val response: Response =
            resource.client()
                .target("/$PROJECTS/${projectId}/search")
                .request()
                .post(Entity.entity(query, APPLICATION_JSON_TYPE))
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        mockIndexServer.verify(exp[0].id)
    }

}