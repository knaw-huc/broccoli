package nl.knaw.huc.broccoli.core

import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.config.IndexConfiguration
import nl.knaw.huc.broccoli.resources.projects.ProjectsResource.FragOpts
import kotlin.properties.Delegates

class ElasticQueryBuilder(private val index: IndexConfiguration) {
    private var from by Delegates.notNull<Int>()
    private var size by Delegates.notNull<Int>()
    private lateinit var sort: String
    private lateinit var frag: FragOpts
    private lateinit var query: IndexQuery

    fun sort(sort: String) = apply { this.sort = sort }

    fun frag(frag: FragOpts) = apply { this.frag = frag }
    fun from(from: Int) = apply { this.from = from }

    fun size(size: Int) = apply { this.size = size }

    fun query(query: IndexQuery) = apply { this.query = query }

    fun toElasticQuery() = ElasticQuery(
        from = from,
        size = size,
        sort = sort,

        query = ComplexQuery(
            bool = BoolQuery(
                must = mutableListOf<BaseQuery>().apply {
                    query.terms?.forEach {
                        add(TermsQuery(mapOf(it.key to it.value)))
                    }
                    query.date?.let {
                        add(DateQuery(it.name, it.from, it.to))
                    }
                    query.text?.let {
                        add(FullTextQuery(MatchPhrasePrefixQuery(it)))
                    }
                }
            )
        ),

        highlight = query.text
            ?.let { HighlightTerm(it, frag) },

        aggregations = (query.aggregations ?: index.fields.map { it.name })
            .mapNotNull { name ->
                when (index.fields.find { it.name == name }?.type) {
                    "keyword", "short", "byte" -> TermAggregation(name)
                    "date" -> DateAggregation(name)
                    else -> null
                }
            }
            .let { Aggregations(it) }
    )

    private fun isConfiguredIndexField(name: String) = index.fields.any { it.name == name }
}
