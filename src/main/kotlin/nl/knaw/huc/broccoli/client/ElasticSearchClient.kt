import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.ParseContext
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.api.IndexQuery
import nl.knaw.huc.broccoli.config.IndexConfiguration
import nl.knaw.huc.broccoli.core.ElasticQueryBuilder
import nl.knaw.huc.broccoli.resources.projects.Params
import nl.knaw.huc.broccoli.service.extractAggregations
import nl.knaw.huc.broccoli.service.toJsonString
import org.slf4j.LoggerFactory

class ElasticSearchClient(
    private val client: Client,
    private val jsonParser: ParseContext,
    private val jsonWriter: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createIndex(
        index: IndexConfiguration,
        projectUri: String
    ): String {
        val properties = mutableMapOf(
            "text" to mapOf(
                "type" to "text",
                "fields" to mapOf(
                    "tokenCount" to mapOf(
                        "type" to "token_count",
                        "analyzer" to "fulltext_analyzer"
                    )
                ),
                "index_options" to "offsets",
                "analyzer" to "fulltext_analyzer"
            ),
        )
        index.fields.forEach { field ->
            field.type.let { type ->
                properties[field.name] = mapOf("type" to type)
            }
        }

        val mappings = mapOf("properties" to properties)

        return """
            {
              "mappings": ${mappings.toJsonString()},
              "settings": {
                "analysis": {
                  "analyzer": {
                    "fulltext_analyzer": {
                      "type": "custom",
                      "tokenizer": "standard",
                      "filter": [
                        "lowercase"
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()
            .also { mapping -> log.info("mapping: $mapping") }
            .let { mapping ->
                client.target(projectUri)
                    .path(index.name)
                    .request()
                    .put(Entity.json(mapping))
                    .also { log.info("response: $it") }
                    .readEntityAsJsonString()
                    .also { log.info("entity: $it") }
            }
    }

    fun searchIndex(
        index: IndexConfiguration,
        projectUri: String,
        query: IndexQuery,
        params: Params,
    ): MutableMap<String, Any> {
        val queryBuilder = ElasticQueryBuilder(index)
            .query(query)
            .from(params.from)
            .size(params.size)
            .sortBy(params.sortBy)
            .sortOrder(params.sortOrder.toString())
            .fragmentSize(params.fragmentSize)

        val baseQuery = queryBuilder.toElasticQuery()
        log.atTrace()
            .addKeyValue("ES query", jsonWriter.writeValueAsString(baseQuery))
            .log("base")

        val baseResult = client
            .target(projectUri).path(index.name).path("_search")
            .request().post(Entity.json(baseQuery))
        validateElasticResult(baseResult, query)
        val baseJson = baseResult.readEntityAsJsonString()
        log.atTrace().addKeyValue("json", baseJson).log("base")

        val result: MutableMap<String, Any> = mutableMapOf()
        val aggs: MutableMap<String, Any> = mutableMapOf()
        jsonParser.parse(baseJson).let { context ->
            context.read<Map<String, Any>>("$.hits.total")
                ?.let { result["total"] = it }

            extractAggregations(index, context)?.let { aggs.putAll(it) }
            log.atTrace().addKeyValue("aggs", aggs).log("base")

            context.read<List<Map<String, Any>>>("$.hits.hits[*]")
                ?.map { buildHitResult(index, it) }
                ?.let { result["results"] = it }
        }

        val auxQueries = queryBuilder.toMultiFacetCountQueries()
        auxQueries.forEachIndexed { auxIndex, auxQuery ->
            log.atTrace().addKeyValue(
                "query[$auxIndex]",
                jsonWriter.writeValueAsString(auxQuery)
            ).log("aux")

            val auxResult = client
                .target(projectUri)
                .path(index.name)
                .path("_search")
                .request()
                .post(Entity.json(auxQuery))
            validateElasticResult(auxResult, query)
            val auxJson = auxResult.readEntityAsJsonString()
            log.atTrace().addKeyValue("json", auxJson).log("aux")

            jsonParser.parse(auxJson).let { context ->
                extractAggregations(index, context)
                    ?.forEach { entry ->
                        @Suppress("UNCHECKED_CAST")
                        (aggs[entry.key] as MutableMap<String, Any>).putAll(
                            entry.value as Map<String, Any>
                        )
                    }
            }
        }

        // use LinkedHashMap to fix aggregation order
        result["aggs"] = LinkedHashMap<String, Any?>().apply {
            query.aggregations?.keys?.forEach { name ->
                val nameAndOrder =
                    "$name@${query.aggregations[name]?.get("order")}"
                if (!aggs.containsKey(name) && aggs.containsKey(nameAndOrder)) {
                    aggs[name] = aggs[nameAndOrder] as Any
                }
                (aggs[name] as MutableMap<*, *>?)?.apply {
                    val desiredAmount: Int =
                        (query.aggregations[name]?.get("size") as Int?) ?: size
                    if (desiredAmount < entries.size) {
                        val keep = LinkedHashMap<Any, Any>()
                        entries.take(desiredAmount).forEach {
                            keep[it.key as Any] = it.value as Any
                        }
                        aggs[name] = keep
                    }
                }
            }
            // prefer query string order; default to order from config
            (query.aggregations?.keys
                ?: index.fields.map { it.name }).forEach { name ->
                aggs[name]?.let { aggregationResult ->
                    put(
                        name,
                        aggregationResult
                    )
                }
            }
        }
        return result;

    }

    fun deleteIndex(
        index: IndexConfiguration,
        projectUri: String,
    ): String {
        return client.target(projectUri)
            .path(index.name)
            .request()
            .delete()
            .also { log.info("response: $it") }
            .readEntityAsJsonString()
            .also { log.info("entity: $it") }
    }

    private fun validateElasticResult(
        result: Response,
        queryString: IndexQuery
    ) {
        if (result.status != 200) {
            log.atWarn()
                .addKeyValue("status", result.status)
                .addKeyValue("query", queryString)
                .addKeyValue("result", result.readEntityAsJsonString())
                .log("ElasticSearch failed")
            throw BadRequestException("Query not understood: $queryString")
        }
    }

    private fun buildHitResult(
        index: IndexConfiguration,
        hit: Map<String, Any>
    ) =
        mutableMapOf("_id" to hit["_id"]).apply {
            // store highlight if available
            hit["highlight"]?.let { put("_hits", it) }

            @Suppress("UNCHECKED_CAST")
            val source = hit["_source"] as Map<String, Any>

            // store all configured index fields with their search result, if any
            index.fields.forEach { field ->
                source[field.name]?.let { put(field.name, it) }
            }
        }

    private fun Response.readEntityAsJsonString(): String =
        readEntity(String::class.java) ?: ""

}