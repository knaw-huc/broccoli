package nl.knaw.huc.broccoli.core

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import nl.knaw.huc.broccoli.api.IndexDate
import nl.knaw.huc.broccoli.api.IndexQuery
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class ElasticQueryBuilderTest {

    private val log = LoggerFactory.getLogger(javaClass)
    private val objectMapper = ObjectMapper().registerKotlinModule().writerWithDefaultPrettyPrinter()

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

        log.info("result: ${ElasticQueryBuilder().toElasticQuery(q)}")
    }

    @Test
    fun toElasticQuery2() {
        val q = IndexQuery(
            date = IndexDate("sessionDate", "1728-01-01", "1728-06-30"),
            terms = mapOf(
                "sessionWeekday" to listOf("ma", "di"),
                "propositionType" to listOf("missive", "secret")
            ),
            text = "Er was eens...",
            aggregations = null
        )

        val result = ElasticQueryBuilder().toElasticQuery(q)
        log.info("result: ${objectMapper.writeValueAsString(result)}")
    }
}
