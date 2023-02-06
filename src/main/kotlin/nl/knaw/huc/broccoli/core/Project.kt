package nl.knaw.huc.broccoli.core

import nl.knaw.huc.broccoli.config.ProjectConfiguration
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import org.slf4j.LoggerFactory

class Project(
    config: ProjectConfiguration,
    val annoRepo: AnnoRepo,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    val name = config.name

}
