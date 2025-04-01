package nl.knaw.huc.broccoli.resources.projects;

import TestUtils.createTestResources
import io.dropwizard.testing.junit5.DropwizardAppExtension
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import io.dropwizard.testing.junit5.ResourceExtension
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.BroccoliApplication
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.api.ResourcePaths.V2
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.service.readEntityAsJsonString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith
import org.mockserver.integration.ClientAndServer

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport::class)
class ProjectsResourceTest_versioning {

    lateinit var mockServer: ClientAndServer
    lateinit var resource: ResourceExtension
    lateinit var application: DropwizardAppExtension<BroccoliConfiguration>

    var projectId = "dummy"

    @BeforeAll
    fun setup() {
        mockServer =
            ClientAndServer.startClientAndServer(9200)

        application = DropwizardAppExtension(
            BroccoliApplication::class.java
        )

        resource = createTestResources(projectId)
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
