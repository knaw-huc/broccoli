package nl.knaw.huc.broccoli.service

import com.jayway.jsonpath.ReadContext

const val ANY_WHITESPACE_PAT = "\\s+"
val ANY_WHITESPACE_REGEX = ANY_WHITESPACE_PAT.toRegex()

fun <K, V> List<Map<K, V>>.groupByKey(): Map<K, V> = flatMap { it.asSequence() }.associate { it.key to it.value }

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

fun String.capitalize(): String = replaceFirstChar(Char::uppercase)

fun String.wordCount(): Int = this.trim().split(ANY_WHITESPACE_REGEX).size
