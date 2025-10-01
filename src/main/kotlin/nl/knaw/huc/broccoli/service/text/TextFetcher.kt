package nl.knaw.huc.broccoli.service.text

import jakarta.ws.rs.ClientErrorException
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.core.HttpHeaders
import jakarta.ws.rs.core.Response.Status
import org.slf4j.LoggerFactory

class TextFetcher(val client: Client, val uri: String, val apiKey: String?) {
    fun fetchText(textSourceUrl: String): String {
        logger.info("GET {}", textSourceUrl)

        val resp = client
            .target(textSourceUrl)
            .request()
            .let { b ->
                if (apiKey != null && canResolve(textSourceUrl)) {
                    logger.info("with apiKey {}", apiKey)
                    b.header(HttpHeaders.AUTHORIZATION, "Basic $apiKey")
                } else b
            }
            .get()

        if (resp.status == Status.UNAUTHORIZED.statusCode) {
            logger.warn("Auth failed fetching $textSourceUrl")
            throw ClientErrorException("Need credentials for $textSourceUrl", Status.UNAUTHORIZED)
        }

        return resp.readEntity(String::class.java) ?: ""
    }

    private fun canResolve(sourceUrl: String): Boolean {
        logger.info("canResolve: configured uri=$uri, sourceUrl=$sourceUrl")
        return sourceUrl.startsWith(uri)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TextFetcher::class.java)
    }
}
