package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import java.net.URI

@JsonPropertyOrder("type", "request", "iiif", "json", "text")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AnnoTextResult(
    val type: String = "AnnoTextResult",
    val request: Map<String, String>,
    val anno: List<Any>,
    val text: List<String>,
    val iiif: IIIFContext,
)

@JsonPropertyOrder("manifest", "canvasId")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IIIFContext(
    val manifest: URI,
    val canvasId: String,
)
