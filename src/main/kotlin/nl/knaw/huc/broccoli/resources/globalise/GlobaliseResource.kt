package nl.knaw.huc.broccoli.resources.globalise

import nl.knaw.huc.broccoli.config.GlobaliseConfiguration
import org.slf4j.LoggerFactory

class GlobaliseResource(private val globalise: GlobaliseConfiguration) {
    private val log = LoggerFactory.getLogger(javaClass)
}
