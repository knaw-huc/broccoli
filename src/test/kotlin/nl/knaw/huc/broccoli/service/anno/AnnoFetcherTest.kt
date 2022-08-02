package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.config.RepublicVolume
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory

class AnnoFetcherTest {
    private val log = LoggerFactory.getLogger(javaClass)

    /*
    companion object {
        val DW = DropwizardAppExtension(
            BroccoliApplication::class.java,
            ResourceHelpers.resourceFilePath("config.yml")
        )
    }
    */
    private val conf = RepublicConfiguration().apply {
        archiefNr = "1.01.02"
    }

    private val sut = AnnoFetcher("https://annorepo.republic-caf.diginfra.org", conf)

    @Test
    fun `Anno fetcher should call out to remote annotation repository`() {
        val volume = RepublicVolume().apply {
            name = "1728"
            invNr = "3783"
        }
        val anno = sut.getScanAnno(volume, 285)
        log.info("anno: $anno")
    }
}