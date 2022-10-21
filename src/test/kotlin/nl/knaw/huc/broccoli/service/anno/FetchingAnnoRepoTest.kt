package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.config.AnnoRepoConfiguration
import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class FetchingAnnoRepoTest {
    private val log = LoggerFactory.getLogger(javaClass)

    private val annoRepoConfig = AnnoRepoConfiguration()
    private val republicConfig = RepublicConfiguration()

    private val sut = FetchingAnnoRepo(annoRepoConfig, republicConfig)

    @Test
    fun `Anno fetcher should call out to remote annotation repository`() {
        annoRepoConfig.uri = "https://annorepo.republic-caf.diginfra.org"
        annoRepoConfig.rev = "7"

        republicConfig.archiefNr = "1.01.02"

        val volume = RepublicVolume()
        volume.name = "1728"
        volume.invNr = "3783"

        val scanPageResult = sut.getScanAnno(volume, 285)
        log.info("scanPageResult: ${scanPageResult.anno.size}")
    }
}