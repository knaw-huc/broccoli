package nl.knaw.huc.broccoli.log

import RequestDebugLog
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import org.glassfish.jersey.server.ContainerRequest
import org.slf4j.LoggerFactory
import java.io.IOException

@RequestDebugLog
class RequestDebugLogFilter : ContainerRequestFilter {
    private val log = LoggerFactory.getLogger(RequestDebugLog::class.java.name)

    @Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext) {
        if (!log.isDebugEnabled) {
            return;
        }

        requestContext as ContainerRequest
        val method = requestContext.method
        val path = requestContext.requestUri.path
        val params = requestContext.requestUri.query
        requestContext.bufferEntity()
        val body = requestContext.readEntity(String::class.java)

        log.debug("$method $path: \n --> params: $params\n --> body:   $body")
    }
}