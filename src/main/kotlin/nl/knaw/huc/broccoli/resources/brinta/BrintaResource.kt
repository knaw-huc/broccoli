package nl.knaw.huc.broccoli.resources.brinta

import com.jayway.jsonpath.PathNotFoundException
import jakarta.ws.rs.*
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.ResourcePaths.BRINTA
import nl.knaw.huc.broccoli.config.IndexConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepoSearchResult
import nl.knaw.huc.broccoli.service.anno.AnnoSearchResultInterpreter
import nl.knaw.huc.broccoli.service.readEntityAsJsonString
import nl.knaw.huc.broccoli.service.toJsonString
import org.slf4j.LoggerFactory

@Path("$BRINTA/{projectId}")
@Produces(MediaType.APPLICATION_JSON)
class BrintaResource(
    private val projects: Map<String, Project>,
    private val client: Client
) {

    @POST
    @Path("{indexName}")
    fun createIndex(
        @PathParam("projectId") projectId: String,
        @PathParam("indexName") indexName: String
    ): Response {
        val project = getProject(projectId)
        val index = getIndex(project, indexName)
        logger.info("Creating ${project.name} index: ${index.name}")

        val properties = mutableMapOf(
            "text" to mapOf(
                "type" to "text",
                "fields" to mapOf(
                    "tokenCount" to mapOf(
                        "type" to "token_count",
                        "analyzer" to "fulltext_analyzer"
                    )
                ),
                "index_options" to "offsets",
                "analyzer" to "fulltext_analyzer"
            ),
        )
        index.fields.forEach { field ->
            field.type.let { type -> properties[field.name] = mapOf("type" to type) }
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
                      "tokenizer": "standard",
                      "filter": [
                        "lowercase"
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()
            .also { mapping -> logger.info("mapping: $mapping") }
            .let { mapping ->
                client.target(project.brinta.uri)
                    .path(index.name)
                    .request()
                    .put(Entity.json(mapping))
                    .also { logger.info("response: $it") }
                    .readEntityAsJsonString()
                    .also { logger.info("entity: $it") }
            }
            .let { result ->
                Response.ok(result).build()
            }
    }

    @GET
    @Path("indices")
    fun getIndices(@PathParam("projectId") projectId: String): Response =
        getProject(projectId).brinta.indices
            .associate { idx ->
                idx.name to idx.fields.associate { field ->
                    field.name to (field.nested?.let { nf -> nf.fields.associate { it.name to it.type } } ?: field.type)
                }
            }
            .let { Response.ok(it).build() }

    @DELETE
    @Path("{indexName}")
    fun deleteIndex(
        @PathParam("projectId") projectId: String,
        @PathParam("indexName") indexName: String,
        @QueryParam("deleteKey") deleteKey: String?
    ): Response {
        val project = getProject(projectId)
        val index = getIndex(project, indexName)
        logger.warn("Deleting ${project.name} index: ${index.name}")

        if (project.brinta.deleteKey != deleteKey) {
            logger.warn("Unauthorized request: config: [${project.brinta.deleteKey}] vs param: [$deleteKey]")
            throw WebApplicationException(UNAUTHORIZED)
        }

        return client.target(project.brinta.uri)
            .path(index.name)
            .request()
            .delete()
            .also { logger.info("response: $it") }
            .readEntityAsJsonString()
            .also { logger.info("entity: $it") }
            .let { result ->
                Response.ok(result).build()
            }
    }

    @POST
    @Path("{indexName}/fill")
    fun fillIndex(
        @PathParam("projectId") projectId: String,      // e.g., "republic"
        @PathParam("indexName") indexName: String,      // e.g., "resolutions"
        @QueryParam("metaAnno") metaAnno: String?,      // e.g., 'tf:File'
        @QueryParam("metaValues") metaValues: String?,  // e.g., "1728" (optional, if not given: index all)
        @QueryParam("take") take: Int? = null,          // testing param, only index first 'take' items
    ): Response {
        val project = getProject(projectId)

        logger.atInfo()
            .setMessage("Filling index")
            .addKeyValue("project", project)
            .addKeyValue("index", indexName)
            .log()

        val index = getIndex(project, indexName)

        // assume AnnoRepo may just have had new data uploaded, so invalidate query cache
        project.annoRepo.invalidateCache()

        val metadataKey = metaAnno ?: project.topTierBodyType
        val requestedMetadataPairs = metaValues
            ?.split(',')
            ?.map { metadataValue -> Pair(metadataKey, metadataValue) }
            ?.also { logger.info(" indexing body.metadata pair: $it") }
            ?: emptyList()

        val todo = project.annoRepo.findByMetadata(
            bodyType = project.topTierBodyType,
            metadata = requestedMetadataPairs
        )
        logger.atInfo().log("Indexing {} items: ", todo.size)

        val ok = mutableListOf<String>()
        val err = mutableListOf<Map<*, *>>()
        val result = mapOf(
            "ok" to ok,
            "err" to err
        )

        todo.forEachIndexed { i, cur ->
            logger.atInfo().log("Indexing #{} -> {}: {}", i, cur.bodyType(), cur.bodyId())

            // find annotations with body.type corresponding to this index, overlapping with current item's text range
            val target = cur.withField<Any>("NormalText", "source").first()
            val selector = target["selector"] as Map<*, *>
            project.annoRepo.streamOverlap(
                bodyTypes = Constants.isIn(index.bodyTypes.toSet()),
                source = target["source"] as String,
                start = selector["start"] as Int,
                end = selector["end"] as Int
            ).apply {
                take?.let { limit ->
                    logger.atInfo().log("limiting: only indexing first {} item(s)", limit)
                    take(limit)
                }
            }
                .map(::AnnoRepoSearchResult)
                .forEach { anno ->
                    // use anno's body.id as Elastic documentId
                    val docId = anno.bodyId()
                    logger.atDebug().log("gathering index info for annoId / ES docId: {}", docId)

                    // build index payload for current anno
                    val payload = mutableMapOf<String, Any>()

                    // First, core payload for index: fetch "full text" from (remote) URL
                    payload["text"] = project.textFetcher.fetchText(
                        AnnoSearchResultInterpreter(anno, project.textType).findTextSource()
                    )

                    // Then, optional extra payload: fields from config
                    index.fields.forEach { field ->
                        field.path?.let { path ->
                            try {
                                anno.read(path)?.let { payload[field.name] = it }
                                logger.atTrace().log("payload[{}] -> {}", field.name, payload[field.name])
                            } catch (_: PathNotFoundException) {
                                // Must catch PNF, even though DEFAULT_PATH_LEAF_TO_NULL is set, because intermediate
                                //   nodes can also be null, i.e., they don't exist, which still yields a PNF Exception.
                                // Ignore this, just means the annotation doesn't have a value for this field
                            }
                        }
                    }

                    logger.atInfo().log("Indexing {}, payload.size={}", docId, payload.size)

                    client.target(project.brinta.uri)
                        .path(index.name).path("_doc").path(docId)
                        .request()
                        .put(Entity.json(payload))
                        .run {
                            if (statusInfo.family != Response.Status.Family.SUCCESSFUL) {   // could be OK or CREATED
                                val entity = readEntityAsJsonString()       // !! must read entity to close connection!
                                logger.atWarn().log("Failed to index {}: {}", docId, entity)
                                err.add(
                                    mapOf(
                                        "bodyId" to docId,
                                        "annoURL" to anno.read("$.id"),
                                        "elastic" to entity
                                    )
                                )
                            } else {
                                close() // explicit close, or connection pool will be exhausted !!!
                                ok.add(docId)
                            }
                        }
                }
        }

        return Response.ok(result).build()
    }

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

    companion object {
        private val logger = LoggerFactory.getLogger(BrintaResource::class.java)
    }
}
