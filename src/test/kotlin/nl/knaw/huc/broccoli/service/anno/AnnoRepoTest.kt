package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.annorepo.client.AnnoRepoClient
import org.junit.jupiter.api.Test
import java.net.URI
import kotlin.test.assertEquals

class AnnoRepoTest {
//    private val log = LoggerFactory.getLogger(javaClass)

    private var annoRepoClient: AnnoRepoClient

    private var sut: AnnoRepo

    init {
        annoRepoClient = AnnoRepoClient(serverURI = URI.create("https://annorepo.republic-caf.diginfra.org"))
        sut = AnnoRepo(annoRepoClient, "volume-1728-7", "Text")
    }

    @Test
    fun `Anno fetcher should fetch bodyId`() {
        sut.findByBodyId(
            bodyId = "urn:republic:session-1728-06-19-ordinaris-num-1-resolution-16",
        )
    }

    @Test
    fun `Anno fetcher should call out to remote annotation repository`() {
        val archNr = ""
        val invNr = "3783"
        val scanNr = "%04d".format(285)
        val bodyId = "urn:republic:NL-HaNA_${archNr}_${invNr}_${scanNr}"

//        val containerName = volumeMapper.buildContainerName("1728")
//        val scanPageResult = sut.getScanAnno(containerName, bodyId = bodyId)
//        log.info("scanPageResult.size: ${scanPageResult.anno.size}")
    }

    @Test
    fun `Received bodyId should equal search key`() {
        val expectedBodyId = "urn:republic:NL-HaNA_1.01.02_3783_0331"
        val result = sut.findByBodyId(expectedBodyId)
        assertEquals(expectedBodyId, result.bodyId())
    }
}
