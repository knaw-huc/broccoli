package nl.knaw.huc.broccoli.resources.projects;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import io.dropwizard.testing.junit5.ResourceExtension
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.Response
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.BroccoliApplication.Companion.createJsonMapper
import nl.knaw.huc.broccoli.BroccoliApplication.Companion.createJsonParser
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.config.BrintaConfiguration
import nl.knaw.huc.broccoli.core.Project
import TestUtils
import com.google.gson.Gson
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MediaType.APPLICATION_JSON_TYPE
import nl.knaw.huc.broccoli.api.IndexQuery
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.readEntityAsJsonString
import nl.knaw.huc.broccoli.service.text.TextRepo
import org.assertj.core.api.Assertions.assertThat
import org.glassfish.jersey.client.JerseyClientBuilder.createClient
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockserver.integration.ClientAndServer

@ExtendWith(DropwizardExtensionsSupport::class)
class ProjectsResourceTest {
    val resource: ResourceExtension
    val mockIndexServer: ClientAndServer
    val projectId = "dummy"
    init {
        mockIndexServer =
            ClientAndServer.startClientAndServer()

        val mockAnnoRepoClient = mock(AnnoRepoClient::class.java)
        val project = Project(
            projectId,
            "textType",
            "topTierBodyType",
            emptyMap(),
            BrintaConfiguration(),
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
        val request = TestUtils.getResourceAsString("./projects/search/request.json")
        val response: Response =
            resource.client()
                .target("/$PROJECTS")
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("[\"${projectId}\"]")

    }

    @Disabled
    @Test
    fun `ProjectsResource should search`() {
        val request = TestUtils.getResourceAsString("./projects/search/request.json")

        val query: IndexQuery = Gson().fromJson(request, IndexQuery::class.java)
        val response: Response =
            resource.client()
                .target("/$PROJECTS/${projectId}/search")
                .request()
                .post(Entity.entity(query, APPLICATION_JSON_TYPE))
        assertThat(response.status)
            .isEqualTo(Response.Status.OK)
    }
}