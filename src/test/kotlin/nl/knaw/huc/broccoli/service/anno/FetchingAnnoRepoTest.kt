package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.config.AnnoRepoConfiguration
import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.URI

class FetchingAnnoRepoTest {
    private val log = LoggerFactory.getLogger(javaClass)

    private var annoRepoConfig = AnnoRepoConfiguration()
    private var republicConfig = RepublicConfiguration()
    private var annoRepoClient: AnnoRepoClient

    private var sut: FetchingAnnoRepo

    init {
        annoRepoConfig.uri = "https://annorepo.republic-caf.diginfra.org"
        annoRepoConfig.rev = "7"
        annoRepoClient = AnnoRepoClient(URI.create(annoRepoConfig.uri))

        republicConfig.archiefNr = "1.01.02"

        annoRepoClient = AnnoRepoClient(URI.create(annoRepoConfig.uri))
        sut = FetchingAnnoRepo(annoRepoClient, annoRepoConfig, republicConfig)
    }

    @Test
    fun `Anno fetcher should fetch bodyId`() {
        sut.findByBodyId(volume = "1728", bodyId = "urn:republic:session-1728-06-19-ordinaris-num-1-resolution-16")
    }

    @Test
    fun `Anno fetcher should call out to remote annotation repository`() {
        val volume = RepublicVolume()
        volume.name = "1728"
        volume.invNr = "3783"

        val scanPageResult = sut.getScanAnno(volume, 285)
        log.info("scanPageResult: ${scanPageResult.anno.size}")
    }
}