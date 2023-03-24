package nl.knaw.huc.broccoli.resources.projects

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jayway.jsonpath.Configuration.defaultConfiguration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option.DEFAULT_PATH_LEAF_TO_NULL
import io.swagger.v3.oas.annotations.Operation
import nl.knaw.huc.broccoli.api.Constants
import nl.knaw.huc.broccoli.api.ResourcePaths.PROJECTS
import nl.knaw.huc.broccoli.api.TextMarker
import nl.knaw.huc.broccoli.core.Project
import nl.knaw.huc.broccoli.service.anno.AnnoRepoSearchResult
import nl.knaw.huc.broccoli.service.anno.AnnoSearchResultInterpreter
import nl.knaw.huc.broccoli.service.anno.TextSelector
import nl.knaw.huc.broccoli.service.text.TextRepo
import org.slf4j.LoggerFactory
import javax.ws.rs.*
import javax.ws.rs.client.Client
import javax.ws.rs.client.Entity
import javax.ws.rs.core.*
import javax.ws.rs.core.Response.Status.CREATED

@Path(PROJECTS)
@Produces(MediaType.APPLICATION_JSON)
class ProjectsResource(
    private val projects: Map<String, Project>,
    private val client: Client,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val objectMapper = ObjectMapper()
    private val jsonParser = JsonPath.using(defaultConfiguration().addOptions(DEFAULT_PATH_LEAF_TO_NULL))

    init {
        log.debug("init: projects=$projects, client=$client")
    }

    @GET
    @Path("")
    @Operation(summary = "Get configured projects")
    fun listProjects(): Set<String> = projects.keys

    @GET
    @Path("{projectId}/search/{key}")
    fun searchIndex(
        @PathParam("key") key: String,
        @QueryParam("snip") @DefaultValue("false") snip: Boolean,
        @QueryParam("frag") @DefaultValue("none") frag: String,
        @QueryParam("size") @DefaultValue("100") size: Int,
        @QueryParam("num") @DefaultValue("10") num: Int
    ): Response {
        val snipsAndOffsets = if (snip) "return_snippets_and_offsets" else "return_offsets"
        val query = """
            {
              "_source": false,
              "query": {
                "match_phrase_prefix": {
                  "text": {
                    "query": "$key"
                  }
                }
              },
              "highlight": {
                "fields": {
                  "text": {
                    "type": "experimental",
                    "fragmenter": "$frag",
                    "fragment_size": $size,
                    "number_of_fragments": $num,
                    "options": {
                      "$snipsAndOffsets": true
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val response = client.target("http://localhost:9200").path("brinta")        // TODO: host config
            .path("_search")
            .request()
            .post(Entity.json(query))
        log.info("response: $response")
        val json = response.readEntityAsJsonString()
        log.info("data: $json")
        val hits = jsonParser.parse(json).read<Any>("$.hits.hits[0].highlight.text")
        return Response.ok(hits).build()
    }

    @GET
    @Path("{projectId}/index/{tierValue}/{typeToIndex}")
    fun createIndex(
        @PathParam("projectId") projectId: String,      // e.g., "republic"
        @PathParam("tierValue") tierValue: String,      // e.g., "1728"
        @PathParam("typeToIndex") typeToIndex: String   // e.g., "Resolution"
    ): Response {
        val project = getProject(projectId)
        log.info("creating index: $project")

        val topTierName = project.tiers[0].name
        val topTier = project.annoRepo.findByTiers(
            bodyType = topTierName.capitalize(),
            tiers = listOf(Pair(topTierName, tierValue))
        )
        val textTarget = topTier.withField<Any>("Text", "source").first()
        val source = textTarget["source"] as String
        val selector = textTarget["selector"] as Map<*, *>
        val start = selector["start"] as Int
        val end = selector["end"] as Int

        project.annoRepo.streamOverlap(source, start, end, Constants.isIn(setOf(typeToIndex)))
            .map(::AnnoRepoSearchResult)
            .forEach {
                val id = it.bodyId()
                val date = it.bodyMetadata()["sessionDate"] as String    // TODO: make into param: Republic specific!
                val text = it.withoutField<String>("Text", "selector")
                    .first()
                    .let { textTarget ->
                        val textURL = textTarget["source"] as String
                        client.target(textURL).request().get().readEntity(String::class.java)
                    }
                val indexPayload = mapOf(
                    "date" to date,
                    "text" to text
                )

                log.info("Indexing $id, date=$date, size=${text.length}")
                val resp = client.target("http://localhost:9200").path("brinta")    // TODO: host config
                    .path("_doc").path(id)
                    .request()
                    .put(Entity.json(indexPayload))

                if (resp.status != CREATED.statusCode) {
                    log.warn("Failed to index $id: ${resp.readEntityAsJsonString()}")
                }

                resp.close() // !!! manual close, if not reading entity, or connection pool will be exhausted !!!
            }

        return Response.ok().build()
    }

    private fun String.capitalize(): String = replaceFirstChar(Char::uppercase)

    private fun Response.readEntityAsJsonString(): String = readEntity(String::class.java) ?: ""

    @GET
    @Path("{projectId}/{bodyType}/{tiers: .*}")
    fun findByTiers(
        @PathParam("projectId") projectId: String,
        @PathParam("bodyType") bodyType: String,
        @PathParam("tiers") tierParams: String,
        @QueryParam("includeResults") @DefaultValue("bodyId") includeResultsParam: String,
    ): Response {
        val result = mutableMapOf<String, Any>()

        val project = getProject(projectId)

        val interestedIn = parseIncludeResults(setOf("anno", "bodyId"), includeResultsParam)

        val formalTiers = project.tiers
        val actualTiers = tierParams.split('/').filter { it.isNotBlank() }
        if (actualTiers.size != formalTiers.size) {
            throw BadRequestException("Must specify value for all formal tiers: $formalTiers, got $actualTiers instead")
        }

        result["request"] = mapOf(
            "projectId" to projectId,
            "bodyType" to bodyType,
            "tiers" to actualTiers,
            "includeResults" to interestedIn
        )

        val queryTiers = mutableListOf<Pair<String, Any>>()
        formalTiers.forEachIndexed { index, formalTier ->
            queryTiers.add(Pair(formalTier.name, formalTier.type.toAnnoRepoQuery(actualTiers[index])))
        }

        val searchResult = project.annoRepo.findByTiers(bodyType, queryTiers)

        if (interestedIn.contains("bodyId")) {
            result["bodyId"] = searchResult.bodyId()
        }

        if (interestedIn.contains("anno")) {
            result["anno"] = searchResult.items()
        }

        return Response.ok(result)
            // TODO: add a Link header? -> which rel to use?
            //  https://www.iana.org/assignments/link-relations/link-relations.xhtml
            // .link(location, "canonical")
            .build()
    }

    @GET
    @Path("{projectId}/{bodyId}")
    @Operation(summary = "Get project's annotations by bodyId")
    fun getProjectBodyId(
        @PathParam("projectId") projectId: String,
        @PathParam("bodyId") bodyId: String,
        @QueryParam("includeResults") includeResultsParam: String?,
        @QueryParam("overlapTypes") overlapTypesParam: String?,
        @QueryParam("relativeTo") @DefaultValue("Origin") relativeTo: String,
    ): Response {
        log.info(
            "project=$projectId, bodyId=$bodyId, " +
                    "includeResults=$includeResultsParam, overlapTypes=$overlapTypesParam, relativeTo=$relativeTo"
        )

        val before = System.currentTimeMillis()

        val project = getProject(projectId)
        val annoRepo = project.annoRepo
        val textRepo = project.textRepo

        val interestedIn = parseIncludeResults(setOf("anno", "text", "iiif"), includeResultsParam)
        val overlapTypes = parseOverlapTypes(overlapTypesParam)

        val annoTimings = mutableMapOf<String, Any>()
        val textTimings = mutableMapOf<String, Any>()
        val selfTimings = mutableMapOf<String, Any>()
        val profile = mapOf("anno" to annoTimings, "text" to textTimings, "self" to selfTimings)

        val request = mutableMapOf(
            "projectId" to projectId,
            "bodyId" to bodyId,
            "includeResults" to interestedIn,
        )
        if (overlapTypes.isNotEmpty()) {
            request["overlapTypes"] = overlapTypes
        }
        request["relativeTo"] = relativeTo

        val result = mutableMapOf<String, Any>(
            "profile" to profile,
            "request" to request
        )

        val searchResult = timeExecution(
            { annoRepo.findByBodyId(bodyId) },
            { timeSpent -> annoTimings["findByBodyId"] = timeSpent }
        )

        if (interestedIn.contains("anno")) {
            result["anno"] = if (overlapTypes.isEmpty()) {
                searchResult.items()
            } else {
                searchResult.withField<Any>("Text", "selector")
                    .also { if (it.size > 1) log.warn("multiple Text with selector: $it") }
                    .first()
                    .let {
                        val selector = it["selector"] as Map<*, *>
                        val sourceUrl = it["source"] as String
                        val start = selector["start"] as Int
                        val end = selector["end"] as Int
                        val bodyTypes = Constants.isIn(overlapTypes)
                        timeExecution(
                            { annoRepo.fetchOverlap(sourceUrl, start, end, bodyTypes) },
                            { timeSpent -> annoTimings["fetchOverlap"] = timeSpent }
                        )
                    }
            }
        }

        if (interestedIn.contains("text")) {
            val textInterpreter = AnnoSearchResultInterpreter(searchResult)

            val textLines = timeExecution(
                { fetchTextLines(textRepo, textInterpreter.findTextSource()) },
                { timeSpent -> textTimings["fetchTextLines"] = timeSpent }
            )
            val textResult = mutableMapOf<String, Any>("lines" to textLines)

            val selector = textInterpreter.findSelector()
            val segmentsSource = textInterpreter.findSegmentsSource()

            if (interestedIn.contains("anno") && relativeTo != "Origin") {
                val offset = timeExecution(
                    { annoRepo.findOffsetRelativeTo(segmentsSource, selector, relativeTo) },
                    { timeSpent -> textTimings["findOffsetRelativeTo"] = timeSpent })

                val relocatedAnnotations = mutableListOf<Map<String, Any>>()
                (result["anno"] as List<*>).forEach { anno ->
                    if (anno is Map<*, *>) {
                        val annoBodyId = extractBodyId(anno)
                        val annoSelector = extractTextSelector(anno)

                        if (annoBodyId != null && annoSelector != null) {
                            val start = TextMarker(annoSelector.start(), annoSelector.beginCharOffset())
                            val end = TextMarker(annoSelector.end(), annoSelector.endCharOffset())
                            val markers = TextMarkers(start, end).relativeTo(offset.value)
                            relocatedAnnotations.add(
                                mapOf(
                                    "bodyId" to annoBodyId,
                                    "start" to markers.start,
                                    "end" to markers.end
                                )
                            )
                        }
                    }

                    if (relocatedAnnotations.isNotEmpty()) {
                        textResult["locations"] = mapOf(
                            "relativeTo" to mapOf("type" to relativeTo, "bodyId" to offset.id),
                            "annotations" to relocatedAnnotations
                        )
                    }
                }
            }

            result["text"] = textResult
        }

        if (interestedIn.contains("iiif")) {
            val manifest = searchResult.withField<Any>("Text", "selector")
                .also { if (it.size > 1) log.warn("multiple Text with selector: $it") }
                .first()
                .let {
                    val selector = it["selector"] as Map<*, *>
                    val sourceUrl = it["source"] as String
                    val start = selector["start"] as Int
                    val end = selector["end"] as Int
                    val tier0 = project.tiers[0].name.capitalize()//.replaceFirstChar(Char::uppercase)
                    val bodyTypes = Constants.isIn(setOf(tier0))
                    timeExecution(
                        { annoRepo.fetchOverlap(sourceUrl, start, end, bodyTypes) },
                        { timeSpent -> annoTimings["fetchManifest"] = timeSpent }
                    )
                }
                .firstNotNullOfOrNull { extractManifest(it) }

            result["iiif"] = mapOf(
                "manifest" to manifest,
                "canvasIds" to searchResult.targetField<String>("Canvas", "source")
            )
        }

        val after = System.currentTimeMillis()
        selfTimings["total"] = after - before

        return Response.ok(result).build()
    }

    private fun extractManifest(anno: Map<*, *>): String? {
        if (anno.containsKey("body")) {
            val body = anno["body"]
            if (body is Map<*, *> && body.containsKey("metadata")) {
                val metadata = body["metadata"]
                if (metadata is Map<*, *> && metadata.containsKey("manifest")) {
                    return metadata["manifest"] as String
                }
            }
        }
        return null
    }

    private fun extractBodyId(anno: Map<*, *>): String? {
        if (anno.containsKey("body")) {
            val body = anno["body"]
            if (body is Map<*, *> && body.containsKey("id")) {
                return body["id"] as String
            }
        }
        return null
    }

    private fun extractTextSelector(anno: Map<*, *>): TextSelector? {
        if (anno.containsKey("target")) {
            (anno["target"] as List<*>).forEach { target ->
                if (target is Map<*, *> && target["type"] == "Text" && target.containsKey("selector")) {
                    @Suppress("UNCHECKED_CAST")
                    val selector = target["selector"] as Map<String, Any>
                    return TextSelector(selector)
                }
            }
        }
        return null
    }

    private fun <R> timeExecution(workToBeTimed: () -> R, storeTimeSpent: (Long) -> Unit): R {
        val before = System.currentTimeMillis()
        val result = workToBeTimed()
        storeTimeSpent(System.currentTimeMillis() - before)
        return result
    }

    private fun getProject(projectId: String): Project {
        return projects[projectId]
            ?: throw NotFoundException("Unknown project: $projectId. See /projects for known projects")
    }

    private fun parseIncludeResults(all: Set<String>, includeResultString: String?): Set<String> {
        if (includeResultString == null) {
            return all
        }

        val requested = if (includeResultString.startsWith('[')) {
            objectMapper.readValue(includeResultString)
        } else {
            includeResultString
                .removeSurrounding("\"")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }

        val includes = all.intersect(requested)

        val undefined = requested.minus(all)
        if (undefined.isNotEmpty()) {
            throw BadRequestException("Undefined include: $undefined not in $all")
        }

        return includes
    }

    private fun parseOverlapTypes(overlapTypes: String?): Set<String> =
        if (overlapTypes == null) {
            emptySet()
        } else if (overlapTypes.startsWith('[')) {
            objectMapper.readValue(overlapTypes)
        } else {
            overlapTypes
                .removeSurrounding("\"")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .toSet()
        }

    private fun fetchTextLines(textRepo: TextRepo, textSourceUrl: String): List<String> {
        log.info("GET {}", textSourceUrl)

        var builder = client.target(textSourceUrl).request()

        with(textRepo) {
            if (apiKey != null && canResolve(textSourceUrl)) {
                log.info("with apiKey {}", apiKey)
                builder = builder.header(HttpHeaders.AUTHORIZATION, "Basic $apiKey")
            }
        }

        val resp = builder.get()

        if (resp.status == Response.Status.UNAUTHORIZED.statusCode) {
            log.warn("Auth failed fetching $textSourceUrl")
            throw ClientErrorException("Need credentials for $textSourceUrl", Response.Status.UNAUTHORIZED)
        }

        return resp.readEntity(object : GenericType<List<String>>() {})
    }

    data class TextMarkers(val start: TextMarker, val end: TextMarker) {
        fun relativeTo(offset: Int): TextMarkers {
            return TextMarkers(start.relativeTo(offset), end.relativeTo(offset))
        }
    }

}
