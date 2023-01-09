package nl.knaw.huc.broccoli.resources.globalise

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jayway.jsonpath.JsonPath
import nl.knaw.huc.broccoli.api.Constants.isIn
import nl.knaw.huc.broccoli.api.Constants.isNotIn
import nl.knaw.huc.broccoli.api.ResourcePaths.GLOBALISE
import nl.knaw.huc.broccoli.config.GlobaliseConfiguration
import nl.knaw.huc.broccoli.service.ResourceLoader
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.anno.BodyIdSearchResult
import org.slf4j.LoggerFactory
import java.net.URI
import javax.ws.rs.*
import javax.ws.rs.client.Client
import javax.ws.rs.core.GenericType
import javax.ws.rs.core.HttpHeaders.AUTHORIZATION
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path(GLOBALISE)
@Produces(MediaType.APPLICATION_JSON)
class GlobaliseResource(
    private val config: GlobaliseConfiguration,
    private val annoRepo: AnnoRepo,
    private val client: Client,
) {
    companion object {
        private const val GLOBALISE_NS = "urn:globalise"
        private const val LATEST_API_VERSION = 0
        private val latestVersionURI = URI.create("$GLOBALISE/v$LATEST_API_VERSION")
    }

    private val log = LoggerFactory.getLogger(javaClass)

    private val objectMapper = ObjectMapper()

    private val defaultURI =
        URI.create("$GLOBALISE/v0/documents/${config.defaultDocument}/openings/${config.defaultOpening}")

    @GET
    fun redirectToLatestVersion(): Response = Response.seeOther(latestVersionURI).build()

    @GET
    @Path("v0")
    fun redirectToDefaultDocumentAndOpening(): Response = Response.seeOther(defaultURI).build()

    @GET
    @Path("v0/documents/{documentId}/openings/{openingNr}")
    fun getDocumentOpening(
        @PathParam("documentId") documentId: String,
        @PathParam("openingNr") openingNr: Int,
        @QueryParam("includeType") includeTypesSet: Set<String>,
        @QueryParam("includeTypes") includeTypesString: String?,
        @QueryParam("excludeType") excludeTypesSet: Set<String>,
        @QueryParam("excludeTypes") excludeTypesString: String?,
        @QueryParam("mock") @DefaultValue("false") mock: Boolean,
    ): Response {
        log.info("documentId: $documentId, openingNr: $openingNr, mock: $mock")

        if (openingNr < 1) {
            throw BadRequestException("Path parameter 'openingNr' must be >= 1")
        }

        val typesToInclude = gatherTypes(includeTypesSet, includeTypesString)
        val typesToExclude = gatherTypes(excludeTypesSet, excludeTypesString)

        if (typesToInclude.isNotEmpty() && typesToExclude.isNotEmpty()) {
            throw BadRequestException("Use either 'includeType(s)' or 'excludeType(s)', but not both")
        }

        val doc = config.documents.find { it.name == documentId }
            ?: throw NotFoundException("Document [$documentId] not found in globalise configuration")
        log.info("doc: $doc")

        val scanNr = "%04d".format(openingNr)
        val scanName = "NL-HaNA_${config.archiefNr}_${doc.invNr}_${scanNr}"

        val bodyId = "${GLOBALISE_NS}:${scanName}"
        val anno = if (mock) {
            BodyIdSearchResult(JsonPath.parse("[]"))
        } else {
            annoRepo.findByBodyId(bodyId)
        }
        log.info("Got anno: $anno")

        // Text part: fetch designated lines from TextRepo
        val resultText = if (mock) {
            listOf("aap", "noot", "mies")
        } else {
            anno.withoutField<String>("Text", "selector")
                .also { if (it.size > 1) log.warn("multiple Text without selector: $it") }
                .first()
                .let { fetchTextLines(it["source"] as String, config.textRepo.apiKey) }
        }

        // Annotation part: overlapping annotations dependent on requested bodyTypes
        val resultAnno: List<Map<String, Any>> = if (mock) {
            JsonPath.parse(ResourceLoader.asText("mock/globalise/anno-sample.json")).read("\$")
        } else {
            anno.withField<Any>("Text", "selector")
                .also { if (it.size > 1) log.warn("multiple Text with selector: $it") }
                .first()
                .let {
                    val source = it["source"] as String

                    @Suppress("UNCHECKED_CAST")
                    val selector = it["selector"] as Map<String, Any>

                    val requestBodyTypes =
                        if (typesToInclude.isNotEmpty()) {
                            isIn(typesToInclude)
                        } else if (typesToExclude.isNotEmpty()) {
                            isNotIn(typesToExclude)
                        } else { /* nothing specified, use some sensible default */
                            isNotIn(setOf("px:TextLine", "px:Page", "px:TextRegion"))
                        }

                    annoRepo.fetchOverlap(
                        source = source,
                        start = selector["start"] as Int,
                        end = selector["end"] as Int,
                        bodyTypes = requestBodyTypes
                    )
                }
        }

        // IIIF part: manifest and canvas-ids
        val manifestName = "manifest-${doc.manifest ?: doc.name}.json"
        val manifest = "https://broccoli.tt.di.huc.knaw.nl/mock/globalise/$manifestName"
        val canvasId = "${GLOBALISE_NS}:canvas:$scanName"

        return Response.ok(
            mapOf(
                "type" to "AnnoTextResult",
                "request" to mapOf(
                    "documentId" to documentId,
                    "openingNr" to openingNr
                ),
                "anno" to resultAnno,
                "text" to mapOf(
                    "lines" to resultText
                ),
                "iiif" to mapOf(
                    "manifest" to manifest,
                    "canvasIds" to listOf(canvasId)
                )
            )
        ).build()
    }

    private fun gatherTypes(typesAsSet: Set<String>, typesAsString: String?) =
        if (typesAsString == null) {
            typesAsSet
        } else if (typesAsString.startsWith('[')) {
            typesAsSet.union(objectMapper.readValue<Set<String>>(typesAsString))
        } else {
            typesAsSet.union(
                typesAsString
                    .removeSurrounding("\"")
                    .split(',')
                    .map { it.trim() })
        }

    private fun fetchTextLines(textSourceUrl: String, textRepoApiKey: String?): List<String> {
        log.info("GET {}", textSourceUrl)
        var builder = client.target(textSourceUrl)
            .request()
        if (textRepoApiKey != null) {
            log.info("with apiKey {}", textRepoApiKey)
            builder = builder.header(AUTHORIZATION, "Basic $textRepoApiKey")
        }
        return builder
            .get()
            .readEntity(object : GenericType<List<String>>() {})
    }

}
