package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonPropertyOrder

@JsonPropertyOrder("volume", "page", "bodyId")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Request(
    val volume: String,
    val opening: Int,
    val bodyId: String?
)