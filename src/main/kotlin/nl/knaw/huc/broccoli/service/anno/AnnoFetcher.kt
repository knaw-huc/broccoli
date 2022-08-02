package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import org.slf4j.LoggerFactory
import javax.ws.rs.client.ClientBuilder
import javax.ws.rs.client.Entity

class AnnoFetcher(
    private val annoRepoURI: String, private val conf: RepublicConfiguration
) : AnnoRepo {
    private val log = LoggerFactory.getLogger(javaClass)

    // replace with AnnoRepoClient later on
    private val client = ClientBuilder.newClient()

    override fun getScanAnno(volume: RepublicVolume, opening: Int): Map<String, Any> {
        val webTarget = client.target(annoRepoURI).path("search").path("volume-${volume.name}").path("annotations")
        log.info("path: ${webTarget.uri}")

        val archNr = conf.archiefNr
        val invNr = volume.invNr
        val scanNr = "%04d".format(opening)
        val bodyId = "urn:republic:NL-HaNA_${archNr}_${invNr}_${scanNr}"
        log.info("constructed bodyId: $bodyId")

        val response = webTarget.request().post(
            Entity.json(
                mapOf("body.id" to bodyId)
            )
        )
        log.info("code: ${response.status}")

        @Suppress("UNCHECKED_CAST") val body = response.readEntity(Map::class.java) as Map<String, Any>
        log.info("body: $body")
        return body
    }
}