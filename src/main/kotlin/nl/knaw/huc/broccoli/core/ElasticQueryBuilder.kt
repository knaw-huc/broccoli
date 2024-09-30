package nl.knaw.huc.broccoli.core

import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.config.IndexConfiguration
import org.slf4j.LoggerFactory
import kotlin.properties.Delegates

class ElasticQueryBuilder(private val index: IndexConfiguration) {
    private var from by Delegates.notNull<Int>()
    private var size by Delegates.notNull<Int>()
    private var fragmentSize by Delegates.notNull<Int>()
    private lateinit var sortBy: String
    private lateinit var sortOrder: String
    private lateinit var query: IndexQuery

    fun sortBy(sortBy: String) = apply { this.sortBy = sortBy }

    fun sortOrder(sortOrder: String) = apply { this.sortOrder = sortOrder }

    fun fragmentSize(size: Int) = apply { this.fragmentSize = size }

    fun from(from: Int) = apply { this.from = from }

    fun size(size: Int) = apply { this.size = size }

    fun query(query: IndexQuery) = apply { this.query = normalizeQuery(query) }

    fun toElasticQuery() = ElasticQuery(
        from = from,
        size = size,
        sort = Sort(sortBy, sortOrder),
        query = buildMainQuery(),
        highlight = query.text?.let { queryText ->
            HighlightTerm(
                text = queryText,
                fragmentSize = fragmentSize,
                extraFields = index.fields.filter { it.type == "text" }.map { it.name }
            )
        },
        aggregations = buildAggregations(query.aggregations?.keys ?: configuredFieldNames())
    )

    fun toMultiFacetCountQueries() = mutableListOf<ElasticQuery>()
        .apply {
            query.terms?.keys?.forEach { name ->
                add(
                    ElasticQuery(
                        from = from,
                        size = size,
                        sort = Sort(sortBy, sortOrder),
                        query = buildMainQuery { it.key != name },
                        aggregations = buildAggregations(listOf(name))
                    )
                )
            }
        }

    private fun buildAggregations(names: Collection<String>) =
        Aggregations(
            names.mapNotNull { aggName ->
                query.aggregations?.get(aggName)?.let { aggSpec ->
                    when (configuredFieldType(aggName)) {
                        "byte", "keyword", "short" ->
                            TermAggregation(
                                name = aggName,
                                numResults = aggSpec["size"] as Int,
                                sortOrder = orderParams[aggSpec["order"]]
                            )

                        "date" -> DateAggregation(aggName)

                        "nested" -> {
                            @Suppress("UNCHECKED_CAST")
                            val nestedAggSpec = aggSpec as Map<String, Map<String, Any>>
                            NestedAggregation(name = aggName, fields = nestedAggSpec)
                        }

                        else -> null
                    }
                }
            }
        )

    private fun normalizeQuery(query: IndexQuery): IndexQuery {
        return IndexQuery(
            date = query.date,
            range = query.range,
            terms = query.terms,
            aggregations = query.aggregations,
            text = query.text
                ?.trim()
                ?.let { q ->
                    if (ES_FIELD_PREFIX.matches(q)) q else buildString {
                        append("text:$q")
                        index.fields
                            .filter { it.type == "text" }
                            .map { it.name }
                            .forEach { fieldName ->
                                append(" OR ")
                                append("$fieldName:$q")
                            }
                    }
                },
        )
    }

    private fun buildMainQuery(predicate: ((Map.Entry<String, Any>) -> Boolean) = { true }) = ComplexQuery(
        bool = BoolQuery(
            must = mutableListOf<BaseQuery>().apply {
                query.terms
                    ?.filter(predicate)
                    ?.forEach {
                        when (it.value) {
                            is List<*> -> {
                                add(TermsQuery(mapOf(it.key to (it.value as List<*>))))
                            }

                            is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                add(NestedQuery(it.key, it.value as Map<String, List<String>>))
                            }
                        }
                    }
                query.date?.let {
                    add(RangeQuery(it.name, it.from, it.to, relation = "within"))
                }
                query.range?.let {
                    add(RangeQuery(it.name, it.from, it.to))
                }
                query.text?.let {
                    add(FullTextQuery(QueryString(it)))
                }
            }
        )
    )

    private fun configuredFieldNames() = index.fields.map { it.name }

    private fun configuredFieldType(name: String) = index.fields.find { it.name == name }?.type

    companion object {
        private val logger = LoggerFactory.getLogger(ElasticQueryBuilder::class.java)

        private val ES_FIELD_PREFIX = """^[a-zA-Z]*:""".toRegex()

        private val orderParams = mapOf(
            "keyAsc" to mapOf("_key" to "asc"),
            "keyDesc" to mapOf("_key" to "desc"),
            "countDesc" to mapOf("_count" to "desc")
        )
    }
}
