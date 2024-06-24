package nl.knaw.huc.broccoli.service.anno

import com.jayway.jsonpath.DocumentContext

class AnnoRepoSearchResult(val context: DocumentContext) {
    fun items(): List<Map<String, Any>> {
        return listOf(context.read("$"))
    }

    fun bodyId(): String = context.read("$.body.id")

    fun bodyType(): String = context.read("$.body.type")

    fun read(path: String): Any? = context.read(path)

    fun <T> target(type: String): List<Map<String, T>> = context.read("$.target[?(@.type == '$type')]")

    fun <T> targetField(type: String, field: String): List<T> =
        context.read<List<T?>>("$.target[?(@.type == '$type')].$field").filterNotNull()

    fun <T> withField(type: String, field: String): List<Map<String, T>> =
        context.read("$.target[?(@.type == '$type')][?(@.$field != null)]")

    fun <T> withoutField(type: String, field: String): List<Map<String, T>> =
        context.read("$.target[?(@.type == '$type')][?(@.$field == null)]")

    fun getSpan(type: String): Pair<Int, Int> =
        withField<Any>(type, "selector")
            .first()
            .let { target ->
                (target["selector"] as Map<*, *>).let { selector ->
                    selector["start"] as Int to selector["end"] as Int
                }
            }
}
