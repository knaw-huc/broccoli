package nl.knaw.huc.broccoli.core

import nl.knaw.huc.broccoli.api.IndexDate
import nl.knaw.huc.broccoli.api.IndexQuery
import nl.knaw.huc.broccoli.api.IndexTerms

class ElasticQueryBuilder() {
    fun toElasticQuery(indexQuery: IndexQuery): String {
        val date = dateToEs(indexQuery.date)
        val terms = termsToEs(indexQuery.terms)
        val text = textToEs(indexQuery.text)

        return """{"bool": {"must": [$date, $terms, $text]}}"""
    }

    private fun textToEs(text: String) =
        """{"match_phrase_prefix": {"text": ${quote(text)}}}"""

    private fun termsToEs(terms: IndexTerms) = terms.map {
        """{"terms": {"${it.key}": ${it.value.map(::quote)}}}"""
    }.joinToString()

    private fun dateToEs(date: IndexDate) =
        """{"range": {"${date.name}": {"relation": "within", "gte": "${date.from}", "lte": "${date.to}"}}}"""

    private fun quote(s: String): String = '"' + s + '"'
}
