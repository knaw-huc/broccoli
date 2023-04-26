package nl.knaw.huc.broccoli.core

import nl.knaw.huc.broccoli.api.IndexDate
import nl.knaw.huc.broccoli.api.IndexQuery
import org.junit.jupiter.api.Test

class ElasticQueryBuilderTest {

    @Test
    fun toElasticQuery() {
        val q = IndexQuery(
            date = IndexDate("sessionDate", "1728-01-01", "1728-06-30"),
            terms = mapOf(
                "sessionWeekday" to listOf("ma", "di"),
                "propositionType" to listOf("missive", "secret")
            ),
            text = "Er was eens...",
            aggregations = null
        )

        System.err.println(ElasticQueryBuilder().toElasticQuery(q))
    }
}
