package nl.knaw.huc.broccoli.resources

import io.swagger.v3.oas.annotations.Operation
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.api.ResourcePaths.ABOUT
import nl.knaw.huc.broccoli.service.ResourceLoader
import java.io.InputStream

@Path("/")
class HomePageResource {
    /**
     * Shows the homepage for the backend
     *
     * @return HTML representation of the homepage
     */
    @GET
    @Operation(description = "Show the server homepage")
    @Produces(MediaType.TEXT_HTML)
    fun getHomePage(): Response {
        return Response.ok(getIndexHtml())
            .header(HttpHeaders.CACHE_CONTROL, "public")
            .build()
    }

    private fun getIndexHtml(): InputStream? {
        return ResourceLoader.asStream("index.html")
    }

    @GET
    @Path("favicon.ico")
    @Operation(description = "Placeholder for favicon.ico")
    fun getFavIcon(): Response = Response.noContent().build()

    @GET
    @Path("robots.txt")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(description = "Placeholder for robots.txt")
    fun noRobots(): String = "${HttpHeaders.USER_AGENT}: *\nDisallow: /\n"
}
