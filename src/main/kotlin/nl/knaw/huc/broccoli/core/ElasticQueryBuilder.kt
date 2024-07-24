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

    fun toElasticQuery() = ElasticQuery(
        from = from,
        size = size,
        sort = Sort(sortBy, sortOrder),

        query = ComplexQuery(
            bool = BoolQuery(
                must = mutableListOf<BaseQuery>().apply {
                    query.terms?.forEach {
                        add(TermsQuery(mapOf(it.key to it.value)))
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
        ),

        highlight = query.text?.let { queryText ->
            HighlightTerm(
                text = queryText,
                fragmentSize = fragmentSize,
                extraFields = index.fields.filter { it.type == "text" }.map { it.name }
            )
        },

        aggregations = (query.aggregations ?: index.fields.map { it.name })
            .map { parseAggregationParameters(it) }
            .also { logger.atDebug().addArgument(it).log("parsed aggregation params: {}") }
            .mapNotNull { params ->
                when (index.fields.find { it.name == params.fieldName }?.type) {
                    "keyword", "short", "byte" ->
                        TermAggregation(
                            name = params.fieldName,
                            numResults = params.numResults,
                            sortOrder = params.sortOrder
                        )

                    "date" -> DateAggregation(params.fieldName)
                    else -> null
                }
            }
            .let { Aggregations(it) }
    )

    fun toMultiFacetCountQueries() = mutableListOf<ElasticQuery>().apply {
        query.terms?.forEach { curTerm ->
            add(ElasticQuery(
                from = from,
                size = size,
                sort = Sort(sortBy, sortOrder),
                query = ComplexQuery(
                    bool = BoolQuery(
                        must = mutableListOf<BaseQuery>().apply {
                            query.terms?.forEach {
                                if (it.key != curTerm.key) add(TermsQuery(mapOf(it.key to it.value)))
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
                ),
                aggregations = Aggregations(listOf(
                    // use aggregation sort order / count, if specified
                    query.aggregations?.find { it.startsWith(curTerm.key + ':') }?.let {
                        parseAggregationParameters(it).let { params ->
                            TermAggregation(params.fieldName, params.numResults, params.sortOrder)
                        }
                    } ?: TermAggregation(curTerm.key))
                )))
        }
    }.toList()

    companion object {
        private val logger = LoggerFactory.getLogger(ElasticQueryBuilder::class.java)

        private val ES_FIELD_PREFIX = """^[a-zA-Z]*:""".toRegex()

        private data class ParsedAggParams(
            val fieldName: String,
            var numResults: Int? = null,
            var sortOrder: Map<String, Any>? = null
        )

        private val orderParams = mapOf(
            "keyAsc" to mapOf("_key" to "asc"),
            "keyDesc" to mapOf("_key" to "desc"),
            "countDesc" to mapOf("_count" to "desc")
        )

        private fun parseAggregationParameters(aggName: String): ParsedAggParams =
            ParsedAggParams(aggName.substringBeforeLast(delimiter = ':')).apply {
                aggName.substringAfterLast(delimiter = ':', missingDelimiterValue = "")
                    .split(',')
                    .forEach { param ->
                        param.toIntOrNull()?.let { numResults = it }
                        orderParams[param]?.let { sortOrder = it }
                    }
            }
    }
}
