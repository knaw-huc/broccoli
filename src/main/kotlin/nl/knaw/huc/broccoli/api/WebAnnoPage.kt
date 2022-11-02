package nl.knaw.huc.broccoli.api

import com.jayway.jsonpath.DocumentContext

class WebAnnoPage(val context: DocumentContext) {
    fun items(): List<Map<String, Any>> {
        return listOf(context.read("$"))
    }

    fun bodyId(): List<String> = context.read("$.body.id")

    fun <T> target(type: String): List<Map<String, T>> = context.read("$.target[?(@.type == '$type')]")

    fun <T> targetField(type: String, field: String): List<T> =
        context.read<List<T?>>("$.target[?(@.type == '$type')].$field").filterNotNull()
}