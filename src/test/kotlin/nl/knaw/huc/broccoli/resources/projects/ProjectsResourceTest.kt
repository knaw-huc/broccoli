package nl.knaw.huc.broccoli.resources.projects;

import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import io.dropwizard.testing.junit5.ResourceExtension
import jakarta.ws.rs.core.Response
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.BroccoliApplication.Companion.createJsonMapper
import nl.knaw.huc.broccoli.BroccoliApplication.Companion.createJsonParser
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.config.BrintaConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.text.TextRepo
import org.glassfish.jersey.client.JerseyClientBuilder.createClient
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockserver.integration.ClientAndServer

@ExtendWith(DropwizardExtensionsSupport::class)
class ProjectsResourceTest {
    val resource: ResourceExtension
    val mockIndexServer: ClientAndServer
    val mockIndexPort = 1234

    init {
        mockIndexServer =
            ClientAndServer.startClientAndServer(mockIndexPort)

        val mockAnnoRepoClient = mock(AnnoRepoClient::class.java)
        val project = Project(
            "name",
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
        val projects = mapOf("" to project)
        val client = createClient()

        val jsonParser = createJsonParser()
        val jsonWriter = createJsonMapper()

        resource = ResourceExtension
            .builder()
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
                .target(PROJECTS)
                .request()
                .get()

    }
}