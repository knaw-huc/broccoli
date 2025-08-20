package nl.knaw.huc.broccoli.core

import jakarta.ws.rs.BadRequestException
import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.config.IndexConfiguration
import org.slf4j.LoggerFactory
import kotlin.properties.Delegates

class ElasticQueryBuilder(private val index: IndexConfiguration) {
    private val normalizer = QueryNormalizer(index)

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

    fun query(query: IndexQuery) = apply { this.query = normalizer.normalizeQuery(query) }

    fun toElasticQuery(): ElasticQuery {
        val logicalAggregationBuilder = LogicalAggregationBuilder(index)

        val query = ElasticQuery(
            from = from,
            size = size,
            sort = Sort(sortBy.let {
                if (it == "date" && index.fields.any { f -> f.name == "dateSortable" }) "dateSortable" else it
            }, sortOrder),
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
                    query.aggregations
                        ?.get(aggName)
                        ?.let { aggSpec ->
                            when (configuredFieldType(aggName)) {
                                "byte", "keyword", "short" ->
                                    TermAggregation(
                                        name = aggName,
                                        numResults = aggSpec["size"] as Int,
                                        sortOrder = orderParams[aggSpec["order"]]
                                    )

                                "date" -> DateAggregation(aggName)

                                "logical" -> {
                                    logicalAggregationBuilder.add(aggName, aggSpec)
                                    null // defer adding to Aggregations we are currently building
                                }

                                "nested" -> {
                                    @Suppress("UNCHECKED_CAST")
                                    val nestedAggSpec = aggSpec as Map<String, Map<String, Any>>
                                    NestedAggregation(name = aggName, fields = nestedAggSpec)
                                }

                                else -> null
                            }
                        }
                }
            ).addAll(logicalAggregationBuilder.toAggregations())
        )

        return query
    }

    class LogicalAggregationBuilder(private val index: IndexConfiguration) {
        private val aggSpecs = mutableMapOf<String, Map<String, Any>>()

        fun add(aggName: String, aggSpec: Map<String, Any>) {
            aggSpecs[aggName] = aggSpec
        }

        fun toAggregations(): List<Aggregation> {
            val scopes: MutableMap<String, MutableMap<String, List<Map<String, Any>>>> = mutableMapOf()

            aggSpecs.forEach { (aggName, aggSpec) ->
                val field = index.fields.find { it.name == aggName }
                    ?: throw BadRequestException("Unknown field '$aggName'")

                val logical = field.logical
                    ?: throw BadRequestException("field $aggName lacks 'logical' configuration")

                // create a copy as we may have to update its size, yet we don't want to fubar the original queryString
                val copy = aggSpec.toMutableMap()

                // logical.scope, e.g., "entities"
                scopes.merge(logical.scope, mutableMapOf(logical.path to mutableListOf(copy))) { scope, _ ->
                    scope[logical.path] =
                        if (scope[logical.path] == null)
                            mutableListOf(copy)
                        else {
                            var found = false
                            @Suppress("UNCHECKED_CAST")
                            (scope[logical.path] as MutableList<MutableMap<String, Any>>).onEach { curSpec: MutableMap<String, Any> ->
                                if (curSpec["order"] == copy["order"]) {
                                    copy["size"]?.let { newSize ->
                                        if (newSize as Int > curSpec["size"] as Int) {
                                            curSpec["size"] = newSize   // in place update of 'size', requires copy
                                        }
                                    }
                                    found = true
                                }
                            }.apply {
                                if (!found) add(copy)
                            }
                        }
                    scope
                }
            }

            return scopes.map { (scope, spec) ->
                var fixedField = ""
                val values = LinkedHashMap<String, MutableList<String>>() // preserve order from config
                index.fields.filter { it.logical?.scope == scope }
                    .forEach { field ->
                        field.logical!!.fixed?.let { fixed ->
                            fixedField = fixed.path
                            values.putIfAbsent(fixed.value, mutableListOf())
                            values[fixed.value]!!.add(field.name)
                        }
                    }
                LogicalAggregation(LogicalFilterScope(scope, spec), LogicalFilterSpec(fixedField, values))
            }
        }

        data class LogicalFilterScope(
            val name: String,                       // "entities"
            val spec: Map<String,                   // {.name=[{order=countDesc, size=10}, {order=keyAsc, size=20}], .labels=[{order=countDesc, size=10}]}
                    List<Map<String, Any>>>
        )

        data class LogicalFilterSpec(
            val path: String,                       // ".category"
            val values: Map<String, List<String>>   // {LOC=[locationName, locationLabels], PERS=[personName, personLabels], HOE=[roleName, roleLabels]}
        )
    }

    fun toMultiFacetCountQueries() = mutableListOf<ElasticQuery>()
        .apply {
            query.terms
                ?.filterNot { configuredFieldType(it.key) == "logical" }
                ?.forEach { term ->
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
                    add(RangeQuery(it.name, it.from, it.to))
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
            val fixedValueTypes = mutableMapOf<FixedTypeKey?, MutableMap<String, MutableList<String>>>()

            fun update(key: FixedTypeKey?, logicalPath: String, values: MutableList<String>) = apply {
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
            val key = logical.fixed?.let { FixedTypeKey(it.path, it.value) }
            scopes.compute(logical.scope) { _, oldValue ->
                (oldValue ?: LogicalTypeScope()).update(key, logical.path, values)
            }
        }

        fun toQueries(): List<BaseQuery> =
            mutableListOf<BaseQuery>().apply {
                scopes.forEach { (scopeName: String, scope: LogicalTypeScope) ->
                    scope.fixedValueTypes.forEach { (key: FixedTypeKey?, values: Map<String, List<String>>) ->
                        add(LogicalQuery(scopeName, key, values))
                    }
                }
            }
    }

    private fun configuredFieldNames() = index.fields.map { it.name }

    private fun configuredFieldType(name: String) =
        index.fields.find { it.name == name }
            ?.let {
                if (it.logical != null) "logical"
                else if (it.nested != null) "nested"
                else it.type
            }
            ?: "unknown"

    companion object {
        private val logger = LoggerFactory.getLogger(ElasticQueryBuilder::class.java.simpleName)

        private val ES_FIELD_PREFIX = """^[a-zA-Z]*:""".toRegex()

        private val orderParams = mapOf(
            "keyAsc" to mapOf("_key" to "asc"),
            "keyDesc" to mapOf("_key" to "desc"),
            "countDesc" to mapOf("_count" to "desc")
        )
    }
}
