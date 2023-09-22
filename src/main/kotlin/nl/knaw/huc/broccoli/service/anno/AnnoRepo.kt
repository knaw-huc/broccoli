package nl.knaw.huc.broccoli.service.anno

import arrow.core.getOrElse
import com.jayway.jsonpath.Configuration.defaultConfiguration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL
import jakarta.ws.rs.BadRequestException
import jakarta.ws.rs.NotFoundException
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.api.Constants.AR_BODY_TYPE
import nl.knaw.huc.broccoli.api.Constants.AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE
import nl.knaw.huc.broccoli.api.Constants.AR_WITHIN_TEXT_ANCHOR_RANGE
import nl.knaw.huc.broccoli.api.Constants.isEqualTo
import nl.knaw.huc.broccoli.api.Constants.region
import nl.knaw.huc.broccoli.service.cache.LRUCache
import org.slf4j.LoggerFactory
import kotlin.streams.asSequence

class AnnoRepo(
    private val annoRepoClient: AnnoRepoClient,
    private val defaultContainerName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val cachedQueryResults =
        LRUCache<Pair<String, Map<String, Any>>, List<DocumentContext>>(capacity = CACHE_CAPACITY)

    // choose 'null' over throwing exceptions when json paths cannot be found
    private val jsonParser = JsonPath.using(defaultConfiguration().addOptions(DEFAULT_PATH_LEAF_TO_NULL))

    private fun liveQuery(containerName: String, query: Map<String, Any>) =
        annoRepoClient.filterContainerAnnotations(containerName, query)
            .getOrElse { err -> throw BadRequestException("query failed: $err") }
            .also { log.info("queryId: ${it.queryId}") }
            .annotations.asSequence()
            .map { it.getOrElse { err -> throw BadRequestException("fetch failed: $err") } }
            .map(jsonParser::parse)

    private fun cacheQuery(containerName: String, query: Map<String, Any>): List<DocumentContext> {
        val key = Pair(containerName, query)
        val cached = cachedQueryResults.get(key)
        if (cached != null) {
            log.info("cache hit for: $key, hashCode=${key.hashCode()}")
            return cached
        }

        log.info("cache miss for: $key, hashCode=${key.hashCode()}")
        val value = liveQuery(containerName, query).toList() // note: this pulls in the entire result list
        if (value.size < CACHE_RESULT_SET_SIZE_THRESHOLD) {
            log.info("resultSet.size (${value.size}) < $CACHE_RESULT_SET_SIZE_THRESHOLD) -> caching")
            cachedQueryResults.put(key, value)
        } else {
            log.info("resultSet.size (${value.size}) < $CACHE_RESULT_SET_SIZE_THRESHOLD) -> not caching")
        }
        return value
    }

    fun findByTiers(
        bodyType: String,
        tiers: List<Pair<String, Any>>
    ): List<AnnoRepoSearchResult> {
        val query = mutableMapOf<String, Any>("body.type" to bodyType)
        tiers.forEach { query["body.metadata.${it.first}"] = it.second }
        log.info("querying annorepo: $query")
        return cacheQuery(defaultContainerName, query)
            .map(::AnnoRepoSearchResult)
    }

    fun findByBodyId(bodyId: String) = findByBodyId(defaultContainerName, bodyId)
    fun findByBodyId(containerName: String, bodyId: String): AnnoRepoSearchResult {
        log.info("getBodyId: containerName=[$containerName], bodyId=[$bodyId]")
        val before = System.currentTimeMillis()

        val query = mapOf("body.id" to bodyId)
        val result = cacheQuery(containerName, query)
            .map(::AnnoRepoSearchResult)
            .firstOrNull()
            ?: throw NotFoundException("bodyId not found: $bodyId")

        val after = System.currentTimeMillis()
        log.info("fetching bodyId $bodyId took ${after - before} ms")

        return result
    }

    fun fetch(query: Map<String, Any>) = cacheQuery(defaultContainerName, query)

    fun fetchOverlap(source: String, start: Int, end: Int, bodyTypes: Map<String, Any>) =
        fetch(
            mapOf(
                AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE to region(source, start, end),
                AR_BODY_TYPE to bodyTypes
            )
        )

    fun streamOverlap(source: String, start: Int, end: Int, bodyTypes: Map<String, Any>) =
        liveQuery(
            containerName = defaultContainerName,
            query = mapOf(
                AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE to region(source, start, end),
                AR_BODY_TYPE to bodyTypes
            )
        )

    fun findWithin(source: String, start: Int, end: Int, constraints: Map<String, Any>) =
        fetch(constraints.plus(AR_WITHIN_TEXT_ANCHOR_RANGE to region(source, start, end)))


    fun findDistinct(field: String) = annoRepoClient
        .getDistinctFieldValues(defaultContainerName, field)
        .getOrElse { err -> throw BadRequestException("fetch distinct ($field) failed: $err") }
        .distinctValues

    fun findOffsetRelativeTo(source: String, selector: TextSelector, type: String) =
        findOffsetRelativeTo(defaultContainerName, source, selector, type)

    fun findOffsetRelativeTo(containerName: String, source: String, selector: TextSelector, type: String): Offset {
        log.info("findOffsetRelativeTo: containerName=[$containerName], selector=$selector, type=[$type]")

        val query = mapOf(
            AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE to region(source, selector.start(), selector.end()),
            AR_BODY_TYPE to isEqualTo(type)
        )

        val anno = cacheQuery(containerName, query)
            .map(::AnnoRepoSearchResult)
            .firstOrNull()
            ?: throw NotFoundException("overlap not found ($containerName,$source,$selector)")

        val start = anno.targetField<Int>("Text", "selector.start")
            .filter { it <= selector.start() }
            .max()

        log.info("closest [$type] starts at $start (absolute)")
        return Offset(start, anno.bodyId())
    }

    data class Offset(val value: Int, val id: String)

    companion object {
        const val CACHE_CAPACITY = 1000
        const val CACHE_RESULT_SET_SIZE_THRESHOLD = 100
    }
}
