import com.jayway.jsonpath.JsonPath.parse
import io.dropwizard.testing.junit5.DropwizardExtensionsSupport
import io.dropwizard.testing.junit5.ResourceExtension
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.api.ResourcePaths.ABOUT
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.api.ResourcePaths.V2
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.resources.AboutResource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith


@ExtendWith(DropwizardExtensionsSupport::class)
class AboutResourceTest {

    val resource: ResourceExtension
    val appName = "testApp"

    init {
        val aboutResource = AboutResource(
            BroccoliConfiguration(),
            appName,
            "version"
        )
        resource = ResourceExtension
            .builder()
            .addResource(aboutResource)
            .build()
    }

    @Test
    fun `AboutResource should provide app name at root`() {
        val response: Response =
            resource.client()
                .target("/$ABOUT")
                .request()
                .get()

        assertThat(response.status).isEqualTo(200)
        val body = response.readEntity(String::class.java)
        val json = parse(body)
        assertThat(json.read<String>("$.appName"))
            .isEqualTo(appName)
    }

    @Test
    fun `AboutResource should provide app name at v1`() {
        val response: Response =
            resource.client()
                .target("/$V1/$ABOUT")
                .request()
                .get()

        assertThat(response.status).isEqualTo(200)
        val body = response.readEntity(String::class.java)
        val json = parse(body)
        assertThat(json.read<String>("$.appName"))
            .isEqualTo(appName)
    }

    @Test
    fun `AboutResource should provide app name at v2`() {
        val response: Response =
            resource.client()
                .target("/$V2/$ABOUT")
                .request()
                .get()

        assertThat(response.status).isEqualTo(200)
        val body = response.readEntity(String::class.java)
        val json = parse(body)
        assertThat(json.read<String>("$.appName"))
            .isEqualTo(appName)
    }
}
