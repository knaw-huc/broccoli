package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("type", "request", "iiif", "anno", "text")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AnnoTextResult(
    val type: String = "AnnoTextResult",
    val request: Request,
    val anno: List<Any>,
    val text: List<String>,
    val iiif: IIIFContext
)

@JsonPropertyOrder("volume", "page")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Request(
    val volume: String,
    val opening: Int
)

@JsonPropertyOrder("manifest", "canvasId")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IIIFContext(
    val manifest: String,
    val canvasId: String
)