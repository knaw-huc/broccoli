package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.annorepo.client.AnnoRepoClient
import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import nl.knaw.huc.broccoli.resources.RepublicVolumeMapper
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.net.URI

class FetchingAnnoRepoTest {
    private val log = LoggerFactory.getLogger(javaClass)

    private var republicConfig = RepublicConfiguration()
    private var annoRepoClient: AnnoRepoClient
    private var volumeMapper: RepublicVolumeMapper

    private var sut: FetchingAnnoRepo

    init {
        republicConfig.archiefNr = "1.01.02"
        volumeMapper = RepublicVolumeMapper(republicConfig, revision = "7")

        annoRepoClient = AnnoRepoClient(serverURI = URI.create("https://annorepo.republic-caf.diginfra.org"))
        sut = FetchingAnnoRepo(annoRepoClient)
    }

    @Test
    fun `Anno fetcher should fetch bodyId`() {
        val containerName = volumeMapper.buildContainerName("1728")
        sut.findByBodyId(
            bodyId = "urn:republic:session-1728-06-19-ordinaris-num-1-resolution-16",
            containerName = containerName
        )
    }

    @Test
    fun `Anno fetcher should call out to remote annotation repository`() {
        val volume = RepublicVolume()
        volume.name = "1728"
        volume.invNr = "3783"

        val archNr = republicConfig.archiefNr
        val invNr = volume.invNr
        val scanNr = "%04d".format(285)
        val bodyId = "urn:republic:NL-HaNA_${archNr}_${invNr}_${scanNr}"

        val containerName = volumeMapper.buildContainerName(volume.name)
        val scanPageResult = sut.getScanAnno(containerName, bodyId = bodyId)
        log.info("scanPageResult.size: ${scanPageResult.anno.size}")
    }
}