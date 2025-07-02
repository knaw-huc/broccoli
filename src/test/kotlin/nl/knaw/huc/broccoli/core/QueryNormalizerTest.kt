package nl.knaw.huc.broccoli.core

import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import jakarta.ws.rs.BadRequestException
import nl.knaw.huc.broccoli.api.IndexQuery
import nl.knaw.huc.broccoli.config.IndexConfiguration
import nl.knaw.huc.broccoli.config.IndexFieldConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class QueryNormalizerTest {

    @RelaxedMockK
    lateinit var config: IndexConfiguration

    @Test
    fun `test ES_FIELD_PREFIX is kept as is`() {
        val sut = createQueryNormalizer()

        val query = mockk<IndexQuery>(relaxed = true)
        val expected = "invNr:"
        every { query.text } returns expected

        val result = sut.normalizeQuery(query)
        assertThat(result.text).isEqualTo(expected)
    }

    @Test
    fun `text strings are prefixed by the text view`() {
        val sut = createQueryNormalizer()

        val query = mockk<IndexQuery>(relaxed = true)
        every { query.text } returns "paard"
        every { query.textViews } returns listOf("a")

        val result = sut.normalizeQuery(query)
        assertThat(result.text).isEqualTo("a:paard")
    }

    @Test
    fun `text views are joined by ' OR ' and view names are distributed`() {
        val sut = createQueryNormalizer()

        val query = mockk<IndexQuery>(relaxed = true)
        every { query.text } returns "paard"
        every { query.textViews } returns listOf("a", "c", "e")

        val result = sut.normalizeQuery(query)
        assertThat(result.text).isNotNull()

        val parts = result.text!!.split(" OR ")
        assertThat(parts.size).isEqualTo(3)
        assertThat(parts[0]).startsWith("a:")
        assertThat(parts[1]).startsWith("c:")
        assertThat(parts[2]).startsWith("e:")
        assertThat(parts).allSatisfy { assertThat(it).endsWith("paard") }
    }

    @Test
    fun `unknown text views throw BadRequest with explanation and available views`() {
        val sut = createQueryNormalizer()

        val query = mockk<IndexQuery>(relaxed = true)
        every { query.textViews } returns listOf("a", "x", "y")

        val exception = assertThrows<BadRequestException> {
            sut.normalizeQuery(query)
        }

        assertThat(exception).hasMessageContainingAll("Unknown", "x", "y", "available", "a, b, c, d, e")
    }

    private fun createQueryNormalizer(): QueryNormalizer {
        every { config.fields } returns mutableListOf(
            IndexFieldConfiguration().apply { name = "a"; type = "text" },
            IndexFieldConfiguration().apply { name = "b"; type = "text" },
            IndexFieldConfiguration().apply { name = "c"; type = "text" },
            IndexFieldConfiguration().apply { name = "d"; type = "text" },
            IndexFieldConfiguration().apply { name = "e"; type = "text" },
        )
        return QueryNormalizer(config)
    }
}
