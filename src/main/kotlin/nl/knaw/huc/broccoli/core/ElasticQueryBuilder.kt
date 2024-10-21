package nl.knaw.huc.broccoli.core

import jakarta.ws.rs.BadRequestException
import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.config.IndexConfiguration
import org.slf4j.LoggerFactory
import kotlin.properties.Delegates

class ElasticQueryBuilder(private val index: IndexConfiguration) {
    private var from by Delegates.notNull<Int>()
    private var size by Delegates.notNull<Int>()
    private var fragmentSize by Delegates.notNull<Int>()
    private lateinit var sortBy: String
    private lateinit var sortOrder: String
    private lateinit var query: IndexQuery

    fun sortBy(sortBy: String) = apply { this.sortBy = sortBy }

    fun sortOrder(sortOrder: String) = apply { this.sortOrder = sortOrder }

    fun fragmentSize(size: Int) = apply { this.fragmentSize = size }

    fun from(from: Int) = apply { this.from = from }

    fun size(size: Int) = apply { this.size = size }

    fun query(query: IndexQuery) = apply { this.query = normalizeQuery(query) }

    fun toElasticQuery() = ElasticQuery(
        from = from,
        size = size,
        sort = Sort(sortBy, sortOrder),
        query = buildMainQuery(),
        highlight = query.text?.let { queryText ->
            HighlightTerm(
                text = queryText,
                fragmentSize = fragmentSize,
                extraFields = index.fields.filter { it.type == "text" }.map { it.name }
            )
        },
        aggregations = Aggregations(
            (query.aggregations?.keys ?: configuredFieldNames()).mapNotNull { aggName ->
                query.aggregations?.get(aggName)?.let { aggSpec ->
                    when (configuredFieldType(aggName)) {
                        "byte", "keyword", "short" ->
                            TermAggregation(
                                name = aggName,
                                numResults = aggSpec["size"] as Int,
                                sortOrder = orderParams[aggSpec["order"]]
                            )

                        "date" -> DateAggregation(aggName)

                        "nested" -> {
                            @Suppress("UNCHECKED_CAST")
                            val nestedAggSpec = aggSpec as Map<String, Map<String, Any>>
                            NestedAggregation(name = aggName, fields = nestedAggSpec)
                        }

                        else -> null
                    }
                }
            }
        )
    )

    fun toMultiFacetCountQueries() = mutableListOf<ElasticQuery>()
        .apply {
            query.terms?.forEach { term ->
                add(
                    ElasticQuery(
                        from = from,
                        size = size,
                        sort = Sort(sortBy, sortOrder),
                        query = buildMainQuery { it.key != term.key },
                        aggregations = Aggregations(
                            query.aggregations
                                ?.get(term.key)
                                ?.let { termAgg ->
                                    if (configuredFieldType(term.key) == "nested") {
                                        @Suppress("UNCHECKED_CAST")
                                        val spec = termAgg as MutableMap<String, Map<String, Any>>
                                        NestedAggregation(
                                            name = term.key,
                                            fields = spec.filterKeys { (term.value as Map<*, *>).containsKey(it) }
                                        )
                                    } else {
                                        TermAggregation(
                                            name = term.key,
                                            numResults = termAgg["size"] as Int,
                                            sortOrder = orderParams[termAgg["order"]]
                                        )
                                    }
                                }
                                ?.let { listOf(it) }
                                ?: emptyList()
                        )
                    )
                )
            }
        }

    private fun normalizeQuery(query: IndexQuery): IndexQuery {
        return IndexQuery(
            date = query.date,
            range = query.range,
            terms = query.terms,
            aggregations = query.aggregations,
            text = query.text
                ?.trim()
                ?.let { q ->
                    if (ES_FIELD_PREFIX.matches(q)) q else buildString {
                        append("text:$q")
                        index.fields
                            .filter { it.type == "text" }
                            .map { it.name }
                            .forEach { fieldName ->
                                append(" OR ")
                                append("$fieldName:$q")
                            }
                    }
                },
        )
    }

    private fun buildMainQuery(predicate: ((Map.Entry<String, Any>) -> Boolean) = { true }) = ComplexQuery(
        bool = BoolQuery(
            must = mutableListOf<BaseQuery>().apply {
                /*
                 * entities ->
                 *    (".category", "HOE") ->
                 *       (roleName -> ["koning"])
                 *       (roleLabels -> ["Adel & Vorsten"]
                 *    (".category", "PERS") ->
                 *       (personName -> ["frankrijk"])
                 *       (personLabels -> ["ongelabeld"])
                 * attendants ->
                 *   [...]
                 */
                val logicalQueryBuilder = LogicalQueryBuilder(index)
                query.terms
                    ?.filter(predicate)
                    ?.forEach { termsQuery ->
                        logger.atInfo().addKeyValue("name", termsQuery.key).log("termsQuery")
                        when (termsQuery.value) {
                            is List<*> -> {
                                val field = index.fields.find { it.name == termsQuery.key }
                                if (field?.logical != null) {
                                    @Suppress("UNCHECKED_CAST")
                                    logicalQueryBuilder.add(field.name, termsQuery.value as MutableList<String>)
                                } else {
                                    add(TermsQuery(mapOf(termsQuery.key to (termsQuery.value as List<*>))))
                                }
                            }

                            is Map<*, *> -> {
                                @Suppress("UNCHECKED_CAST")
                                add(NestedQuery(termsQuery.key, termsQuery.value as Map<String, List<String>>))
                            }
                        }
                    }
                addAll(logicalQueryBuilder.toQueries())
                query.date?.let {
                    add(RangeQuery(it.name, it.from, it.to, relation = "within"))
                }
                query.range?.let {
                    add(RangeQuery(it.name, it.from, it.to))
                }
                query.text?.let {
                    add(FullTextQuery(QueryString(it)))
                }
            }
        )
    )


    class LogicalQueryBuilder(private val index: IndexConfiguration) {
        private val scopes = mutableMapOf<String, LogicalTypeScope>()

        data class FixedTypeKey(val path: String, val value: String)

        class LogicalTypeScope {
            val fixedValueTypes = mutableMapOf<FixedTypeKey, MutableMap<String, MutableList<String>>>()

            fun update(key: FixedTypeKey, logicalPath: String, values: MutableList<String>) {
                fixedValueTypes.merge(key, mutableMapOf(logicalPath to values)) { soFar, _ ->
                    soFar[logicalPath] = values; soFar
                }
            }

            override fun toString() = buildString {
                append("LogicalTypeScope(fixedValueTypes=")
                append(fixedValueTypes.toString())
                append(')')
            }
        }

        fun add(fieldName: String, values: MutableList<String>) {
            val field = index.fields.find { it.name == fieldName }
                ?: throw BadRequestException("Unknown field: $fieldName")
            val logical = field.logical
                ?: throw BadRequestException("Missing 'logical:' section in field: $fieldName")
            val fixed = logical.fixed
                ?: throw BadRequestException("Missing 'fixed:' section in logical field: $fieldName")
            val key = FixedTypeKey(fixed.path, fixed.value)
            scopes.putIfAbsent(logical.scope, LogicalTypeScope())
            scopes[logical.scope]!!.update(key, logical.path, values)
            System.err.println("AFTER ADD, SCOPES[" + logical.scope + "]=" + scopes[logical.scope])
        }

        fun toQueries(): List<BaseQuery> =
            mutableListOf<BaseQuery>().apply {
                scopes.forEach { (scopeName: String, scope: LogicalTypeScope) ->
                    scope.fixedValueTypes.forEach { (key: FixedTypeKey, values: Map<String, List<String>>) ->
                        add(LogicalQuery(scopeName, key, values))
                    }
                }
            }
    }

    private fun configuredFieldNames() = index.fields.map { it.name }

    private fun configuredFieldType(name: String) = index.fields.find { it.name == name }?.type

    companion object {
        private val logger = LoggerFactory.getLogger(ElasticQueryBuilder::class.java)

        private val ES_FIELD_PREFIX = """^[a-zA-Z]*:""".toRegex()

        private val orderParams = mapOf(
            "keyAsc" to mapOf("_key" to "asc"),
            "keyDesc" to mapOf("_key" to "desc"),
            "countDesc" to mapOf("_count" to "desc")
        )
    }
}
