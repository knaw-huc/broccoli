package nl.knaw.huc.broccoli.resources.brinta

import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class BrintaResourceTest {

    @Test
    fun `a subsegment in a single anchor segment should work`() {
        val textLines = listOf("Lorem ipsum dolor amet")
        val textUrl = "https://blablabla/segments/index/0/6/0/10"
        val textSegments = BrintaResource.fetchTextSegmentsLocal(textLines, textUrl)
        assertThat(textSegments).containsExactly("ipsum")
    }

    @Test
    fun `a subsegment in a multiple anchor segments should work`() {
        val textLines = listOf("Lorem ipsum dolor amet.", "Eligendi aut nihil voluptas temporibus.")
        val textUrl = "https://blablabla/segments/index/0/6/1/7"
        val textSegments = BrintaResource.fetchTextSegmentsLocal(textLines, textUrl)
        assertThat(textSegments).containsExactly("ipsum dolor amet.", "Eligendi")
    }
}