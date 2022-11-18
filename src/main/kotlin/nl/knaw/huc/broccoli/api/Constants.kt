package nl.knaw.huc.broccoli.api

object Constants {
    const val APP_NAME = "Broccoli"

    const val AR_BODY_TYPE = "body.type"
    const val AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE = ":overlapsWithTextAnchorRange"

    fun overlap(source: String, start: Int, end: Int): Map<String, *> =
        mapOf("source" to source, "start" to start, "end" to end)

    fun isEqualTo(it: String) = mapOf(":=" to it)
    fun isNotEqualTo(it: String) = mapOf(":!=" to it)
    fun isIn(included: Set<String>) = mapOf(":isIn" to included)
    fun isNotIn(excluded: Set<String>) = mapOf(":isNotIn" to excluded)

    enum class EnvironmentVariable {
        BR_SERVER_PORT,
        BR_EXTERNAL_URL,
        BR_PAGE_SIZE
    }
}
