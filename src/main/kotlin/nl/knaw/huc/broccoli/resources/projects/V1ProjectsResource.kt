package nl.knaw.huc.broccoli.resources.projects

import nl.knaw.huc.broccoli.core.Project
import org.slf4j.LoggerFactory

class V1ProjectsResource(
    private val projects: Map<String, Project>,
) {

    fun listProjects(): Set<String> {
        logger.info("List projects v1")
        return projects.keys
    }

    companion object {
        private val logger = LoggerFactory.getLogger(V1ProjectsResource::class.java)
    }

}
