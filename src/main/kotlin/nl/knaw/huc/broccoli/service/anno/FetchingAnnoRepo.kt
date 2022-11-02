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
import nl.knaw.huc.broccoli.api.TextMarker
import nl.knaw.huc.broccoli.api.WebAnnoPage
import nl.knaw.huc.broccoli.config.AnnoRepoConfiguration
import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import org.slf4j.LoggerFactory
import javax.ws.rs.BadRequestException
import javax.ws.rs.NotAcceptableException
import javax.ws.rs.NotFoundException
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.core.GenericType
import kotlin.streams.asSequence

class FetchingAnnoRepo(
    private val annoRepoClient: AnnoRepoClient,
    private val annoRepoConfig: AnnoRepoConfiguration,
    private val republicConfig: RepublicConfiguration
) : AnnoRepo {
    private val log = LoggerFactory.getLogger(javaClass)

    // choose 'null' over throwing exceptions when json paths cannot be found
    private val jsonParser = JsonPath.using(defaultConfiguration().addOptions(DEFAULT_PATH_LEAF_TO_NULL))

    private val pageStarts = HashMap<Pair<String, Int>, Int>()

    override fun getScanAnno(volume: RepublicVolume, opening: Int): ScanPageResult {
        val before = System.currentTimeMillis()
        val volumeName = buildVolumeName(volume.name)

        val archNr = republicConfig.archiefNr
        val invNr = volume.invNr
        val scanNr = "%04d".format(opening)
        val bodyId = "urn:republic:NL-HaNA_${archNr}_${invNr}_${scanNr}"
        log.info("constructed bodyId: $bodyId")

        val anno = findByBodyId(volume.name, bodyId)
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
                pageStarts[Pair(volumeName, opening)] = start
                annos.addAll(fetchOverlappingAnnotations(volumeName, sourceUrl, start, end))
            }
        }

        val after = System.currentTimeMillis()
        log.info("fetching scan page took ${after - before} ms")

        return ScanPageResult(annos, text)
    }

    override fun findByBodyId(volume: String, bodyId: String): WebAnnoPage {
        log.info("getBodyId: volume=[$volume], bodyId=[$bodyId]")
        val before = System.currentTimeMillis()
        val volumeName = buildVolumeName(volume)

        val query = mapOf("body.id" to bodyId)
        val result = annoRepoClient.filterContainerAnnotations(volumeName, query)
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

    override fun getRepublicBodyId(volume: RepublicVolume, opening: Int, bodyId: String): BodyIdResult {
        val before = System.currentTimeMillis()
        val volumeName = buildVolumeName(volume.name)
        if (pageStarts[Pair(volumeName, opening)] == null) {
            log.info("page starts for ($volumeName, opening) not yet loaded, fetching...")
            getScanAnno(volume, opening)
        }
        val startOfPage = (pageStarts[Pair(volumeName, opening)] ?: throw NotFoundException(bodyId))

        val annoPage = findByBodyId(volume.name, bodyId)
        val textTargets = annoPage.target<Any>("Text")
        log.info("data: $textTargets")
        if (textTargets.size != 2)
            throw NotAcceptableException("unsupported # of target.type == Text elements: ${textTargets.size}")

        val text = getText(textTargets)
        log.info("text: $text")

        val markers = getTextMarkers(textTargets, text)
        log.info("markers: $markers")

        val after = System.currentTimeMillis()
        log.info("fetching bodyId took ${after - before} ms")

        return BodyIdResult(markers.relativeTo(startOfPage), text)
    }

    fun getText(annoTargets: List<Map<String, *>>): List<String> {
        annoTargets.forEach {
            if (it["selector"] == null) {
                return fetchTextLines(it["source"] as String)
            }
        }
        log.info("No text found!")
        return emptyList()
    }

    private fun getTextMarkers(
        annoTargets: List<Map<String, *>>,
        text: List<String>
    ): TextMarkers {
        annoTargets.forEach {
            if (it["selector"] is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                val selector = TextSelector(it["selector"] as Map<String, Any>)

                val beginCharOffset = selector.beginCharOffset() ?: 0
                val start = TextMarker(selector.start(), beginCharOffset, text[0].length)
                log.info("start: $start")

                val lengthOfLastLine = text.last().length
                val endCharOffset = selector.endCharOffset() ?: (lengthOfLastLine - 1)
                val end = TextMarker(selector.end(), endCharOffset, lengthOfLastLine)
                log.info("end: $end")

                return TextMarkers(start, end)
            }
        }

        throw NotFoundException("missing start, end and offset markers")
    }

    data class TextMarkers(val start: TextMarker, val end: TextMarker) {
        fun relativeTo(offset: Int): TextMarkers {
            return TextMarkers(start.relativeTo(offset), end.relativeTo(offset))
        }
    }

    data class TextSelector(private val context: Map<String, Any>) {
        fun start(): Int = context["start"] as Int
        fun beginCharOffset(): Int? = context["beginCharOffset"] as Int?
        fun end(): Int = context["end"] as Int
        fun endCharOffset(): Int? = context["endCharOffset"] as Int?
    }

    private fun buildVolumeName(volume: String): String {
        val volumeNameBuilder = StringBuilder("volume-$volume")
        if (annoRepoConfig.rev != null) {
            volumeNameBuilder.append('-')
            volumeNameBuilder.append(annoRepoConfig.rev)
        }
        val volumeName = volumeNameBuilder.toString()
        log.info("volumeName: $volumeName")
        return volumeName
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

        val overlappingAnnotations = annoRepoClient.filterContainerAnnotations(volumeName, query)
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
}
