package nl.knaw.huc.broccoli.api

import com.jayway.jsonpath.DocumentContext

class WebAnnoPage(val context: DocumentContext) {
    fun items(): List<Map<String, Any>> {
        return context.read("$.items")
    }

    fun <T> target(type: String): List<Map<String, T>> {
        return context.read("$.items[*].target[?(@.type == '$type')]")
    }

    fun <T> targetField(type: String, field: String): List<T> {
        return context.read("$.items[*].target[?(@.type == '$type')].$field")
    }
}