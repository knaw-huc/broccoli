package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("id", "type", "anno", "text")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AnnoTextResult(
    val id: String,
    val anno: List<Any>,
    val text: List<String>,
    val iiif: IIIFContext
) {
    val type: String = "AnnoTextResult"
}

@JsonPropertyOrder("manifest", "canvasId")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IIIFContext(
    val manifest: String,
    val canvasId: String
)