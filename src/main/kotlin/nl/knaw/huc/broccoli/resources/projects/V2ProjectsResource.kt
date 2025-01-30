package nl.knaw.huc.broccoli.resources.projects

import nl.knaw.huc.broccoli.core.Project
import org.slf4j.LoggerFactory

class V2ProjectsResource(
    private val projects: Map<String, Project>,
) {

    fun listProjects(): Set<String> {
        logger.info("List projects v2")
        return projects.keys
    }

    companion object {
        private val logger = LoggerFactory.getLogger(V2ProjectsResource::class.java)
    }

}
