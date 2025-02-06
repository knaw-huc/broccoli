import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.ParseContext
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.core.MediaType
import nl.knaw.huc.broccoli.api.ResourcePaths.ABOUT
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.resources.AboutResource
import nl.knaw.huc.broccoli.resources.HomePageResource
import nl.knaw.huc.broccoli.resources.projects.ProjectsResource


@Path("/")
@Produces(MediaType.APPLICATION_JSON)
class V0Resource(
    private val projects: Map<String, Project>,
    private val client: Client,
    private val jsonParser: ParseContext,
    private val jsonWriter: ObjectMapper,
    private val configuration: BroccoliConfiguration,
    private val appName: String,
    private val version: String
) {

    @Path(ABOUT)
    fun getAboutResource(): AboutResource {
        return AboutResource(configuration, appName, version)
    }

    @Path("/")
    fun getHomePageResource(): HomePageResource {
        return HomePageResource()
    }

    @Path(PROJECTS)
    fun getProjectsResource(): ProjectsResource {
        return ProjectsResource(projects, client, jsonParser, jsonWriter)
    }
}