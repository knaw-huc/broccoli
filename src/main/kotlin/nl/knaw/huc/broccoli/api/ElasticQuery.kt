package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ElasticQuery(
    @JsonProperty("_source") val source: Boolean = true,
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

data class DateQuery(
    @JsonIgnore val name: String,
    @JsonIgnore val from: String,
    @JsonIgnore val to: String
) : BaseQuery() {
    @JsonAnyGetter
    fun toJson() = mapOf(
        "range" to mapOf(
            name to mapOf(
                "relation" to "within",
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
    @JsonProperty("query") val query: Query,
) : BaseQuery()


data class Query(
    @JsonProperty("query_string") val queryString: QueryString
)

data class QueryString(
    val text: String
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
    @JsonIgnore val fragmenter: String
) {
    @JsonAnyGetter
    fun toJson() = mapOf(
        "fields" to mapOf(
            "text" to mapOf(
                "type" to "experimental",
                "fragmenter" to fragmenter,
                "options" to mapOf(
                    "return_snippets_and_offsets" to true
                )
            )
        ),
        "highlight_query" to FullTextQuery(Query(QueryString(text)))
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
            "size" to 100
        )
    )
}
