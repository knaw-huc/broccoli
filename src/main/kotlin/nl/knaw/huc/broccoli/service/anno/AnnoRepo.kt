package nl.knaw.huc.broccoli.service.anno

import arrow.core.getOrHandle
import com.jayway.jsonpath.Configuration.defaultConfiguration
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.api.Constants.AR_BODY_TYPE
import nl.knaw.huc.broccoli.api.Constants.AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE
import nl.knaw.huc.broccoli.api.Constants.isEqualTo
import nl.knaw.huc.broccoli.api.Constants.overlap
import nl.knaw.huc.broccoli.service.cache.LRUCache
import org.slf4j.LoggerFactory
import javax.ws.rs.BadRequestException
import javax.ws.rs.NotFoundException
import kotlin.streams.asSequence

class AnnoRepo(private val annoRepoClient: AnnoRepoClient) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val cachedQueryResults =
        LRUCache<Pair<String, Map<String, Any>>, List<DocumentContext>>(capacity = CACHE_CAPACITY)

    // choose 'null' over throwing exceptions when json paths cannot be found
    private val jsonParser = JsonPath.using(defaultConfiguration().addOptions(DEFAULT_PATH_LEAF_TO_NULL))

    private fun queryAnnoRepo(containerName: String, query: Map<String, Any>) =
        annoRepoClient.filterContainerAnnotations(containerName, query)
            .getOrHandle { err -> throw BadRequestException("query failed: $err") }
            .annotations.asSequence()
            .map { it.getOrHandle { err -> throw BadRequestException("fetch failed: $err") } }
            .map(jsonParser::parse)
            .toList()

    private fun cacheQuery(containerName: String, query: Map<String, Any>): List<DocumentContext> {
        val key = Pair(containerName, query)
        val cached = cachedQueryResults.get(key)
        if (cached != null) {
            log.info("cache hit for: $key")
            return cached
        }

        log.info("cache miss for: $key")
        val value = queryAnnoRepo(containerName, query)
        if (value.size < CACHE_RESULT_SET_SIZE_THRESHOLD) {
            log.info("resultSet.size (${value.size}) < $CACHE_RESULT_SET_SIZE_THRESHOLD) -> caching")
            cachedQueryResults.put(key, value)
        }
        return value
    }

    fun findByBodyId(containerName: String, bodyId: String): BodyIdSearchResult {
        log.info("getBodyId: containerName=[$containerName], bodyId=[$bodyId]")
        val before = System.currentTimeMillis()

        val query = mapOf("body.id" to bodyId)
        val result = cacheQuery(containerName, query)
            .map(::BodyIdSearchResult)
            .firstOrNull()
            ?: throw NotFoundException("bodyId not found: $bodyId")

        val after = System.currentTimeMillis()
        log.info("fetching bodyId $bodyId took ${after - before} ms")

        return result
    }

    fun fetchOverlap(
        containerName: String, source: String, start: Int, end: Int,
        bodyTypes: Map<String, Any>,
    ): List<Map<String, Any>> = cacheQuery(
        containerName = containerName,
        query = mapOf(
            AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE to overlap(source, start, end),
            AR_BODY_TYPE to bodyTypes
        )
    ).map { it.read<Map<String, Any>>("$") }.toList()

    fun findOffsetRelativeTo(containerName: String, source: String, selector: TextSelector, type: String)
            : Pair<Int, String> {
        log.info("findOffsetRelativeTo: containerName=[$containerName], selector=$selector, type=[$type]")

        val query = mapOf(
            AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE to overlap(source, selector.start(), selector.end()),
            AR_BODY_TYPE to isEqualTo(type)
        )

        val anno = cacheQuery(containerName, query)
            .map(::BodyIdSearchResult)
            .firstOrNull()
            ?: throw NotFoundException("overlap not found ($containerName,$source,$selector)")

        val start = anno.targetField<Int>("Text", "selector.start")
            .filter { it <= selector.start() }
            .max()

        log.info("closest [$type] starts at $start (absolute)")
        return Pair(start, anno.bodyId())
    }

    data class TextSelector(private val context: Map<String, Any>) {
        fun start(): Int = context["start"] as Int
        fun beginCharOffset(): Int? = context["beginCharOffset"] as Int?
        fun end(): Int = context["end"] as Int
        fun endCharOffset(): Int? = context["endCharOffset"] as Int?
    }

    companion object {
        const val CACHE_CAPACITY = 100
        const val CACHE_RESULT_SET_SIZE_THRESHOLD = 100
    }
}
