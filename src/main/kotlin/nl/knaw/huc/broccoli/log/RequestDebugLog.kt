
import jakarta.ws.rs.NameBinding

@NameBinding
@Target(
    AnnotationTarget.TYPE,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CLASS
)
annotation class RequestDebugLog
