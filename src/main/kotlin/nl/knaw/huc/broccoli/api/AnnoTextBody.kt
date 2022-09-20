package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("request", "start", "end", "text")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AnnoTextBody(
    val request: Request,
    val start: TextMarker,
    val end: TextMarker,
    val text: List<String>
)

@JsonPropertyOrder("line", "offset", "len")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TextMarker(
    val line: Int,
    val offset: Int,
    val len: Int
) {
    fun relativeTo(startLine: Int): TextMarker {
        return TextMarker(
            line = line - startLine,
            offset = offset,
            len = len
        )
    }
}
