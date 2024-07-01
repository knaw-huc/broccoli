package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonProperty

data class IndexQuery(
    val date: IndexRange?,
    val terms: IndexTerms?,
    val text: String?,
    val range: IndexRange?,

    @JsonProperty("aggs")
    val aggregations: List<String>? = null
) {
    override fun toString(): String = buildString {
        text?.let { append(it).append('|') }
        date?.let { append(it).append('|') }
        range?.let { append(it).append('|') }
        terms?.let { append(it).append('|') }
    }
}

typealias IndexTerms = Map<String, List<String>>

data class IndexRange(val name: String, val from: String?, val to: String?) {
    override fun toString(): String = "$name:[$from,$to]"
}
