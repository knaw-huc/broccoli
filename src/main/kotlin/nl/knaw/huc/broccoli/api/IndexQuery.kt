package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

data class IndexQuery
@JsonCreator
constructor(
    @Schema(description = "Restrict results to the project's configured 'date' field, given as a " +
            "{name, from, to} range. 'from'/'to' are inclusive ISO 8601 dates; either may be omitted " +
            "for an open-ended range.")
    val date: IndexRange?,

    @Schema(description = "Restrict results by exact-match term filters, keyed by field name. Each " +
            "value's shape depends on the field's configured type: a list of strings for a plain " +
            "keyword field, or a nested map for a 'nested' or 'logical' field, as set up in the " +
            "project's index configuration.")
    val terms: IndexTerms?,

    @Schema(description = "Free-text query, run against the project's full-text field.")
    val text: String?,

    @Schema(description = "Restrict full-text search to these text views. Valid values are project-specific " +
            "(they come from the project's configured text views, e.g. 'letterOriginalText' for one project " +
            "and something else for another) — see GET /projects/{projectId}/views for the current set.")
    val textViews: List<String>?,

    @Schema(description = "Restrict results to a numeric or date range on a field other than 'date', " +
            "given as a {name, from, to} range. 'from'/'to' are inclusive; either may be omitted for " +
            "an open-ended range.")
    val range: IndexRange?,

    @JsonProperty("aggs")
    @Schema(description = "Requests aggregation buckets, keyed by field name. Each value configures that " +
            "field's aggregation, e.g. {\"size\": 10, \"order\": \"countDesc\"} for a term aggregation. " +
            "Supported keys depend on the field's configured type in the project's index configuration.")
    val aggregations: Map<String, Map<String, Any>>? = null
) {
    override fun toString(): String = buildString {
        text?.let { append(it) }
        append('|')
        terms?.let { append(it) }
        append('|')
        date?.let { append(it) }
        append('|')
        range?.let { append(it) }
        append('|')
    }
}

typealias IndexTerms = Map<String, Any>

data class IndexRange(val name: String, val from: String?, val to: String?) {
    override fun toString(): String = "$name:[$from,$to]"
}
