package nl.knaw.huc.broccoli.api

object Constants {
    const val APP_NAME = "Broccoli"

    const val AR_SERVICES = "services"
    const val AR_SEARCH = "search"

    const val AR_BODY_TYPE = "body.type"
    const val AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE = ":overlapsWithTextAnchorRange"
    const val AR_OVERLAP_SOURCE = "source"
    const val AR_OVERLAP_START = "start"
    const val AR_OVERLAP_END = "end"
    fun overlap(source: String, start: Int, end: Int): Map<String, *> =
        mapOf(AR_OVERLAP_SOURCE to source, AR_OVERLAP_START to start, AR_OVERLAP_END to end)

    fun isEqualTo(it: String) = mapOf(":=" to it)
    fun isNotEqualTo(it: String) = mapOf(":!=" to it)
    fun isIn(vararg included: String) = mapOf(":isIn" to included)
    fun isNotIn(vararg excluded: String) = mapOf(":isNotIn" to excluded)

    enum class EnvironmentVariable {
        BR_SERVER_PORT,
        BR_EXTERNAL_URL,
        BR_PAGE_SIZE
    }
}
