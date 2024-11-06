package nl.knaw.huc.broccoli.service

import com.jayway.jsonpath.ReadContext
import nl.knaw.huc.broccoli.api.Constants.DOC_COUNT
import nl.knaw.huc.broccoli.api.Constants.NO_FILTERS
import nl.knaw.huc.broccoli.config.IndexConfiguration

// migrate to ES specific 'util'
fun extractAggregations(index: IndexConfiguration, context: ReadContext) =
    context.read<Map<String, Any>>("$.aggregations")
        ?.mapNotNull { aggregation ->
            @Suppress("UNCHECKED_CAST")
            val aggValuesMap = aggregation.value as Map<String, Any>
            if ("buckets" in aggValuesMap) {
                @Suppress("UNCHECKED_CAST")
                val buckets = aggValuesMap["buckets"] as List<Map<String, Any>>
                if (buckets.isEmpty())
                    null
                else {
                    mapOf(aggregation.key to buckets.associate {
                        (it["key_as_string"] ?: it["key"]) to it[DOC_COUNT]
                    })
                }
            } else if ("nested" in aggValuesMap) {
                mapOf(
                    aggregation.key to mutableListOf<Map<String, Any>>().apply {
                        aggValuesMap
                            .filter { it.key != DOC_COUNT }
                            .forEach { (nestedFacetName: String, nestedFacetValues: Any) ->
                                val nestedAggValuesMap = nestedFacetValues as Map<*, *>
                                if ("buckets" in nestedAggValuesMap) {
                                    @Suppress("UNCHECKED_CAST")
                                    val nestedBuckets = nestedAggValuesMap["buckets"] as List<Map<String, Any>>
                                    if (nestedBuckets.isNotEmpty()) {
                                        add(
                                            mapOf(nestedFacetName to nestedBuckets.associate {
                                                (it["key_as_string"] ?: it["key"]) to
                                                        (it["documents"] as Map<*, *>)[DOC_COUNT]
                                            })
                                        )
                                    }
                                }
                            }
                    }.groupByKey()
                )
            } else if ("filter" in aggValuesMap) {
                val filterBuckets: Map<String, Any> = getValueAtPath(aggValuesMap, "filter.buckets")
                    ?: return@mapNotNull null // no yield here after all, perhaps throw Exception?
                mutableListOf<Map<String, Any>>().apply {
                    filterBuckets.forEach { (key, vals) ->
                        @Suppress("UNCHECKED_CAST")
                        (vals as Map<String, Map<String, Any>>)
                            .filterNot { it.key == DOC_COUNT }
                            .forEach { (nameAndSort, logicalAggValuesMap) ->
                                val name = nameAndSort.substringBefore('|')
                                val order = nameAndSort.substringAfter('|')
                                System.err.println("name=$name, order=$order")
                                val prefix = if (key == NO_FILTERS) null else key
                                findLogicalFacetName(index, name, prefix)?.let { logicalFacetName ->
                                    System.err.println(" --> logicalFacetName=$logicalFacetName")
                                    val buckets = logicalAggValuesMap["buckets"] as List<Map<String, Any>>
                                    if (buckets.isNotEmpty()) {
                                        add(
                                            mapOf("${logicalFacetName}@$order" to buckets.associate {
                                                (it["key_as_string"]
                                                    ?: it["key"]) to (it["documents"] as Map<*, *>)[DOC_COUNT]
                                            })
                                        )
                                    }
                                }
                            }
                    }
                }.groupByKey()
            } else null
        }
        ?.groupByKey()

fun findLogicalFacetName(index: IndexConfiguration, path: String, prefix: String?): String? {
    System.err.println("findLogicalFacetName: path=$path, prefix=$prefix")
    return index.fields.find { field ->
        field.logical?.path == path
                && prefix?.let { field.name.startsWith(it) } ?: true
    }?.name
}

inline fun <reified V> getValueAtPath(anno: Map<*, *>, path: String): V? {
    val steps = path.split('.').iterator()

    var cur: Any = anno
    while (cur is Map<*, *> && steps.hasNext()) {
        cur = cur[steps.next()] ?: return null
    }

    if (steps.hasNext()) {
        return null
    }

    if (cur is V) {
        return cur
    }

    return null
}

/*
 * Find common prefix in a list of Strings
 * e.g. ["roleLabel", "roleName"] -> "role"
 *
 * edge cases:
 * size 1: ["str"] -> "str"
 * size 0: [] -> ""
 */
fun List<String>.commonPrefix(): String {
    if (isEmpty()) {
        return ""
    }

    var result = get(0)

    for (i in 1 until size) {
        result = result.commonPrefixWith(get(i))
    }

    return result

}
