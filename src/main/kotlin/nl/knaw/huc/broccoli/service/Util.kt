package nl.knaw.huc.broccoli.service

import com.jayway.jsonpath.ReadContext

// migrate to ES specific 'util'
fun extractAggregations(context: ReadContext) = context.read<Map<String, Any>>("$.aggregations")
    ?.mapNotNull { aggregation ->
        @Suppress("UNCHECKED_CAST")
        val buckets = (aggregation.value as Map<String, Any>)["buckets"] as List<Map<String, Any>>

        if (buckets.isEmpty())
            null
        else {
            mapOf(aggregation.key to buckets.associate { (it["key_as_string"] ?: it["key"]) to it["doc_count"] })
        }
    }
    ?.groupByKey()
