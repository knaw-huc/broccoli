package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ElasticQuery(
    @JsonProperty("_source") val source: Boolean = true,
    @JsonProperty("track_total_hits") val trackTotalHits: Boolean = true,
    @JsonProperty("fields") val getTextTokenCount: List<String> = listOf("text.tokenCount"),
    val from: Int,
    val size: Int,
    val sort: Sort,
    val query: ComplexQuery,
    val highlight: HighlightTerm? = null,
    val aggregations: Aggregations? = null
)

data class ComplexQuery(
    val bool: BoolQuery
)

data class BoolQuery(
    val must: List<BaseQuery>
)

abstract class BaseQuery

data class RangeQuery(
    @JsonIgnore val name: String,
    @JsonIgnore val from: String,
    @JsonIgnore val to: String,
    @JsonIgnore val relation: String? = "intersects"
) : BaseQuery() {
    @JsonAnyGetter
    fun toJson() = mapOf(
        "range" to mapOf(
            name to mapOf(
                "relation" to relation,
                "gte" to from,
                "lte" to to
            )
        )
    )
}

data class TermsQuery(
    val terms: Map<String, List<String>>
) : BaseQuery()

data class FullTextQuery(
    @JsonProperty("query_string") val queryString: QueryString
) : BaseQuery()

data class QueryString(
    val query: String
)

data class Sort(
    @JsonIgnore val sortBy: String,
    @JsonIgnore val sortOrder: String
) {
    @JsonAnyGetter
    fun toJson() = mapOf(sortBy to mapOf("order" to sortOrder))
}

data class HighlightTerm(
    @JsonIgnore val text: String,
    @JsonIgnore val fragmentSize: Int
) {
    @JsonAnyGetter
    fun toJson() = mapOf(
        "fields" to mapOf(
            "text" to mapOf(
                "type" to "unified",
                "fragment_size" to fragmentSize,
            )
        ),
        "highlight_query" to FullTextQuery(QueryString(text))
    )
}

data class Aggregations(
    @JsonIgnore val aggs: List<Aggregation>
) {
    @JsonAnyGetter
    fun toJson() = aggs.associate { it.name to it.toJson() }
}

abstract class Aggregation(val name: String) {
    abstract fun toJson(): Map<String, Map<String, Any>>
}

class DateAggregation(name: String) : Aggregation(name) {
    override fun toJson() = mapOf(
        "auto_date_histogram" to mapOf(
            "buckets" to 10,
            "field" to name,
            "format" to "yyyy-MM-dd"
        )
    )
}

class TermAggregation(name: String) : Aggregation(name) {
    override fun toJson() = mapOf(
        "terms" to mapOf(
            "field" to name,
            "size" to 250
        )
    )
}
