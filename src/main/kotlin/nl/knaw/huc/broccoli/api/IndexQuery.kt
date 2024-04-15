package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonProperty

data class IndexQuery(
    val date: IndexRange?,
    val terms: IndexTerms?,
    val text: String?,
    val range: IndexRange?,

    @JsonProperty("aggs")
    val aggregations: List<String>? = null
)

typealias IndexTerms = Map<String, List<String>>

data class IndexRange(
    val name: String,
    val from: String,
    val to: String
)
