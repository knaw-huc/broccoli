package nl.knaw.huc.broccoli.service.anno

import com.jayway.jsonpath.DocumentContext
import nl.knaw.huc.broccoli.service.getValueAtPath

class AnnoRepoSearchResult(val context: DocumentContext) {
    fun root(): Map<String, Any> = context.read("$")

    fun items(): List<Map<String, Any>> {
        return listOf(root())
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

    fun liesWithin(region: IntRange) =
        target<Any>("Text")
            .any { target ->
                getValueAtPath<Int>(target, "selector.start")?.let { start ->
                    getValueAtPath<Int>(target, "selector.end")?.let { end ->
                        start >= region.first && end <= region.last
                    } ?: false
                } ?: false
            }

    fun satisfies(constraints: Map<String, String>) =
        constraints.all { (path, value) -> read("$.$path") == value }
}
