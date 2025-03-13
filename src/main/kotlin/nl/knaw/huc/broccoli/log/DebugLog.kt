import jakarta.ws.rs.NameBinding
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider
import jakarta.ws.rs.ext.ReaderInterceptor
import jakarta.ws.rs.ext.ReaderInterceptorContext
import java.io.IOException


@Target(
    AnnotationTarget.FUNCTION,
)
@Retention(
    AnnotationRetention.SOURCE
)
annotation class DebugLog(val toLog: String)

object DebugLogAnnotationProcessor {

    fun processAnnotations(`object`: Any) {
        val clazz: Class<*> = `object`.javaClass
        for (method in clazz.declaredMethods) {
            if (method.isAnnotationPresent(DebugLog::class.java)) {
                method.invoke(`object`)
                println("DEBUG !!! blarp " + method.name + ": ")
            }
        }
    }
}

//class DebugLogAnnotationProcessorClientRequestFilter : ReaderInterceptor {
//    override fun aroundReadFrom(context: ReaderInterceptorContext): Any {
//        println("DEBUG !!! blarp DebugLogAnnotationProcessorClientRequestFilter")
//        return context
//    }
//
//}

class DebugLogAnnotationProcessorClientRequestFilter : ReaderInterceptor {
    override fun aroundReadFrom(context: ReaderInterceptorContext): Any {
        println("DEBUG !!! blarp DebugLogAnnotationProcessorClientRequestFilter")
        return context
    }
}

@NameBinding
@Target(AnnotationTarget.TYPE, AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
annotation class Logged

@Logged
@Provider
class DebugLoggingFilter : ContainerRequestFilter {
    @Throws(IOException::class)
    override fun filter(requestContext: ContainerRequestContext) {
        println("DebugLoggingFilter is called")

        // Use the ContainerRequestContext to extract information from the HTTP request
        // Information such as the URI, headers and HTTP entity are available
    }
}
