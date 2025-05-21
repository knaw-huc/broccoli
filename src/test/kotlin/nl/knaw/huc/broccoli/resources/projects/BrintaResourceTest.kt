package nl.knaw.huc.broccoli.resources.projects;

import TestUtils.createTestResources
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import io.dropwizard.testing.junit5.ResourceExtension
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.api.ResourcePaths.BRINTA
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.api.ResourcePaths.V2
import nl.knaw.huc.broccoli.service.readEntityAsJsonString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.extension.ExtendWith

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(DropwizardExtensionsSupport::class)
class BrintaResourceTest {

    lateinit var resource: ResourceExtension
    var projectId = "dummy2"

    @BeforeAll
    fun setup() {
        resource = createTestResources(projectId)
    }

    @Test
    fun `BrintaResource should show index at root`() {
        val response: Response =
            resource.target("/$BRINTA/${projectId}/indices")
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("{\"dummy-index\":{\"bodyType\":\"keyword\"}}")
    }

    @Test
    fun `BrintaResource should show index at v1`() {
        val response: Response =
            resource.target("/$V1/$BRINTA/${projectId}/indices")
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("{\"dummy-index\":{\"bodyType\":\"keyword\"}}")
    }

    @Test
    fun `BrintaResource should show index at v2`() {
        val response: Response =
            resource.target("/$V2/$BRINTA/${projectId}/indices")
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.OK.statusCode)
        assertThat(response.readEntityAsJsonString()).isEqualTo("{\"dummy-index\":{\"bodyType\":\"keyword\"}}")
    }

    @Test
    fun `BrintaResource does not show index at v3`() {
        val response: Response =
            resource.target("/V3/$BRINTA/${projectId}/indices")
                .request()
                .get()
        assertThat(response.status)
            .isEqualTo(Response.Status.NOT_FOUND.statusCode)
    }


}
