package nl.knaw.huc.broccoli.resources

import io.swagger.v3.oas.annotations.Operation
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import nl.knaw.huc.broccoli.api.AboutInfo
import nl.knaw.huc.broccoli.api.ResourcePaths
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import java.time.Instant

@Path(ResourcePaths.ABOUT)
@Produces(MediaType.APPLICATION_JSON)
class AboutResource(configuration: BroccoliConfiguration, appName: String, version: String) {
    private val about = AboutInfo(
        appName = appName,
        version = version,
        startedAt = Instant.now().toString(),
        baseURI = configuration.externalBaseUrl
    )

    @Operation(description = "Get basic server information")
    @GET
    fun getAboutInfo(): AboutInfo = about
}
