package nl.knaw.huc.broccoli.service.anno

import com.jayway.jsonpath.Configuration.defaultConfiguration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL
import nl.knaw.huc.broccoli.api.Constants.AR_BODY_TYPE
import nl.knaw.huc.broccoli.api.Constants.AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE
import nl.knaw.huc.broccoli.api.Constants.AR_SEARCH
import nl.knaw.huc.broccoli.api.Constants.AR_SERVICES
import nl.knaw.huc.broccoli.api.Constants.isNotIn
import nl.knaw.huc.broccoli.api.Constants.overlap
import nl.knaw.huc.broccoli.api.TextMarker
import nl.knaw.huc.broccoli.api.WebAnnoPage
import nl.knaw.huc.broccoli.config.AnnoRepoConfiguration
import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import org.eclipse.jetty.http.HttpStatus
import org.slf4j.LoggerFactory
import javax.ws.rs.NotAcceptableException
import javax.ws.rs.NotFoundException
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity.json
import javax.ws.rs.core.GenericType
import javax.ws.rs.core.HttpHeaders

class FetchingAnnoRepo(
    private val annoRepoConfig: AnnoRepoConfiguration,
    private val republicConfig: RepublicConfiguration
) : AnnoRepo {
    private val log = LoggerFactory.getLogger(javaClass)

    // replace with AnnoRepoClient later on
    private val client = ClientBuilder.newClient()

    // choose 'null' over throwing exceptions when json paths cannot be found
    private val jsonParser = JsonPath.using(defaultConfiguration().addOptions(DEFAULT_PATH_LEAF_TO_NULL))

    private val pageStarts = HashMap<Pair<String, Int>, Int>()

    override fun getScanAnno(volume: RepublicVolume, opening: Int): ScanPageResult {
        val before = System.currentTimeMillis()
        val volumeName = buildVolumeName(volume.name)
        val webTarget = client.target(annoRepoConfig.uri).path(AR_SERVICES).path(volumeName).path(AR_SEARCH)
        log.info("path: ${webTarget.uri}")

        val archNr = republicConfig.archiefNr
        val invNr = volume.invNr
        val scanNr = "%04d".format(opening)
        val bodyId = "urn:republic:NL-HaNA_${archNr}_${invNr}_${scanNr}"
        log.info("constructed bodyId: $bodyId")

        val queryResponse = webTarget.request().post(json(mapOf("body.id" to bodyId)))
        log.info("code: ${queryResponse.status}")

        val resultLocation = queryResponse.getHeaderString(HttpHeaders.LOCATION)
        log.info("query created: $resultLocation")

        val queryTarget = client.target(resultLocation)
        val response = queryTarget.request().get()
        log.info("code: ${response.status}")

        val body = response.readEntity(String::class.java)
        val json = jsonParser.parse(body)
        val data = json.read<List<Map<String, *>>>("$.items[0].target[?(@.type == 'Text')]")
        log.info("data: $data")

        val text = ArrayList<String>()
        val annos = ArrayList<Map<String, Any>>()
        data.forEach {
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

    override fun getBodyId(volume: String, bodyId: String): WebAnnoPage {
        val before = System.currentTimeMillis()
        val volumeName = buildVolumeName(volume)

        val webTarget = client.target(annoRepoConfig.uri).path(AR_SERVICES).path(volumeName).path(AR_SEARCH)

        val queryResponse = webTarget.request().post(json(mapOf("body.id" to bodyId)))
        log.info("code: ${queryResponse.status}")

        val resultLocation = queryResponse.getHeaderString(HttpHeaders.LOCATION)
        log.info("location header: $resultLocation")

        val queryTarget = client.target(resultLocation)
        val response = queryTarget.request().get()
        log.info("code: ${response.status}")

        val body = response.readEntity(String::class.java)
        val result = WebAnnoPage(jsonParser.parse(body))

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

        val annoPage = getBodyId(volume.name, bodyId)
        val textTargets = annoPage.target<Any>("Text")
        log.info("data: $textTargets")
        if (textTargets.size != 2)
            throw NotAcceptableException("unsupported # of target.type == Text elements: ${textTargets.size}")

        val text = getText(textTargets)
        log.info("text: $text")

        val markers = getTextMarkers(textTargets, startOfPage, text)
        log.info("markers: $markers")

        val after = System.currentTimeMillis()
        log.info("fetching bodyId took ${after - before} ms")

        return BodyIdResult(markers.first, markers.second, text)
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
        startOfPage: Int,
        text: List<String>
    ): Pair<TextMarker, TextMarker> {
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

                return Pair(start.relativeTo(startOfPage), end.relativeTo(startOfPage))
            }
        }

        throw NotFoundException("missing start, end and offset markers")
    }

    class TextSelector(private val context: Map<String, Any>) {
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
        val resp = client.target(textSourceUrl).request().get()
        val result = resp.readEntity(object : GenericType<List<String>>() {})
        log.info("fetching took ${System.currentTimeMillis() - startTime} ms")
        return result
    }

    private fun fetchOverlappingAnnotations(
        volumeName: String, source: String, start: Int, end: Int
    ): List<Map<String, Any>> {
        val startTime = System.currentTimeMillis()
        // initial request without page parameter
        var webTarget = client.target(annoRepoConfig.uri).path(AR_SERVICES).path(volumeName).path(AR_SEARCH)
        log.info("webTarget: $webTarget")

        val queryResponse = webTarget.request()
            .post(
                json(
                    mapOf(
                        AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE to overlap(source, start, end),
                        AR_BODY_TYPE to isNotIn("Line", "Page", "RepublicParagraph", "TextRegion", "Scan")
                    )
                )
            )
        log.info("code: ${queryResponse.status}")
        if (queryResponse.status == HttpStatus.BAD_REQUEST_400) {
            log.info("BAD REQUEST: ${queryResponse.readEntity(String::class.java)}")
        }

        val resultLocation = queryResponse.getHeaderString(HttpHeaders.LOCATION)
        log.info("query created: $resultLocation")

        webTarget = client.target(resultLocation)
        val result = ArrayList<Map<String, Any>>()
        while (result.count() < 1000) { // some arbitrary cap for now
            log.info("Fetching overlapping annotations page: ${webTarget.uri}")
            val resp = webTarget.request().get()
            val annoBody = resp.readEntity(String::class.java)
            val annoJson = jsonParser.parse(annoBody)
            result.addAll(annoJson.read<List<Map<String, Any>>>("$.items"))

            // Loop on with request for next page using provided 'next page url'.
            val nextPageUrl = annoJson.read<String>("$.next") ?: break // If '.next' is absent, we are done
            webTarget = client.target(nextPageUrl)
        }

        log.info("fetching overlapping annotations took: ${System.currentTimeMillis() - startTime} ms")
        return result
    }
}
