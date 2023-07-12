package nl.knaw.huc.broccoli.resources

import io.dropwizard.jersey.errors.ErrorMessage
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.BAD_REQUEST
import jakarta.ws.rs.ext.ExceptionMapper
import jakarta.ws.rs.ext.Provider

@Provider
class RuntimeExceptionMapper : ExceptionMapper<RuntimeException> {
    override fun toResponse(exception: RuntimeException): Response =
        Response.status(BAD_REQUEST)
            .entity(ErrorMessage(BAD_REQUEST.statusCode, exception.message))
            .type(MediaType.APPLICATION_JSON)
            .build()
}
