package nl.knaw.huc.broccoli.service.anno

import arrow.core.getOrHandle
import com.jayway.jsonpath.Configuration.defaultConfiguration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL
import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.api.Constants.AR_BODY_TYPE
import nl.knaw.huc.broccoli.api.Constants.AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE
import nl.knaw.huc.broccoli.api.Constants.isEqualTo
import nl.knaw.huc.broccoli.api.Constants.isNotIn
import nl.knaw.huc.broccoli.api.Constants.overlap
import nl.knaw.huc.broccoli.api.WebAnnoPage
import org.slf4j.LoggerFactory
import javax.ws.rs.BadRequestException
import javax.ws.rs.NotFoundException
import kotlin.streams.asSequence

class FetchingAnnoRepo(private val annoRepoClient: AnnoRepoClient) : AnnoRepo {
    private val log = LoggerFactory.getLogger(javaClass)

    // choose 'null' over throwing exceptions when json paths cannot be found
    private val jsonParser = JsonPath.using(defaultConfiguration().addOptions(DEFAULT_PATH_LEAF_TO_NULL))

    override fun findByBodyId(containerName: String, bodyId: String): WebAnnoPage {
        log.info("getBodyId: containerName=[$containerName], bodyId=[$bodyId]")
        val before = System.currentTimeMillis()

        val query = mapOf("body.id" to bodyId)
        val result = annoRepoClient.filterContainerAnnotations(containerName, query)
            .getOrHandle { err -> throw BadRequestException("query failed: $err") }
            .annotations.asSequence()
            .map { it.getOrHandle { err -> throw BadRequestException("fetch failed: $err") } }
            .map(jsonParser::parse)
            .map(::WebAnnoPage)
            .firstOrNull()
            ?: throw NotFoundException("bodyId not found: $bodyId")

        val after = System.currentTimeMillis()
        log.info("fetching bodyId $bodyId took ${after - before} ms")

        return result
    }

    override fun fetchOverlappingAnnotations(
        containerName: String, source: String, start: Int, end: Int
    ): List<Map<String, Any>> {
        val query = mapOf(
            AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE to overlap(source, start, end),
            AR_BODY_TYPE to isNotIn("Line", "Page", "RepublicParagraph", "TextRegion", "Scan")
        )

        val startTime = System.currentTimeMillis()

        val overlappingAnnotations = annoRepoClient.filterContainerAnnotations(containerName, query)
            .getOrHandle { err -> throw BadRequestException("query failed: $err") }
            .annotations.asSequence()
            .map { it.getOrHandle { err -> throw BadRequestException("fetch failed: $err") } }
            .map(jsonParser::parse)
            .map { it.read<Map<String, Any>>("$") }
            .toList()

        val endTime = System.currentTimeMillis()
        log.info("fetching overlapping annotations took: ${endTime - startTime} ms")

        return overlappingAnnotations
    }

    override fun findOffsetRelativeTo(containerName: String, source: String, selector: TextSelector, type: String)
            : Pair<Int, String> {
        log.info("findOffsetRelativeTo: containerName=[$containerName], selector=$selector, type=[$type]")

        val query = mapOf(
            AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE to overlap(source, selector.start(), selector.end()),
            AR_BODY_TYPE to isEqualTo(type)
        )

        val anno = annoRepoClient.filterContainerAnnotations(containerName, query)
            .getOrHandle { err -> throw BadRequestException("query failed: $err") }
            .annotations.asSequence()
            .map { it.getOrHandle { err -> throw BadRequestException("fetch failed: $err") } }
            .map(jsonParser::parse)
            .map(::WebAnnoPage)
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

}
