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
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.core.GenericType
import kotlin.streams.asSequence

class FetchingAnnoRepo(
    private val annoRepoClient: AnnoRepoClient,
    private val revision: String?
) : AnnoRepo {
    private val log = LoggerFactory.getLogger(javaClass)

    // choose 'null' over throwing exceptions when json paths cannot be found
    private val jsonParser = JsonPath.using(defaultConfiguration().addOptions(DEFAULT_PATH_LEAF_TO_NULL))

    override fun getScanAnno(volumeName: String, bodyId: String): ScanPageResult {
        log.info("getScanAnno: volumeName=[$volumeName], bodyId=[$bodyId]")
        val before = System.currentTimeMillis()

        val anno = findByBodyId(volumeName, bodyId)
        val textTargets = anno.target<Any>("Text")
        log.info("data: $textTargets")

        val text = ArrayList<String>()
        val annos = ArrayList<Map<String, Any>>()
        textTargets.forEach {
            val sourceUrl = it["source"] as String
            if (it["selector"] == null) {
                text.addAll(fetchTextLines(sourceUrl))
            } else {
                @Suppress("UNCHECKED_CAST") val selector = it["selector"] as Map<String, Any>
                val start = selector["start"] as Int
                val end = selector["end"] as Int
                log.info("start: $start, end: $end")
                annos.addAll(fetchOverlappingAnnotations(volumeName, sourceUrl, start, end))
            }
        }

        val after = System.currentTimeMillis()
        log.info("fetching scan page took ${after - before} ms")

        return ScanPageResult(annos, text)
    }

    override fun findByBodyId(volumeName: String, bodyId: String): WebAnnoPage {
        log.info("getBodyId: volumeName=[$volumeName], bodyId=[$bodyId]")
        val before = System.currentTimeMillis()

        val query = mapOf("body.id" to bodyId)
        val containerName = buildContainerName(volumeName)
        val result = annoRepoClient.filterContainerAnnotations(containerName, query)
            .getOrHandle { err -> throw BadRequestException("query failed: $err") }
            .annotations.asSequence()
            .map { it.getOrHandle { err -> throw BadRequestException("fetch failed: $err") } }
            .map(jsonParser::parse)
            .map(::WebAnnoPage)
            .firstOrNull()
            ?: throw NotFoundException("bodyId not found: $bodyId")

        val after = System.currentTimeMillis()
        log.info("fetching resolution $bodyId took ${after - before} ms")

        return result
    }

    private fun buildContainerName(volume: String): String {
        val builder = StringBuilder("volume-$volume")
        if (revision != null) {
            builder.append('-')
            builder.append(revision)
        }
        val containerName = builder.toString()
        log.info("containerName: $containerName")
        return containerName
    }

    private fun fetchTextLines(textSourceUrl: String): List<String> {
        log.info("Fetching relevant text segments: $textSourceUrl")
        val startTime = System.currentTimeMillis()
        val resp = ClientBuilder.newClient().target(textSourceUrl).request().get()
        val result = resp.readEntity(object : GenericType<List<String>>() {})
        log.info("fetching took ${System.currentTimeMillis() - startTime} ms")
        return result
    }

    private fun fetchOverlappingAnnotations(
        volumeName: String, source: String, start: Int, end: Int
    ): List<Map<String, Any>> {
        val query = mapOf(
            AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE to overlap(source, start, end),
            AR_BODY_TYPE to isNotIn("Line", "Page", "RepublicParagraph", "TextRegion", "Scan")
        )

        val startTime = System.currentTimeMillis()

        val containerName = buildContainerName(volumeName)
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

    override fun findOffsetRelativeTo(volume: String, source: String, selector: TextSelector, type: String)
            : Pair<Int, String> {
        log.info("findOffsetRelativeTo: volume=[$volume], selector=$selector, type=[$type]")

        val query = mapOf(
            AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE to overlap(source, selector.start(), selector.end()),
            AR_BODY_TYPE to isEqualTo(type)
        )

        val anno = annoRepoClient.filterContainerAnnotations(volume, query)
            .getOrHandle { err -> throw BadRequestException("query failed: $err") }
            .annotations.asSequence()
            .map { it.getOrHandle { err -> throw BadRequestException("fetch failed: $err") } }
            .map(jsonParser::parse)
            .map(::WebAnnoPage)
            .firstOrNull()
            ?: throw NotFoundException("overlap not found ($volume,$source,$selector)")

        val start = anno.targetField<Int>("Text", "selector.start")
            .filter { it <= selector.start() }
            .max()

        log.info("closest [$type] starts at $start (absolute)")
        return Pair(start, anno.bodyId().first())
    }

    data class TextSelector(private val context: Map<String, Any>) {
        fun start(): Int = context["start"] as Int
        fun beginCharOffset(): Int? = context["beginCharOffset"] as Int?
        fun end(): Int = context["end"] as Int
        fun endCharOffset(): Int? = context["endCharOffset"] as Int?
    }

}
