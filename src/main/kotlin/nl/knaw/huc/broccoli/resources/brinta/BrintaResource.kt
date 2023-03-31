package nl.knaw.huc.broccoli.resources.brinta

import com.jayway.jsonpath.ParseContext
import jakarta.ws.rs.*
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.ResourcePaths.BRINTA
import nl.knaw.huc.broccoli.config.IndexConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepoSearchResult
import org.slf4j.LoggerFactory

@Path(BRINTA)
@Produces(MediaType.APPLICATION_JSON)
class BrintaResource(
    private val projects: Map<String, Project>,
    private val client: Client,
    private val jsonParser: ParseContext
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @POST
    @Path("{projectId}/{indexName}")
    fun createIndex(
        @PathParam("projectId") projectId: String,
        @PathParam("indexName") indexName: String
    ): Response {
        val project = getProject(projectId)
        val index = getIndex(project, indexName)
        log.info("Creating ${project.name} index: ${index.name}")

        return """
            {
              "mappings": {
                "properties": {
                  "text": {
                    "type": "text",
                    "index_options": "offsets",
                    "analyzer": "fulltext_analyzer"
                  }
                }
              },
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

    @DELETE
    @Path("{projectId}/{indexName}")
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
    @Path("{projectId}/{indexName}/fill")
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

    private fun getIndex(project: Project, indexName: String): IndexConfiguration {
        return project.brinta.indices.find { it.name == indexName }
            ?: throw NotFoundException("index '$indexName' not configured for project: ${project.name}")
    }

}
