package nl.knaw.huc.broccoli.api

object Constants {
    const val APP_NAME = "Broccoli"

    const val AR_BODY_ID = "body.id"
    const val AR_BODY_TYPE = "body.type"

    const val AR_PURPOSE = "purpose"
    const val AR_TAGGING = "tagging"
    const val AR_REFERENCING = "referencing"
    const val AR_LINKING = "linking"

    const val AR_OVERLAP_WITH_TEXT_ANCHOR_RANGE = ":overlapsWithTextAnchorRange"
    const val AR_WITHIN_TEXT_ANCHOR_RANGE = ":isWithinTextAnchorRange"

    fun region(source: String, start: Int, end: Int): Map<String, *> =
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

    const val TEXT_TOKEN_COUNT = "text.tokenCount"

    const val NO_FILTERS = "no_filters"

    const val DOC_COUNT = "doc_count"
}
