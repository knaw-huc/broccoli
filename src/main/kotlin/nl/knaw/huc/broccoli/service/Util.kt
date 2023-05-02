package nl.knaw.huc.broccoli.service

import com.jayway.jsonpath.ReadContext

fun <K, V> List<Map<K, V>>.groupByKey(): Map<K, V> = flatMap { it.asSequence() }.associate { it.key to it.value }

// migrate to ES specific 'util'
fun extractAggregations(context: ReadContext) = context.read<Map<String, Any>>("$.aggregations")
    ?.map { aggregation ->
        @Suppress("UNCHECKED_CAST")
        val buckets = (aggregation.value as Map<String, Any>)["buckets"]

        @Suppress("UNCHECKED_CAST")
        val associatedValues = (buckets as List<Map<String, Any>>)
            .associate { (it["key_as_string"] ?: it["key"]) to it["doc_count"] }

        mapOf(aggregation.key to associatedValues)
    }
    ?.groupByKey()
