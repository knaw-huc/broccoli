package nl.knaw.huc.broccoli.api

import com.jayway.jsonpath.DocumentContext

class WebAnnoPage(val context: DocumentContext) {
    fun items(): List<Map<String, Any>> {
        return context.read("$.items")
    }

    fun bodyId(): List<String> = context.read("$.items[*].body.id")

    fun <T> target(type: String): List<Map<String, T>> = context.read("$.items[*].target[?(@.type == '$type')]")

    fun <T> targetField(type: String, field: String): List<T> =
        context.read<List<T?>>("$.items[*].target[?(@.type == '$type')].$field").filterNotNull()
}