package nl.knaw.huc.broccoli.service.anno

import com.jayway.jsonpath.Configuration.defaultConfiguration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL
import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import org.slf4j.LoggerFactory
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity.json
import javax.ws.rs.core.GenericType

class AnnoFetcher(
    private val annoRepoURI: String, private val conf: RepublicConfiguration
) : AnnoRepo {
    private val log = LoggerFactory.getLogger(javaClass)

    // replace with AnnoRepoClient later on
    private val client = ClientBuilder.newClient()

    override fun getScanAnno(volume: RepublicVolume, opening: Int): Map<String, Any> {
        val jsonParser = JsonPath.using(defaultConfiguration().addOptions(DEFAULT_PATH_LEAF_TO_NULL))

        val volumeName = "volume-${volume.name}"
        val webTarget = client.target(annoRepoURI)
            .path("search")
            .path(volumeName)
            .path("annotations")
        log.info("path: ${webTarget.uri}")

        val archNr = conf.archiefNr
        val invNr = volume.invNr
        val scanNr = "%04d".format(opening)
        val bodyId = "urn:republic:NL-HaNA_${archNr}_${invNr}_${scanNr}"
        log.info("constructed bodyId: $bodyId")

        val response = webTarget.request().post(json(mapOf("body.id" to bodyId)))
        log.info("code: ${response.status}")

        val body = response.readEntity(String::class.java)
        val json = jsonParser.parse(body)
        val data = json.read<List<Map<String, *>>>("$.items[0].target")
        log.info("data: $data")
        val text = ArrayList<String>()
        val annos = ArrayList<Map<String, Any>>()
        data.filter { it["type"] == "Text" }
            .forEach {
                if (it["selector"] != null) {
                    @Suppress("UNCHECKED_CAST")
                    val selector = it["selector"] as Map<String, Any>
                    val start = selector["start"] as Int
                    val end = selector["end"] as Int

                    // initial request
                    var request = client.target(annoRepoURI)
                        .path("search")
                        .path(volumeName)
                        .path("overlapping_with_range")
                        .queryParam("target.source", it["source"] as String)
                        .queryParam("range.start", start)
                        .queryParam("range.end", end)
                    log.info("Initial anno URI: ${request.uri}")

                    while (annos.count() < 1000) { // some arbitrary cap for now
                        val resp = request.request().get()
                        val annoBody = resp.readEntity(String::class.java)
                        val annoJson = jsonParser.parse(annoBody)
                        annos.addAll(
                            // Thanks https://regexland.com/regex-match-all-except/
                            // Can be removed when AnnoRepo supports this as part of the query
                            annoJson.read<List<Map<String, Any>>>("$.items[?(@.body.type =~ /^(?!.*(Line?|Page?|TextRegion?|Scan?)).*/)]")
                        )

                        // subsequent requests for next page using provided url
                        request = client.target(annoJson.read<String>("$.next") ?: break)
                    }
                } else {
                    val resp = client.target(it["source"] as String).request().get()
                    text.addAll(resp.readEntity(object : GenericType<List<String>>() {}))
                }
            }
        for ((i, anno) in annos.withIndex()) {
            @Suppress("UNCHECKED_CAST") val b = anno["body"] as Map<String, Any>
            log.info("anno[$i].body.id = ${b["id"]}")
        }
        return mapOf(
            "anno" to annos,
            "text" to text
        )
    }
}