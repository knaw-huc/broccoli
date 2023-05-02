package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonProperty

data class IndexQuery(
    val date: IndexDate?,
    val terms: IndexTerms?,
    val text: String?,

    @JsonProperty("aggs")
    val aggregations: List<String>? = null
)

typealias IndexTerms = Map<String, List<String>>

data class IndexDate(
    val name: String,
    val from: String,
    val to: String
)
