package nl.knaw.huc.broccoli.log;

import jakarta.ws.rs.NameBinding

@NameBinding
@Target(
    AnnotationTarget.TYPE,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CLASS
)
annotation class RequestTraceLog
