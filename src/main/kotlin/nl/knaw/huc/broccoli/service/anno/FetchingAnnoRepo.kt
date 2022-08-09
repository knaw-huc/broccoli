package nl.knaw.huc.broccoli.service.anno

import com.jayway.jsonpath.Configuration.defaultConfiguration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL
import nl.knaw.huc.broccoli.api.TextMarker
import nl.knaw.huc.broccoli.config.AnnoRepoConfiguration
import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import org.slf4j.LoggerFactory
import javax.ws.rs.NotAcceptableException
import javax.ws.rs.NotFoundException
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity.json
import javax.ws.rs.core.GenericType

class AnnoFetcher(
    private val annoRepoConfig: AnnoRepoConfiguration,
    private val republicConfig: RepublicConfiguration
) : AnnoRepo {
    private val log = LoggerFactory.getLogger(javaClass)

    // replace with AnnoRepoClient later on
    private val client = ClientBuilder.newClient()

    // choose 'null' over throwing exceptions when json paths cannot be found
    private val jsonParser = JsonPath.using(defaultConfiguration().addOptions(DEFAULT_PATH_LEAF_TO_NULL))

    private val cachedPageStarts = HashMap<Pair<String, Int>, Int>()

    override fun getScanAnno(volume: RepublicVolume, opening: Int): ScanPageResult {
        val volumeName = buildVolumeName(volume)
        val webTarget = client.target(annoRepoConfig.uri).path("search").path(volumeName).path("annotations")
        log.info("path: ${webTarget.uri}")

        val archNr = republicConfig.archiefNr
        val invNr = volume.invNr
        val scanNr = "%04d".format(opening)
        val bodyId = "urn:republic:NL-HaNA_${archNr}_${invNr}_${scanNr}"
        log.info("constructed bodyId: $bodyId")

        val response = webTarget.request().post(json(mapOf("body.id" to bodyId)))
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
                cachedPageStarts[Pair(volumeName, opening)] = start
                annos.addAll(fetchOverlappingAnnotations(volumeName, sourceUrl, start, end))
            }
        }

        return ScanPageResult(annos, text)
    }

    override fun getBodyId(volume: RepublicVolume, opening: Int, bodyId: String): BodyIdResult {
        val volumeName = buildVolumeName(volume)
        val startOfPage = (cachedPageStarts[Pair(volumeName, opening)] ?: throw NotFoundException(bodyId))

        val webTarget = client.target(annoRepoConfig.uri).path("search").path(volumeName).path("annotations")
        log.info("path: ${webTarget.uri}")
        val response = webTarget.request().post(json(mapOf("body.id" to bodyId)))
        log.info("code: ${response.status}")

        val body = response.readEntity(String::class.java)
        val json = jsonParser.parse(body)
        val data = json.read<List<Map<String, *>>>("$.items[0].target[?(@.type == 'Text')]")
        log.info("data: $data")
        if (data.size != 2) throw NotAcceptableException("unsupported # of target.type == Text elements: ${data.size}")

        val text = getText(data)
        log.info("text: $text")

        val markers = getTextMarkers(data, startOfPage, text)
            ?: throw NotFoundException("missing start, end and offset markers")
        log.info("markers: $markers")

        return BodyIdResult(markers.first, markers.second, text)
    }

    private fun getText(annoTargets: List<Map<String, *>>): List<String> {
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
    ): Pair<TextMarker, TextMarker>? {
        annoTargets.forEach {
            if (it["selector"] != null) {

                @Suppress("UNCHECKED_CAST")
                val selector = it["selector"] as Map<String, Any>

                val absoluteStart = selector["start"] as Int
                val relativeStart = absoluteStart - startOfPage
                val beginCharOffset = selector["beginCharOffset"] as Int?
                val start = TextMarker(relativeStart, beginCharOffset ?: 0, text[0].length)
                log.info("start: $start")

                val absoluteEnd = selector["end"] as Int
                val relativeEnd = absoluteEnd - startOfPage
                val endCharOffset = selector["endCharOffset"] as Int?
                val index = text.size - 1
                val end = TextMarker(relativeEnd, endCharOffset ?: (text[index].length - 1), text[index].length)
                log.info("end: $end")

                return Pair(start, end)
            }
        }
        return null
    }

    private fun buildVolumeName(volume: RepublicVolume): String {
        val volumeNameBuilder = StringBuilder("volume-${volume.name}")
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
        val resp = client.target(textSourceUrl).request().get()
        return resp.readEntity(object : GenericType<List<String>>() {})
    }

    private fun fetchOverlappingAnnotations(
        volumeName: String, source: String, start: Int, end: Int
    ): List<Map<String, Any>> {
        // Intent: only collect annotations where body.type is *NOT* one of: (Line,Page,TextRegion,Scan)
        //        (regex credits go to: https://regexland.com/regex-match-all-except/)
        // this can be removed when AnnoRepo supports this as part of the query language
        val requiredAnnotationsPath =
            "$.items[?(@.body.type =~ /^(?!.*(Line?|Page?|RepublicParagraph?|TextRegion?|Scan?)).*/)]"

        // initial request without page parameter
        var webTarget = client.target(annoRepoConfig.uri)
            .path("search").path(volumeName).path("overlapping_with_range")
            .queryParam("target.source", source)
            .queryParam("range.start", start)
            .queryParam("range.end", end)

        val result = ArrayList<Map<String, Any>>()
        while (result.count() < 1000) { // some arbitrary cap for now
            log.info("Fetching overlapping annotations page: ${webTarget.uri}")
            val resp = webTarget.request().get()
            val annoBody = resp.readEntity(String::class.java)
            val annoJson = jsonParser.parse(annoBody)
            result.addAll(annoJson.read<List<Map<String, Any>>>(requiredAnnotationsPath))

            // Loop on with request for next page using provided 'next page url'.
            val nextPageUrl = annoJson.read<String>("$.next") ?: break // If '.next' is absent, we are done
            webTarget = client.target(nextPageUrl)
        }

        return result
    }
}
