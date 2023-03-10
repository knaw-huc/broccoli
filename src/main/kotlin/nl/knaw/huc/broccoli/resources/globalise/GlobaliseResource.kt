package nl.knaw.huc.broccoli.resources.globalise

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import nl.knaw.huc.broccoli.api.Constants.isIn
import nl.knaw.huc.broccoli.api.Constants.isNotIn
import nl.knaw.huc.broccoli.api.ResourcePaths.GLOBALISE
import nl.knaw.huc.broccoli.api.TextMarker
import nl.knaw.huc.broccoli.config.GlobaliseConfiguration
import nl.knaw.huc.broccoli.resources.republic.RepublicResource
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.anno.AnnoRepoSearchResult
import nl.knaw.huc.broccoli.service.anno.TextSelector
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

    private val apiKey = config.textRepo.apiKey

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
    ): Response {
        log.info("documentId: $documentId, openingNr: $openingNr")

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
        val anno = annoRepo.findByBodyId(bodyId)
        log.info("Got anno: $anno")

        // Text part: fetch designated lines from TextRepo
        val resultText = anno.withoutField<String>("Text", "selector")
            .also { if (it.size > 1) log.warn("multiple Text without selector: $it") }
            .first()
            .let { fetchTextLines(it["source"] as String) }

        // Annotation part: overlapping annotations dependent on requested bodyTypes
        val resultAnno: List<Map<String, Any>> = anno.withField<Any>("Text", "selector")
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

        // IIIF part: manifest and canvas-ids
        val manifestName = "manifest-${doc.manifest ?: doc.name}.json"
        val manifest = "https://broccoli.tt.di.huc.knaw.nl/mock/globalise/$manifestName"
        val canvasId = "${GLOBALISE_NS}:canvas:$scanName"

        return Response.ok(
            mapOf(
                "type" to "AnnoTextResult",
                "request" to mapOf(
                    "tier0" to documentId,
                    "tier1" to openingNr
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

    @GET
    @Path("v0/bodies/{bodyId}")
    fun getBodyIdRelativeTo(
        @PathParam("bodyId") bodyId: String,
        @QueryParam("relativeTo") @DefaultValue("Origin") relativeTo: String,
    ): Response {
        val annoPage = annoRepo.findByBodyId(bodyId)
        val textTargets = annoPage.target<Any>("Text")

        val textTargetWithoutSelector = textTargets.find { it["selector"] == null }
            ?: throw WebApplicationException("annotation body $bodyId has no 'Text' target without selector")
        log.info("textTargetWithoutSelector: $textTargetWithoutSelector")
        val textLinesSource = textTargetWithoutSelector["source"] as String
        val textLines = fetchTextLines(textLinesSource)

        val textTargetWithSelector = textTargets.find { it["selector"] != null }
            ?: throw WebApplicationException("annotation body $bodyId has no 'Text' target with selector")
        log.info("textTargetWithSelector: $textTargetWithSelector")
        val textSegmentsSource = textTargetWithSelector["source"] as String
        @Suppress("UNCHECKED_CAST") val selector =
            TextSelector(textTargetWithSelector["selector"] as Map<String, Any>)

        val beginCharOffset = selector.beginCharOffset() ?: 0
        val start = TextMarker(selector.start(), beginCharOffset, textLines[0].length)
        log.info("start: $start")

        val lengthOfLastLine = textLines.last().length
        val endCharOffset = selector.endCharOffset() ?: (lengthOfLastLine - 1)
        val end = TextMarker(selector.end(), endCharOffset, lengthOfLastLine)
        log.info("end: $end")

        var markers = RepublicResource.TextMarkers(start, end) // <- note how this uses 'Republic' for now
        log.info("markers (absolute): $markers")

        val location = mapOf(
            "location" to if (relativeTo == "Origin") {
                mapOf("type" to "Origin", "id" to "")
            } else {
                val (offset, offsetId) = annoRepo.findOffsetRelativeTo(textSegmentsSource, selector, relativeTo)
                markers = markers.relativeTo(offset)
                log.info("markers (relative to $offsetId): $markers")
                mapOf("type" to relativeTo, "bodyId" to offsetId)
            },
            "start" to markers.start,
            "end" to markers.end
        )

        return Response.ok(
            mapOf(
                "type" to "AnnoTextResult",
                "request" to mapOf(
                    "bodyId" to bodyId,
                    "relativeTo" to relativeTo
                ),
                "anno" to annoPage.items(),
                "text" to mapOf(
                    "location" to location,
                    "lines" to getTextLines(annoPage),
                ),
                "iiif" to mapOf(
                    "manifest" to "discuss.where.to.get.this.manifest.from",
                    "canvasIds" to annoPage.targetField<String>("Canvas", "source"),
                )
            )
        ).build()
    }

    private fun getTextLines(annoPage: AnnoRepoSearchResult): List<String> {
        val textTargets = annoPage.target<String>("Text").filter { !it.containsKey("selector") }
        if (textTargets.size > 1) {
            log.warn("Multiple text targets (without selector) found, arbitrarily using the first: $textTargets")
        }
        return textTargets[0]["source"]?.let { fetchTextLines(it) }.orEmpty()
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

    private fun fetchTextLines(textSourceUrl: String): List<String> {
        log.info("GET {}", textSourceUrl)
        var builder = client.target(textSourceUrl)
            .request()
        if (apiKey != null) {
            log.info("with apiKey {}", apiKey)
            builder = builder.header(AUTHORIZATION, "Basic $apiKey")
        }
        return builder
            .get()
            .readEntity(object : GenericType<List<String>>() {})
    }

}
