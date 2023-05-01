package nl.knaw.huc.broccoli.core

import nl.knaw.huc.broccoli.api.*

class ElasticQueryBuilder {
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
            query = ComplexQuery(
                bool = BoolQuery(
                    must = queryTerms
                )
            ),
            highlight = indexQuery.text?.let { HighlightTerm(it) },
            aggregations = indexQuery.aggregations?.let { Aggregations(it) }
        )
    }
}
