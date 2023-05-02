package nl.knaw.huc.broccoli.core

import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.config.IndexConfiguration

class ElasticQueryBuilder(private val index: IndexConfiguration) {
    private var from: Int = 0
    private var size: Int = 10
    private var sort: String = "_score"

    fun sort(sort: String): ElasticQueryBuilder {
        this.sort = sort
        return this
    }

    fun from(from: Int): ElasticQueryBuilder {
        this.from = from
        return this
    }

    fun size(size: Int): ElasticQueryBuilder {
        this.size = size
        return this
    }

    fun toElasticQuery(indexQuery: IndexQuery): ElasticQuery {
        val queryTerms = mutableListOf<BaseQuery>()

        indexQuery.terms?.forEach {
            queryTerms.add(TermsQuery(mapOf(it.key to it.value)))
        }

        indexQuery.date?.let {
            queryTerms.add(DateQuery(it.name, it.from, it.to))
        }

        indexQuery.text?.let {
            queryTerms.add(FullTextQuery(MatchPhrasePrefixQuery(it)))
        }

        return ElasticQuery(
            from = from,
            size = size,
            sort = sort,

            query = ComplexQuery(
                bool = BoolQuery(
                    must = queryTerms
                )
            ),

            highlight = indexQuery.text?.let { HighlightTerm(it) },

            aggregations = indexQuery.aggregations
                ?.filter { index.fields.any { field -> field.name == it } }
                ?.let { Aggregations(it) }
        )
    }
}
