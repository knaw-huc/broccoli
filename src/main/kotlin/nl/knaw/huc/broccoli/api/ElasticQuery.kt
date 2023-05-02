package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import nl.knaw.huc.broccoli.resources.projects.ProjectsResource.FragOpts

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ElasticQuery(
    @JsonProperty("_source") val source: Boolean = true,
    val from: Int,
    val size: Int,
    val sort: String,
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
    @JsonProperty("match_phrase_prefix") val matchPhrasePrefixQuery: MatchPhrasePrefixQuery
) : BaseQuery()

data class MatchPhrasePrefixQuery(
    val text: String
)

data class HighlightTerm(
    @JsonIgnore val text: String,
    @JsonIgnore val fragmenter: FragOpts = FragOpts.SCAN
) {
    @JsonAnyGetter
    fun toJson() = mapOf(
        "fields" to mapOf(
            "text" to mapOf(
                "type" to "experimental",
                "fragmenter" to fragmenter.toString(),
                "options" to mapOf(
                    "return_snippets_and_offsets" to true
                )
            )
        ),
        "highlight_query" to FullTextQuery(MatchPhrasePrefixQuery(text))
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
        "date_histogram" to mapOf(
            "field" to name,
            "format" to "yyyy-MM-dd",
            "calendar_interval" to "week"
        )
    )
}

class TermAggregation(name: String) : Aggregation(name) {
    override fun toJson() = mapOf(
        "terms" to mapOf(
            "field" to name
        )
    )
}
