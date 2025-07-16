package nl.knaw.huc.broccoli.log

import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import org.glassfish.jersey.server.ContainerRequest
import org.slf4j.LoggerFactory
import java.io.IOException

@RequestTraceLog
class RequestTraceLogFilter : ContainerRequestFilter {
    private val logger = LoggerFactory.getLogger(RequestTraceLog::class.java.simpleName)

    @Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext) {
        if (logger.isTraceEnabled) {
            with(requestContext as ContainerRequest) {
                bufferEntity()
                logger.atTrace()
                    .addKeyValue("params", requestUri.query)
                    .addKeyValue("body", readEntity(String::class.java))
                    .log("$method ${requestUri.path}:")
            }
        }
    }
}
