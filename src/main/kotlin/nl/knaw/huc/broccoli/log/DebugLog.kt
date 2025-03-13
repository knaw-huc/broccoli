import jakarta.ws.rs.NameBinding
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import org.glassfish.jersey.server.ContainerRequest
import org.slf4j.LoggerFactory
import java.io.IOException

@NameBinding
@Target(
    AnnotationTarget.TYPE,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CLASS
)
annotation class DebugLog

@DebugLog
class DebugLoggingFilter : ContainerRequestFilter {
    private val log = LoggerFactory.getLogger(javaClass)

    @Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext) {
        if (!log.isDebugEnabled) {
            return;
        }
        (requestContext as ContainerRequest).bufferEntity()
        log.debug(
            "DebugLoggingFilter: " +
                    requestContext.method + "; " +
                    requestContext.absolutePath.path + "; " +
                    requestContext.readEntity(String::class.java)
        )
    }
}
