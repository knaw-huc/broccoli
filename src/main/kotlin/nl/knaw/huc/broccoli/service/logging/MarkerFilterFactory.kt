package nl.knaw.huc.broccoli.service.logging

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.filter.Filter
import ch.qos.logback.core.spi.FilterReply
import com.fasterxml.jackson.annotation.JsonTypeName
import io.dropwizard.logging.common.filter.FilterFactory
import jakarta.validation.constraints.NotNull
import org.slf4j.Marker

@JsonTypeName("marker")
class MarkerFilterFactory : FilterFactory<ILoggingEvent> {
    @NotNull
    val markers = HashSet<String>()

    override fun build(): Filter<ILoggingEvent> {
        return object : Filter<ILoggingEvent>() {
            override fun decide(event: ILoggingEvent): FilterReply {
                event.markerList.forEach { m: Marker ->
                    if (markers.contains(m.name)) {
                        return FilterReply.ACCEPT
                    }
                }
                return FilterReply.DENY
            }
        }
    }

}
