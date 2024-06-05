package nl.knaw.huc.broccoli.resources.brinta

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
import nl.knaw.huc.broccoli.config.EnrichmentViaConfiguration
import nl.knaw.huc.broccoli.config.IndexConfiguration
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepoSearchResult
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
            .associate { idx -> idx.name to idx.fields.associate { f -> f.name to (f.type) } }
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
        @QueryParam("take") takeParam: Int? = null,     // testing param, only index first 'takeParam' items
    ): Response {
        val project = getProject(projectId)
        val joinSeparator = project.brinta.joinSeparator ?: ""

        logger.atInfo()
            .setMessage("Filling index")
            .addKeyValue("project", project)
            .addKeyValue("index", indexName)
            .log()

        val index = getIndex(project, indexName)

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

        // gather all interesting bodyTypes into a single set
        val bodyTypes = index.bodyTypes.union(index.enrich.map { it.from }.flatten())
        logger.atDebug().addKeyValue("bodyTypes", bodyTypes).log("interesting bodyTypes:")

        val coreAnnos = mutableListOf<AnnoRepoSearchResult>()
        val auxAnnos = mutableMapOf<String, MutableList<AnnoRepoSearchResult>>()
        todo
            .take(1) // debug
            .forEachIndexed { idx, curItem ->
                logger.atDebug().log("Indexing #{} -> {}: {}", idx, curItem.bodyType(), curItem.bodyId())

                val textLines = fetchTextLines(project, curItem)
                logger.atDebug().addKeyValue("textLines.size", textLines.size)

                val target = curItem.withField<Any>(type = "Text", field = "source").first()
                val selector = target["selector"] as Map<*, *>
                project.annoRepo.streamOverlap(
                    bodyTypes = Constants.isIn(bodyTypes),
                    source = target["source"] as String,
                    start = selector["start"] as Int,
                    end = selector["end"] as Int
                ).apply {
                    takeParam?.let { limit ->
                        logger.atInfo().addKeyValue("limit", limit).log("limiting taking only first item(s)")
                        take(limit)
                    }
                }
                    .map(::AnnoRepoSearchResult)
                    .forEach { anno ->
                        if (index.bodyTypes.contains(anno.bodyType())) {
                            coreAnnos.add(anno)
                        } else {
                            auxAnnos.computeIfAbsent(anno.bodyType()) { mutableListOf() }.add(anno)
                        }
                    }
                logger.atDebug()
                    .addKeyValue("core.size", coreAnnos.size).apply {
                        auxAnnos.forEach { (type, annos) -> addKeyValue("${type}.size", annos.size) }
                    }
                    .log("annotation counts:")

                coreAnnos.forEach { coreAnno ->
                    val docId = coreAnno.bodyId()
                    val payload = mutableMapOf<String, Any>()
                    coreAnno.withoutField<String>(project.textType, "selector").first().let { textTarget ->
                        val textURL = textTarget["source"] as String
                        val fetchedSegments = fetchTextSegmentsLocal(textLines, textURL)
                        if (fetchedSegments.isNotEmpty()) {
                            payload["text"] = fetchedSegments.joinToString(joinSeparator)
                            ok.add(docId)
                        }
                    }
                    index.fields.forEach { field ->
                        try {
                            coreAnno.read(field.path)?.let { payload[field.name] = it }
                        } catch (_: PathNotFoundException) {
                        }
                    }
                    index.enrich.forEach { enrichment ->
                        enrichment.from.forEach { type ->
                            auxAnnos[type]?.filter { auxAnno ->
                                enrichment.via.fold(true) { ok, via -> ok && checkVia(coreAnno, auxAnno, via) }
                            }?.forEach { auxAnno ->
                                enrichment.fields.forEach { field ->
                                    try {
                                        auxAnno.read(field.path)?.let { annoValue ->
                                            val value = mutableSetOf<Any>().apply {
                                                if (annoValue is Iterable<*>)
                                                    @Suppress("UNCHECKED_CAST")
                                                    addAll(annoValue as Iterable<Any>)
                                                else
                                                    add(annoValue)
                                            }
                                            payload.merge(field.name, value, ::keepUniqueValues)
                                        }
                                    } catch (_: PathNotFoundException) {
                                        // ignore if any part of path cannot be reached
                                    }
                                }
                            }
                        }
                    }
                    logger.atDebug().addKeyValue("payload", payload).log(docId)

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

    private fun keepUniqueValues(l1: Any, l2: Any): Set<Any> =
        @Suppress("UNCHECKED_CAST")
        (l1 as MutableSet<Any>).union(l2 as Iterable<Any>)

    private fun checkVia(anno1: AnnoRepoSearchResult, anno2: AnnoRepoSearchResult, via: EnrichmentViaConfiguration) =
        via.equality?.let { checkEquality(anno1, anno2, it) } ?: true
                && via.overlap?.let { checkOverlap(anno1, anno2, it) } ?: true

    private fun checkEquality(anno1: AnnoRepoSearchResult, anno2: AnnoRepoSearchResult, path: String) =
        // don't use regular 's1 == s2' because we want this to be false when $path is not found in either anno
        (anno1.read(path)?.let { s1 -> anno2.read(path)?.let { s2 -> s1 == s2 } }) ?: false

    private fun checkOverlap(anno1: AnnoRepoSearchResult, anno2: AnnoRepoSearchResult, textType: String): Boolean {
        val res1 = anno1.withField<Any>(textType, "selector").first()
        val selector1 = res1["selector"] as Map<*, *>
        val start1 = selector1["start"] as Int
        val end1 = selector1["end"] as Int

        val res2 = anno2.withField<Any>(textType, "selector").first()
        val selector2 = res2["selector"] as Map<*, *>
        val start2 = selector2["start"] as Int
        val end2 = selector2["end"] as Int

        val secondAnnoBeforeFirst = end2 < start1
        val secondAnnoAfterFirst = start2 > end1
        val disjoint = secondAnnoBeforeFirst || secondAnnoAfterFirst

        logger.atTrace()
            .addKeyValue("res1", res1)
            .addKeyValue("res2", res2)
            .addKeyValue("secondAnnoBeforeFirst", secondAnnoBeforeFirst)
            .addKeyValue("secondAnnoAfterFirst", secondAnnoAfterFirst)
            .addKeyValue("disjoint", disjoint)
            .log("checkOverlap")

        return !disjoint
    }

    private fun fetchTextLines(project: Project, anno: AnnoRepoSearchResult): List<String> =
        anno.withoutField<String>(project.textType, "selector")
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

        @JvmStatic
        fun fetchTextSegmentsLocal(textLines: List<String>, textURL: String): List<String> {
            val coords = textURL.indexOf("segments/index/") + "segments/index/".length
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
                    if (result.lastIndex == 0) {
                        result[result.lastIndex] = result.last().substring(0, endIndex - startIndex + 1)
                    } else {
                        result[result.lastIndex] = result.last().substring(0, endIndex + 1)
                    }
                    result
                }

                else -> {
                    logger.atWarn().log("Failed to extract coordinates from {}", textURL)
                    emptyList()
                }
            }
        }
    }
}
