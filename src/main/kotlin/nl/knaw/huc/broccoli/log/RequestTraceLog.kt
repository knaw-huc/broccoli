package nl.knaw.huc.broccoli.log

import jakarta.ws.rs.NameBinding

@NameBinding
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CLASS
)
annotation class RequestTraceLog
