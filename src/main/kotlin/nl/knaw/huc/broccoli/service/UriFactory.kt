package nl.knaw.huc.broccoli.service

import jakarta.ws.rs.core.UriBuilder
import nl.knaw.huc.broccoli.api.ResourcePaths
import nl.knaw.huc.broccoli.config.BroccoliConfiguration
import java.net.URI

class UriFactory(private val configuration: BroccoliConfiguration) {

    fun indexURL(projectId: String, indexName: String): URI =
        UriBuilder.fromUri(configuration.externalBaseUrl)
            .path(ResourcePaths.BRINTA)
            .path(projectId)
            .path(indexName)
            .build()

    fun indexStatusURL(projectId: String, indexName: String): URI =
        UriBuilder.fromUri(indexURL(projectId, indexName))
            .path(ResourcePaths.STATUS)
            .build()
}
