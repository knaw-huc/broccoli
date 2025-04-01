package nl.knaw.huc.broccoli.resources.projects;

import TestUtils
import TestUtils.resourceAsString
import TestUtils.toUrl
import V0Resource
import V1Resource
import V2Resource
import com.google.gson.Gson
import com.google.gson.JsonParser
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
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.api.ResourcePaths.V2
import nl.knaw.huc.broccoli.config.BrintaConfiguration
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.readEntityAsJsonString
import nl.knaw.huc.broccoli.service.text.TextRepo
import org.assertj.core.api.Assertions.assertThat
import org.glassfish.jersey.client.JerseyClientBuilder.createClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import org.mockserver.model.JsonBody.json

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport::class)
class ProjectsResourceTest_versioning {

    lateinit var mockServer: ClientAndServer

    lateinit var resource: ResourceExtension
    var application: DropwizardAppExtension<BroccoliConfiguration>? = null

    var projectId = "dummy"

    @BeforeAll
    fun setup() {
        mockServer =
            ClientAndServer.startClientAndServer(9200)

        val mockAnnoRepoClient = mock(AnnoRepoClient::class.java)
        val brintaConfigJson = TestUtils
            .resourceAsString("./projects/brintaConfig.json")
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
            BroccoliApplication::class.java
        )

        resource = ResourceExtension
            .builder()
            .setMapper(createJsonMapper())
            .addResource(
                V0Resource(
                    projects,
                    client,
                    jsonParser,
                    jsonWriter,
                )
            )
            .addResource(
                V1Resource(
                    projects,
                    client,
                    jsonParser,
                    jsonWriter,
                )
            )
            .addResource(
                V2Resource(
                    projects,
                    client,
                    jsonParser,
                    jsonWriter,
                )
            )
            .build()
    }

    @AfterAll
    fun teardown() {
        mockServer.stop()
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
    fun `ProjectsResource should list projects at v1`() {
        val response: Response =
            resource.target("/$V1/$PROJECTS")
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("[\"${projectId}\"]")
    }

    @Test
    fun `ProjectsResource should list projects at v2`() {
        val response: Response =
            resource.target("/$V2/$PROJECTS")
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("[\"${projectId}\"]")
    }

    @Test
    fun `ProjectsResource does not list projects at v3`() {
        val response: Response =
            resource.target("/v3/$PROJECTS")
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.NOT_FOUND.statusCode)
    }

}
