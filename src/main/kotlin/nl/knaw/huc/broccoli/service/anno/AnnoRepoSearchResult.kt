package nl.knaw.huc.broccoli.service.anno

import com.jayway.jsonpath.DocumentContext

class AnnoRepoSearchResult(val context: DocumentContext) {
    fun items(): List<Map<String, Any>> {
        return listOf(context.read("$"))
    }

    fun bodyId(): String = context.read("$.body.id")

    fun bodyType(): String = context.read("$.body.type")

    fun bodyMetadata(): Map<String, Any> = context.read("$.body.metadata")

    fun <T> target(type: String): List<Map<String, T>> = context.read("$.target[?(@.type == '$type')]")

    fun <T> targetField(type: String, field: String): List<T> =
        context.read<List<T?>>("$.target[?(@.type == '$type')].$field").filterNotNull()

    fun <T> withField(type: String, field: String): List<Map<String, T>> =
        context.read("$.target[?(@.type == '$type')][?(@.$field != null)]")

    fun <T> withoutField(type: String, field: String): List<Map<String, T>> =
        context.read("$.target[?(@.type == '$type')][?(@.$field == null)]")
}
