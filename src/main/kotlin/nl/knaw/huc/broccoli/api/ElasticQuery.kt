package nl.knaw.huc.broccoli.api

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import nl.knaw.huc.broccoli.api.Constants.TEXT_TOKEN_COUNT

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ElasticQuery(
    @JsonProperty("_source") val source: Boolean = true,
    @JsonProperty("track_total_hits") val trackTotalHits: Boolean = true,
    @JsonProperty("fields") val getTextTokenCount: List<String> = listOf(TEXT_TOKEN_COUNT),
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
    @JsonIgnore val from: String?,
    @JsonIgnore val to: String?,
    @JsonIgnore val relation: String? = "intersects"
) : BaseQuery() {
    @JsonAnyGetter
    fun toJson() = mapOf(
        "range" to mapOf(
            name to mutableMapOf("relation" to relation).apply {
                from?.let { put("gte", it) }
                to?.let { put("lte", it) }
            }.toMap() // back to read-only map
        )
    )
}

data class NestedQuery(
    @JsonIgnore val fieldName: String,
    @JsonIgnore val constraints: Map<String, List<String>>
) : BaseQuery() {
    @JsonAnyGetter
    fun toJson() = mapOf(
        "nested" to mapOf(
            "path" to fieldName,
            "query" to mapOf(
                "bool" to mapOf(
                    "filter" to mutableListOf<Map<String, Map<String, List<String>>>>(
                    ).apply {
                        constraints.forEach { (nestedFieldName, allowedValues) ->
                            add(
                                mapOf("terms" to mapOf("$fieldName.$nestedFieldName" to allowedValues))
                            )
                        }
                    }
                )
            )
        )
    )
}

data class TermsQuery(
    val terms: Map<String, Any>
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
    @JsonIgnore val fragmentSize: Int,
    @JsonIgnore val extraFields: List<String>? = null
) {
    @JsonAnyGetter
    fun toJson() = mapOf(
        "fields" to mutableMapOf(
            "text" to mapOf(
                "type" to "unified",
                "fragment_size" to fragmentSize,
            )
        ).apply {
            extraFields?.forEach {
                put(it, mapOf("type" to "unified"))
            }
        },
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

class TermAggregation(
    name: String,
    private val numResults: Int? = null,
    private val sortOrder: Map<String, Any>? = null
) : Aggregation(name) {
    override fun toJson() = mapOf(
        "terms" to mutableMapOf<String, Any>("field" to name).apply {
            numResults?.let { put("size", it) }
            sortOrder?.let { put("order", it) }
        }
    )
}

class NestedAggregation(
    name: String,
    private val fields: Map<String, Map<String, Any>>
) : Aggregation(name) {
    override fun toJson(): Map<String, Map<String, Any>> = mapOf(
        "nested" to mapOf("path" to name),
        "aggregations" to mutableMapOf<String, Any>().apply {
            fields.forEach { (nestedFieldName, aggSpec) ->
                put(
                    nestedFieldName, mapOf(
                        "terms" to mutableMapOf<String, Any>("field" to "$name.$nestedFieldName").apply {
                            aggSpec["size"]?.let { put("size", it) }
                            aggSpec["order"]?.let<Any, Unit> { order ->
                                put(
                                    "order", when (order) {
                                        "keyAsc" -> mapOf("_key" to "asc")
                                        "keyDesc" -> mapOf("_key" to "desc")
                                        else -> mapOf("_count" to "desc")
                                    }
                                )
                            }
                        },
                        "aggregations" to mapOf<String, Map<String, Map<String, Any>>>(
                            "documents" to mapOf(
                                "reverse_nested" to emptyMap()
                            )
                        )
                    )
                )
            }
        }
    )
}
