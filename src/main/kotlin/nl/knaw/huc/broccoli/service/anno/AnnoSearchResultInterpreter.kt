package nl.knaw.huc.broccoli.service.anno

import jakarta.ws.rs.NotFoundException
import org.slf4j.LoggerFactory

class AnnoSearchResultInterpreter(
    private val searchResult: AnnoRepoSearchResult,
    private val textType: String
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun bodyId() = searchResult.bodyId()

    fun bodyType() = searchResult.bodyType()

    fun findSegmentsSource(): String = findTextTargetWithSelector()["source"] as String

    fun findTextSource(): String = findTextTargetWithoutSelector()["source"] as String

    @Suppress("UNCHECKED_CAST")
    fun findSelector(): TextSelector = TextSelector(findTextTargetWithSelector()["selector"] as Map<String, Any>)

    private fun findTextTargetWithSelector(): Map<String, Any> {
        val withSelectorTargets = searchResult.withField<Any>(textType, "selector")
        return when {
            withSelectorTargets.isEmpty() -> throw NotFoundException("no text target with 'selector' found")
            withSelectorTargets.size == 1 -> withSelectorTargets[0]
            else -> {
                log.warn("multiple 'Text' targets with selector, arbitrarily picking the first")
                withSelectorTargets[0]
            }
        }
    }

    private fun findTextTargetWithoutSelector(): Map<String, Any> {
        val withoutSelectorTargets = searchResult.withoutField<String>(textType, "selector")
        return when {
            withoutSelectorTargets.isEmpty() -> throw NotFoundException("no text targets without 'selector' found")
            withoutSelectorTargets.size == 1 -> withoutSelectorTargets[0]
            else -> {
                log.warn("multiple 'Text' targets without selector, arbitrarily picking the first")
                withoutSelectorTargets[0]
            }
        }
    }
}
