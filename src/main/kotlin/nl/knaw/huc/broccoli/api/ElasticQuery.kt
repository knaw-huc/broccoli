package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import nl.knaw.huc.broccoli.resources.projects.ProjectsResource.FragOpts
import org.slf4j.LoggerFactory

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ElasticQuery(
    val query: ComplexQuery,
    val highlight: HighlightTerm? = null,
    val aggregations: Aggregations? = null,
    @JsonProperty("_source") val source: Boolean = true,
    val from: Int = 0,
    val size: Int = 10,
    val sort: String = "_score"
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
    @JsonIgnore val terms: List<String>
    /*"aggs": {
    "resolutionPropositionTypes": {
      "terms": {
        "field": "propositionType",
        "size": 10
      }
    }
     */
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @JsonAnyGetter
    fun toJson() = mutableMapOf<String, Any>().apply {
        terms.forEach {
            log.info("it -> $it")
            put(it, mapOf("terms" to mapOf("field" to it, "size" to 10)))
            log.info("map now: $this")
        }
    }
}
