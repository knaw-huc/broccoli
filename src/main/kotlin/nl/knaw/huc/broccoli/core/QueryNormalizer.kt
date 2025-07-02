package nl.knaw.huc.broccoli.core

import jakarta.ws.rs.BadRequestException
import nl.knaw.huc.broccoli.api.IndexQuery
import nl.knaw.huc.broccoli.config.IndexConfiguration
import org.slf4j.LoggerFactory

class QueryNormalizer(private val index: IndexConfiguration) {
    fun normalizeQuery(query: IndexQuery): IndexQuery {
        val available = index.fields.filter { it.type == "text" }.map { it.name }

        val todoViews = query.textViews?.apply {
            filterNot { it in available }.apply {
                if (isNotEmpty())
                    throw BadRequestException("Unknown textView(s): $this, available: $available")
            }
        } ?: listOf("text").plus(available)

        logger.atTrace()
            .addKeyValue("available", available)
            .addKeyValue("requested", query.textViews)
            .addKeyValue("todoViews", todoViews)
            .log("computed views")

        val todoText = query.text?.trim()?.let { q ->
            if (ES_FIELD_PREFIX.matches(q)) {
                q
            } else {
                todoViews.joinToString(separator = " OR ") { "${it}:$q" }
            }
        }

        logger.atTrace().addKeyValue("text", todoText).log("computed text")

        return IndexQuery(
            date = query.date,
            range = query.range,
            terms = query.terms,
            aggregations = query.aggregations,
            textViews = todoViews,
            text = todoText
        )
    }

    companion object {
        private val ES_FIELD_PREFIX = """^[a-zA-Z]*:""".toRegex()

        private val logger = LoggerFactory.getLogger(QueryNormalizer::class.java)
    }
}
