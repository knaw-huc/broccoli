package nl.knaw.huc.broccoli.resources

import io.dropwizard.jersey.errors.ErrorMessage
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status.BAD_REQUEST
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

@Provider
class RuntimeExceptionMapper : ExceptionMapper<RuntimeException> {
    override fun toResponse(exception: RuntimeException): Response =
        Response.status(BAD_REQUEST)
            .entity(ErrorMessage(BAD_REQUEST.statusCode, exception.message))
            .type(MediaType.APPLICATION_JSON)
            .build()
}
