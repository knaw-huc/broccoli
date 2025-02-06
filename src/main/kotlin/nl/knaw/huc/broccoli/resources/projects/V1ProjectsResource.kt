package nl.knaw.huc.broccoli.resources.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.ParseContext
import jakarta.ws.rs.client.Client
import nl.knaw.huc.broccoli.core.Project
import org.slf4j.LoggerFactory

class V1ProjectsResource(
    private val projects: Map<String, Project>,
    client: Client,
    jsonParser: ParseContext,
    jsonWriter: ObjectMapper
) : ProjectsResource(
    projects, client, jsonParser, jsonWriter
) {

    override fun listProjects(): Set<String> {
        logger.info("List projects v1")
        return projects.keys
    }

    companion object {
        private val logger =
            LoggerFactory.getLogger(V2ProjectsResource::class.java)
    }

}
