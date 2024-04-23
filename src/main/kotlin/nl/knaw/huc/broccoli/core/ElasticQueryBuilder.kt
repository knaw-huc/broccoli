package nl.knaw.huc.broccoli.core

import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.config.IndexConfiguration
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

    fun query(query: IndexQuery) = apply { this.query = query }

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

        highlight = query.text
            ?.let { HighlightTerm(it, fragmentSize) },

        aggregations = (query.aggregations ?: index.fields.map { it.name })
            .mapNotNull { fieldName ->
                when (index.fields.find { it.name == fieldName }?.type) {
                    "keyword", "short", "byte" -> TermAggregation(fieldName)
                    "date" -> DateAggregation(fieldName)
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
                aggregations = Aggregations(listOf(TermAggregation(curTerm.key)))
            ))
        }
    }.toList()
}
