package nl.knaw.huc.broccoli.resources

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import io.swagger.v3.oas.annotations.Operation
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import nl.knaw.huc.broccoli.api.AboutInfo
import nl.knaw.huc.broccoli.api.ResourcePaths
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import org.slf4j.LoggerFactory
import java.time.Instant

@Path(ResourcePaths.ABOUT)
@Produces(MediaType.APPLICATION_JSON)
class AboutResource(
    private val configuration: BroccoliConfiguration,
    private val appName: String,
    private val version: String
) {
    private val startedAt = Instant.now().toString()

    @Operation(description = "Get basic server information")
    @GET
    fun getAboutInfo(): AboutInfo = AboutInfo(
        appName = appName,
        version = version,
        startedAt = startedAt,
        baseURI = configuration.externalBaseUrl,
        hucLogLevel = findHucLogLevel()
    )

    private fun findHucLogLevel(): String {
        val loggerContext = LoggerFactory.getILoggerFactory()
        val logger: Logger = (loggerContext as LoggerContext).getLogger("nl.knaw.huc")
        return logger.level.toString()
    }
}
