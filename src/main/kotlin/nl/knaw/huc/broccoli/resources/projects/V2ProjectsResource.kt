package nl.knaw.huc.broccoli.resources.projects

import ElasticSearchClient
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.client.Client
import nl.knaw.huc.broccoli.core.Project
import org.slf4j.LoggerFactory

class V2ProjectsResource(
    private val projects: Map<String, Project>,
    client: Client,
    jsonWriter: ObjectMapper,
    esClient: ElasticSearchClient,
) : ProjectsResource(
    projects, client, jsonWriter, esClient
) {

    override fun listProjects(): Set<String> {
        logger.info("List projects v2")
        return projects.keys
    }

    companion object {
        private val logger =
            LoggerFactory.getLogger(V2ProjectsResource::class.java)
    }

}
