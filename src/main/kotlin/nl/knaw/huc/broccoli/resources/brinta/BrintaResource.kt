package nl.knaw.huc.broccoli.resources.brinta

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jayway.jsonpath.DocumentContext
import com.jayway.jsonpath.ParseContext
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.ResourcePaths.BRINTA
import nl.knaw.huc.broccoli.config.IndexConfiguration
import nl.knaw.huc.broccoli.config.IndexFieldConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepoSearchResult
import org.slf4j.LoggerFactory
import javax.ws.rs.*
import javax.ws.rs.client.Client
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("$BRINTA/{projectId}")
@Produces(MediaType.APPLICATION_JSON)
class BrintaResource(
    private val projects: Map<String, Project>,
    private val client: Client,
    private val jsonParser: ParseContext
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @POST
    @Path("{indexName}")
    fun createIndex(
        @PathParam("projectId") projectId: String,
        @PathParam("indexName") indexName: String
    ): Response {
        val project = getProject(projectId)
        val index = getIndex(project, indexName)
        log.info("Creating ${project.name} index: ${index.name}")

        val properties = mutableMapOf<String, Any>(
            "text" to mapOf(
                "type" to "text",
                "index_options" to "offsets",
                "analyzer" to "fulltext_analyzer"
            )
        )
        index.fields.forEach { field ->
            field.type?.let { type -> properties[field.name] = mapOf("type" to type) }
        }

        val mappings = mapOf("properties" to properties)

        return """
            {
              "mappings": ${mappings.toJsonString()},
              "settings": {
                "analysis": {
                  "analyzer": {
                    "fulltext_analyzer": {
                      "type": "custom",
                      "tokenizer": "whitespace",
                      "filter": [
                        "lowercase",
                        "type_as_payload"
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()
            .also { mapping -> log.info("mapping: $mapping") }
            .let { mapping ->
                client.target(project.brinta.uri)
                    .path(index.name)
                    .request()
                    .put(Entity.json(mapping))
                    .also { log.info("response: $it") }
                    .readEntityAsJsonString()
                    .also { log.info("entity: $it") }
            }
            .let { result ->
                Response.ok(result).build()
            }
    }

    @GET
    @Path("indices")
    fun getIndices(
        @PathParam("projectId") projectId: String
    ): Response = getProject(projectId)
        .also { log.info("project: ${it.name}") }
        .brinta
        .indices
        .let { indices ->
            mutableMapOf<String, Map<String, String>>().apply {
                indices.forEach { index ->
                    put(index.name, mutableMapOf<String, String>().apply {
                        index.fields.forEach { field -> put(field.name, field.type ?: "undefined") }
                    })
                }
            }
        }
        .let { map -> Response.ok(map).build() }

    @GET
    @Path("facets")
    fun getFacets(
        @PathParam("projectId") projectId: String,
        @QueryParam("indexName") indexName: String?
    ): Response = getProject(projectId)
        .let { project ->
            getIndex(project, indexName)
                .let { index ->
                    index.fields
                        .mapNotNull(::buildFieldAggregationQuery)
                        .flatMap { it.asSequence() }
                        .associate { it.key to it.value }
                        .let { mapOf("size" to 0, "aggregations" to it) }
                        .toJsonString()
                        .also { log.info("query: $it") }
                        .let { query ->
                            client.target(project.brinta.uri).path(index.name).path("_search")
                                .request()
                                .post(Entity.json(query))
                                .also { log.info("response: $it") }
                                .readEntityAsJsonString()
                                .also { log.info("entity: $it") }
                        }
                        .let<String, DocumentContext>(jsonParser::parse)
                        .let { context ->
                            index.fields
                                .filter(::isSupportedFieldType)
                                .map { field ->
                                    mapOf(field.name to
                                            context
                                                .read<List<Map<String, Any>>>("$.aggregations.${field.name}.buckets")
                                                .associate { (it["key_as_string"] ?: it["key"]) to it["doc_count"] }
                                    )
                                }
                        }
                }
        }
        .let { Response.ok(it).build() }

    private fun isSupportedFieldType(field: IndexFieldConfiguration): Boolean =
        field.type in setOf("date", "keyword")

    private fun buildFieldAggregationQuery(field: IndexFieldConfiguration) =
        when (field.type) {
            "keyword" -> mapOf(
                field.name to mapOf(
                    "terms" to mapOf(
                        "field" to field.name,
                        "size" to 100
                    )
                )
            )

            "date" -> mapOf(
                field.name to mapOf(
                    "date_histogram" to mapOf(
                        "field" to field.name,
                        "format" to "yyyy-MM",
                        "calendar_interval" to "month"
                    )
                )
            )

            else -> {
                log.info("${field.name}: unhandled type '${field.type}'")
                null
            }
        }

    @DELETE
    @Path("{indexName}")
    fun deleteIndex(
        @PathParam("projectId") projectId: String,
        @PathParam("indexName") indexName: String
    ): Response {
        val project = getProject(projectId)
        val index = getIndex(project, indexName)
        log.warn("Deleting ${project.name} index: ${index.name}")

        return client.target(project.brinta.uri)
            .path(index.name)
            .request()
            .delete()
            .also { log.info("response: $it") }
            .readEntityAsJsonString()
            .also { log.info("entity: $it") }
            .let { result ->
                Response.ok(result).build()
            }
    }

    @POST
    @Path("{indexName}/fill")
    fun fillIndex(
        @PathParam("projectId") projectId: String,      // e.g., "republic"
        @PathParam("indexName") indexName: String,      // e.g., "resolutions"
        @QueryParam("tierValue") tierParam: String?,    // e.g., "1728" (optional, if not given: index all)
        @QueryParam("take") take: Int? = null,          // testing param, only index first 'take' items
    ): Response {
        val project = getProject(projectId)
        log.info("filling index for project: $project, index: $indexName")

        val index = getIndex(project, indexName)

        val topTierName = project.tiers[0].name
        val topTierValue = if (tierParam == null) emptyList() else listOf(Pair(topTierName, tierParam))

        val ok = mutableListOf<String>()
        val err = mutableListOf<Map<*, *>>()
        val result = mapOf(
            "ok" to ok,
            "err" to err
        )

        project.annoRepo.findByTiers(
            bodyType = topTierName.capitalize(),
            tiers = topTierValue
        ).forEach { topTier ->
            log.info("Indexing ${topTier.bodyType()}: ${topTier.bodyId()}")

            // extract entire text range of current top tier (for overlap query)
            val textTarget = topTier.withField<Any>("Text", "source").first()
            val source = textTarget["source"] as String
            val selector = textTarget["selector"] as Map<*, *>
            val start = selector["start"] as Int
            val end = selector["end"] as Int

            // find all annotations with body.type matching this index, overlapping with top tier's range
            var annos = project.annoRepo.streamOverlap(source, start, end, Constants.isIn(index.bodyTypes.toSet()))

            if (take != null) {
                log.info("limiting: only indexing first $take item(s)")
                annos = annos.take(take)
            }

            annos.map(::AnnoRepoSearchResult)
                .forEach { anno ->
                    // use anno's body.id as documentId in index
                    val docId = anno.bodyId()

                    // build index payload for current anno
                    val payload = mutableMapOf<String, Any>()

                    // First: core payload for index: fetch "full text" from (remote) URL
                    anno.withoutField<String>("Text", "selector")
                        .also { if (it.size > 1) log.warn("multiple Text targets without selector: $it") }
                        .first() // more than one text target without selector? -> arbitrarily choose the first
                        .let { textTarget ->
                            val textURL = textTarget["source"] as String
                            val textSegments = fetchTextSegments(textURL)
                            val joinedText = textSegments.joinToString(separator = " ")
                            val segmentLengths = textSegments.map { it.length }
                            if (textSegments != null) {
                                payload["text"] = joinedText
                                payload["lengths"] = segmentLengths
                                ok.add(docId)
                            } else {
                                log.warn("Failed to fetch text for $docId from $textURL")
                                err.add(
                                    mapOf(
                                        "body.id" to docId,
                                        "annoURL" to anno.read("$.id"),
                                        "textURL" to textURL
                                    )
                                )
                            }
                        }

                    // Then: optional extra payload: fields from config
                    index.fields.forEach { field ->
                        anno.read(field.path)
                            ?.let { payload[field.name] = it }
                            ?: log.info("$docId has no value for field ${field.name} at ${field.path}")
                    }

                    log.info("Indexing $docId, payload=$payload")

                    client.target(project.brinta.uri)
                        .path(index.name).path("_doc").path(docId)
                        .request()
                        .put(Entity.json(payload))
                        .run {
                            if (statusInfo.family != Response.Status.Family.SUCCESSFUL) {   // could be OK or CREATED
                                val entity = readEntityAsJsonString()       // reading entity also closes connection
                                log.warn("Failed to index $docId: $entity")
                            } else {
                                close() // explicit close, or connection pool will be exhausted !!!
                            }
                        }

                }
        }

        return Response.ok(result).build()
    }

    private fun Response.readEntityAsJsonString(): String = readEntity(String::class.java) ?: ""

    private fun Map<String, Any>.toJsonString() = jacksonObjectMapper().writeValueAsString(this)

    private fun fetchTextSegments(textURL: String) =
        client.target(textURL).request().get().run {
            if (status == Response.Status.OK.statusCode) {
                jsonParser.parse(readEntityAsJsonString()).read<List<String>>("$")
            } else {
                close()
                emptyList()
            }
        }

    private fun String.capitalize(): String = replaceFirstChar(Char::uppercase)

    private fun getProject(projectId: String): Project {
        return projects[projectId]
            ?: throw NotFoundException("Unknown project: $projectId. See /projects for known projects")
    }

    private fun getIndex(project: Project, indexName: String?): IndexConfiguration =
        indexName
            ?.let {
                project.brinta.indices.find { index -> index.name == indexName }
                    ?: throw NotFoundException("index '$indexName' not configured for project: ${project.name}")
            }
            ?: project.brinta.indices[0]

}
