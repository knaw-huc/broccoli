import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.ParseContext
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.core.MediaType
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.api.ResourcePaths.V1
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.resources.projects.ProjectsResource


@Path("/")
@Produces(MediaType.APPLICATION_JSON)
class V0Resource(
    private val projects: Map<String, Project>,
    private val client: Client,
    private val jsonParser: ParseContext,
    private val jsonWriter: ObjectMapper
) {

    @Path(PROJECTS)
    fun getProjectsResource(): ProjectsResource {
        return ProjectsResource(projects, client, jsonParser, jsonWriter)
    }
}