package nl.knaw.huc.broccoli.api

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Search results for a project's index query.")
data class SearchResult(
    val total: TotalHits,
    val results: List<SearchHit>,
    @Schema(description = "Aggregation buckets, keyed by field name. Structure depends on the field's " +
            "configured type (term, date, nested, or logical) in the project's index configuration.")
    val aggs: Map<String, Any>
)

data class TotalHits(
    @Schema(description = "Number of matching documents.")
    val value: Int,

    @Schema(description = "Whether 'value' is exact or a lower bound, e.g., 'eq' or 'gte'.")
    val relation: String
)

@Schema(
    description = "A single search hit. '_id', '_hits', 'date', and 'type' are common across projects; " +
            "any other field is one of the project's configured index fields, so the exact set beyond " +
            "those varies per project and index.",
    additionalProperties = Schema.AdditionalPropertiesValue.TRUE
)
data class SearchHit(
    @Schema(description = "Identifier of the matching annotation.")
    val _id: String,

    @Schema(description = "Highlighted text fragments, keyed by text view name. Only present when the " +
            "query included a 'text' search term.")
    val _hits: Map<String, List<String>>? = null,

    @Schema(description = "The hit's configured body type, e.g. 'letter'.")
    val type: String? = null,

    @Schema(description = "The hit's date range, if the project configures a 'date' field.")
    val date: HitDateRange? = null
)

data class HitDateRange(
    @Schema(description = "Start of the date range, ISO 8601, with precision as recorded (year, month, or day).")
    val gte: String?,

    @Schema(description = "End of the date range, ISO 8601, with precision as recorded (year, month, or day).")
    val lte: String?
)
