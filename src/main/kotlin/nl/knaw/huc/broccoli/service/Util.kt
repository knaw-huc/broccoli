package nl.knaw.huc.broccoli.service

import com.jayway.jsonpath.ReadContext
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
                        (it["key_as_string"] ?: it["key"]) to it["doc_count"]
                    })
                }
            } else if ("nested" in aggValuesMap) {
                mapOf(
                    aggregation.key to mutableListOf<Map<String, Any>>().apply {
                        aggValuesMap
                            .filter { it.key != "doc_count" }
                            .forEach { (nestedFacetName: String, nestedFacetValues: Any) ->
                                val nestedAggValuesMap = nestedFacetValues as Map<*, *>
                                if ("buckets" in nestedAggValuesMap) {
                                    @Suppress("UNCHECKED_CAST")
                                    val nestedBuckets = nestedAggValuesMap["buckets"] as List<Map<String, Any>>
                                    if (nestedBuckets.isNotEmpty()) {
                                        add(
                                            mapOf(nestedFacetName to nestedBuckets.associate {
                                                (it["key_as_string"] ?: it["key"]) to
                                                        (it["documents"] as Map<*, *>)["doc_count"]
                                            })
                                        )
                                    }
                                }
                            }
                    }.groupByKey()
                )
            } else if ("filter" in aggValuesMap) {
                val scopeName = aggregation.key
                System.err.println("logical facet ${scopeName}: $aggValuesMap")
                val buckets = getValueAtPath<Map<String, Any>>(aggValuesMap, "filter.buckets")
                    ?: return@mapNotNull null
                System.err.println("  -> buckets: $buckets")
                buckets.forEach { (key, vals) ->
                    System.err.println("    +- $key")
                    System.err.println("    +- values:")
                    @Suppress("UNCHECKED_CAST")
                    (vals as Map<String, Any>).filter { it.key != "doc_count" }
                        .forEach(System.err::println)
                }
                null
            } else null
        }
        ?.groupByKey()

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
