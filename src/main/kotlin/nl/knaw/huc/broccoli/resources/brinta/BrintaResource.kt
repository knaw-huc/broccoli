package nl.knaw.huc.broccoli.resources.brinta

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.jayway.jsonpath.PathNotFoundException
import jakarta.ws.rs.*
import jakarta.ws.rs.client.Client
import jakarta.ws.rs.client.Entity
import jakarta.ws.rs.core.GenericType
import jakarta.ws.rs.core.HttpHeaders.AUTHORIZATION
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.core.Response.Status.OK
import jakarta.ws.rs.core.Response.Status.UNAUTHORIZED
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.ResourcePaths.BRINTA
import nl.knaw.huc.broccoli.config.IndexConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepoSearchResult
import org.slf4j.LoggerFactory

@Path("$BRINTA/{projectId}")
@Produces(MediaType.APPLICATION_JSON)
class BrintaResource(
    private val projects: Map<String, Project>,
    private val client: Client
) {
    private val logger = LoggerFactory.getLogger(javaClass)

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
            .associate { idx -> idx.name to idx.fields.associate { f -> f.name to (f.type ?: "undefined") } }
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
        @QueryParam("tierMeta") tierMeta: String?,      // e.g., 'file'
        @QueryParam("tierValues") tierValues: String?,  // e.g., "1728" (optional, if not given: index all)
        @QueryParam("take") take: Int? = null,          // testing param, only index first 'take' items
    ): Response {
        val project = getProject(projectId)

        logger.atInfo()
            .setMessage("Filling index")
            .addKeyValue("project", project)
            .addKeyValue("index", indexName)
            .log()

        val index = getIndex(project, indexName)

        val topTier = project.tiers[0]
        val topTierValue = tierValues
            ?.split(',')
            ?.map { tierValue -> Pair(tierMeta ?: topTier.name, tierValue) }
            ?.also { logger.info(" indexing tier: $it") }
            ?: emptyList()

        val ok = mutableListOf<String>()
        val err = mutableListOf<Map<*, *>>()
        val result = mapOf(
            "ok" to ok,
            "err" to err
        )

        val joinSeparator = project.brinta.joinSeparator ?: ""

        val todo = project.annoRepo.findByTiers(
            bodyType = topTier.anno ?: topTier.name.capitalize(),
            tiers = topTierValue
        )

        logger.atInfo().log("Indexing {} items: ", todo.size)
        todo.forEachIndexed { i, cur ->
            logger.atInfo().log("Indexing #{} -> {}: {}", i, cur.bodyType(), cur.bodyId())

            // fetch all text lines for this tier
            val textLines = fetchTextLines(project, cur)

            // find all annotations with body.type matching this index, overlapping with top tier's range of lines
            val target = cur.withField<Any>("Text", "source").first()
            val selector = target["selector"] as Map<*, *>
            var annos = project.annoRepo.streamOverlap(
                source = target["source"] as String,
                start = selector["start"] as Int,
                end = selector["end"] as Int,
                bodyTypes = Constants.isIn(index.bodyTypes.toSet())
            )

            if (take != null) {
                logger.atInfo().log("limiting: only indexing first {} item(s)", take)
                annos = annos.take(take)
            }

            annos.map(::AnnoRepoSearchResult)
                .forEach { anno ->
                    // use anno's body.id as documentId in index
                    val docId = anno.bodyId()
                    logger.atDebug().log("gathering index info for annoId / ES docId: {}", docId)

                    // build index payload for current anno
                    val payload = mutableMapOf<String, Any>()

                    // First: core payload for index: fetch "full text" from (remote) URL
                    anno.withoutField<String>(project.textType, "selector")
                        .also { if (it.size > 1) logger.warn("multiple Text targets without selector: $it") }
                        .first() // more than one text target without selector? -> arbitrarily choose the first
                        .let { textTarget ->
                            val textURL = textTarget["source"] as String
                            val fetchedSegments = fetchTextSegmentsLocal(textLines, textURL)
                            if (fetchedSegments.isNotEmpty()) {
                                if (logger.isTraceEnabled) { // skip iterator if trace is off anyway
                                    fetchedSegments.forEachIndexed { i, s ->
                                        logger.atTrace().log("fetchedSegments[{}] = [{}]", i, s)
                                    }
                                } else logger.atDebug().log("fetching {} segments", fetchedSegments.size)

                                val joinedSegments = fetchedSegments.joinToString(joinSeparator)
                                logger.atTrace().log("joinedSegments.length: {}", joinedSegments.length)

                                payload["text"] = joinedSegments
                                ok.add(docId)
                            } else {
                                logger.atWarn().log("Failed to fetch text for {} from {}", docId, textURL)
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
                        try {
                            anno.read(field.path)?.let { payload[field.name] = it }
                            logger.atTrace().log("payload[{}] -> {}", field.name, payload[field.name])
                        } catch (e: PathNotFoundException) {
                            // Must catch PNF, even though DEFAULT_PATH_LEAF_TO_NULL is set, because intermediate
                            //   nodes can also be null, i.e., they don't exist, which still yields a PNF Exception.
                            // Ignore this, just means the annotation doesn't have a value for this field
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
                            } else {
                                close() // explicit close, or connection pool will be exhausted !!!
                            }
                        }

                }
        }

        return Response.ok(result).build()
    }

    private fun fetchTextLines(project: Project, tier: AnnoRepoSearchResult): List<String> =
        tier.withoutField<String>(project.textType, "selector")
            .also { if (it.size > 1) logger.warn("multiple Text targets without selector: $it") }
            .first() // more than one text target without selector? -> arbitrarily choose the first
            .let { textTarget ->
                val textURL = textTarget["source"] as String
                var builder = client.target(textURL).request()

                with(project.textRepo) {
                    if (apiKey != null && canResolve(textURL)) {
                        logger.atDebug().log("with apiKey {}", apiKey)
                        builder = builder.header(AUTHORIZATION, "Basic $apiKey")
                    }
                }

                val resp = builder.get()

                return when (resp.status) {
                    OK.statusCode -> {
                        resp.readEntity(object : GenericType<ArrayList<String>>() {})
                    }

                    UNAUTHORIZED.statusCode -> {
                        logger.atWarn().log("Auth failed fetching {}", textURL)
                        throw ClientErrorException("Need credentials for $textURL", UNAUTHORIZED)
                    }

                    else -> {
                        logger.atWarn().log("Failed to fetch {} (status: {})", textURL, resp.status)
                        emptyList()
                    }
                }
            }

    private fun fetchTextSegmentsLocal(textLines: List<String>, textURL: String): List<String> {
        logger.atInfo().log("fetchTextSegmentsLocal: URL={}", textURL)

        val coords = textURL.indexOf("segments/index/") + "segments/index/".length
        logger.atInfo().log("fetchTextSegmentsLocal: coords={}", textURL.substring(coords))

        val parts = textURL.substring(coords).split('/')
        return when (parts.size) {
            2 -> {
                val from = parts[0].toInt()
                val to = parts[1].toInt()

                logger.atDebug().setMessage("2 coords")
                    .addKeyValue("from", from)
                    .addKeyValue("to", to)
                    .addKeyValue("textLines.size", textLines.size)
                    .log()

                textLines.subList(from, to + 1)
            }

            4 -> {
                val from = parts[0].toInt()
                val startIndex = parts[1].toInt()
                val to = parts[2].toInt()
                val endIndex = parts[3].toInt()

                logger.atDebug().setMessage("4 coords")
                    .addKeyValue("from", from)
                    .addKeyValue("startIndex", startIndex)
                    .addKeyValue("to", to)
                    .addKeyValue("endIndex", endIndex)
                    .log()

                // start out with correct sublist from all segments
                val result = textLines.subList(from, to + 1).toMutableList()

                // adjust first and last segments according to start-/endIndex
                result[0] = result.first().substring(startIndex)
                result[result.lastIndex] = result.last().substring(0, endIndex + 1)

                result
            }

            else -> {
                logger.atWarn().log("Failed to extract coordinates from {}", coords)
                emptyList()
            }
        }
    }

    private fun Response.readEntityAsJsonString(): String = readEntity(String::class.java) ?: ""

    private fun Map<String, Any>.toJsonString() = jacksonObjectMapper().writeValueAsString(this)

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
