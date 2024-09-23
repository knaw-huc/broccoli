package nl.knaw.huc.broccoli.service

import com.jayway.jsonpath.ReadContext

// migrate to ES specific 'util'
fun extractAggregations(context: ReadContext) = context.read<Map<String, Any>>("$.aggregations")
    ?.mapNotNull { aggregation ->
        @Suppress("UNCHECKED_CAST")
        val aggValuesMap = aggregation.value as Map<String, Any>
        if ("buckets" in aggValuesMap) {
            @Suppress("UNCHECKED_CAST")
            val buckets = aggValuesMap["buckets"] as List<Map<String, Any>>
            if (buckets.isEmpty())
                null
            else {
                mapOf(aggregation.key to buckets.associate { (it["key_as_string"] ?: it["key"]) to it["doc_count"] })
            }
        } else {
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
        }
    }
    ?.groupByKey()

inline fun <reified V> getValueAtPath(anno: Map<*, *>, path: String): V? {
    val steps = path.split('.').iterator()

    var cur: Any = anno
    while (cur is Map<*, *>) {
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
