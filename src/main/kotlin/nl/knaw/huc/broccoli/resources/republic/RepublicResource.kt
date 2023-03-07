package nl.knaw.huc.broccoli.resources.republic

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import nl.knaw.huc.broccoli.api.*
import nl.knaw.huc.broccoli.api.Constants.isIn
import nl.knaw.huc.broccoli.api.Constants.isNotIn
import nl.knaw.huc.broccoli.api.ResourcePaths.REPUBLIC
import nl.knaw.huc.broccoli.config.RepublicConfiguration
import nl.knaw.huc.broccoli.service.IIIFStore
import nl.knaw.huc.broccoli.service.anno.AnnoRepo
import nl.knaw.huc.broccoli.service.anno.BodyIdSearchResult
import nl.knaw.huc.broccoli.service.anno.TextSelector
import org.slf4j.LoggerFactory
import java.net.URI
import javax.ws.rs.*
import javax.ws.rs.client.Client
import javax.ws.rs.core.GenericType
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path(REPUBLIC)
@Produces(MediaType.APPLICATION_JSON)
class RepublicResource(
    config: RepublicConfiguration,
    private val volumeMapper: RepublicVolumeMapper,
    private val annoRepo: AnnoRepo,
    private val iiifStore: IIIFStore,
    private val client: Client,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val objectMapper = ObjectMapper()

    private val defaultURI =
        URI.create("$REPUBLIC/v3/volumes/${config.defaultVolume}/openings/${config.defaultOpening}")

    @GET
    @Path("v3")
    fun redirectToDefaultVolumeAndOpening(): Response = Response.seeOther(defaultURI).build()

    @GET
    @Path("v3/volumes/{volumeId}/openings/{openingNr}")
    fun getVolumeOpening(
        @PathParam("volumeId") volumeId: String,
        @PathParam("openingNr") openingNr: Int,
        @QueryParam("includeType") includeTypesSet: Set<String>,
        @QueryParam("includeTypes") includeTypesString: String?,
        @QueryParam("excludeType") excludeTypesSet: Set<String>,
        @QueryParam("excludeTypes") excludeTypesString: String?,
    ): Response {
        log.info("volumeId: $volumeId, openingNr: $openingNr")

        if (openingNr < 1) {
            throw BadRequestException("Path parameter 'openingNr' must be >= 1")
        }

        val typesToInclude = gatherTypes(includeTypesSet, includeTypesString)
        log.info("‣include: $includeTypesSet ∪ ${includeTypesString ?: "∅"} = $typesToInclude (${typesToInclude.size})")

        val typesToExclude = gatherTypes(excludeTypesSet, excludeTypesString)
        log.info("‣exclude: $excludeTypesSet ∪ ${excludeTypesString ?: "∅"} = $typesToExclude (${typesToExclude.size})")

        if (typesToInclude.isNotEmpty() && typesToExclude.isNotEmpty()) {
            throw BadRequestException("Use either 'includeType(s)' or 'excludeType(s)', but not both")
        }

        val volume = volumeMapper.byVolumeName(volumeId)
        val bodyId = volumeMapper.buildBodyId(volume, openingNr)
        val anno = annoRepo.findByBodyId(bodyId)

        val resultText = anno.withoutField<String>("Text", "selector")
            .also { if (it.size > 1) log.warn("multiple Text without selector: $it") }
            .first()
            .let { fetchTextLines(it["source"] as String) }

        val resultAnno = anno.withField<Any>("Text", "selector")
            .also { if (it.size > 1) log.warn("multiple Text with selector: $it") }
            .first()
            .let {
                @Suppress("UNCHECKED_CAST")
                val selector = it["selector"] as Map<String, Any>
                val sourceUrl = it["source"] as String
                val start = selector["start"] as Int
                val end = selector["end"] as Int
                val bodyTypes =
                    if (typesToInclude.isNotEmpty()) {
                        isIn(typesToInclude)
                    } else if (typesToExclude.isNotEmpty()) {
                        isNotIn(typesToExclude)
                    } else /* both are empty */ {
                        isNotIn(setOf("Line", "Page", "RepublicParagraph", "TextRegion", "Scan"))
                    }

                annoRepo.fetchOverlap(sourceUrl, start, end, bodyTypes)
            }

        return Response.ok(
            mapOf(
                "type" to "AnnoTextResult",
                "request" to mapOf(
                    "tier0" to volumeId,
                    "tier1" to openingNr
                ),
                "anno" to resultAnno,
                "text" to mapOf(
                    "location" to mapOf(
                        "relativeTo" to "TODO",
                        "start" to TextMarker(-1, -1, -1),
                        "end" to TextMarker(-1, -1, -1)
                    ),
                    "lines" to resultText
                ),
                "iiif" to mapOf(
                    "manifest" to iiifStore.manifest(volume.imageset),
                    "canvasIds" to listOf(iiifStore.getCanvasId(volume.name, openingNr))
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

    @GET
    @Path("v3/bodies/{bodyId}")
    // Both.../v3/bodies/urn:republic:session-1728-06-19-ordinaris-num-1-resolution-11?relativeTo=Session
    // and /v3/bodies/urn:republic:NL-HaNA_1.01.02_3783_0331 (Either NO ?relativeTo or MUST BE: relativeTo=Volume)
    fun getBodyIdRelativeTo(
        @PathParam("bodyId") bodyId: String, // could be resolutionId, sessionId, ...
        @QueryParam("relativeTo") @DefaultValue("Origin") relativeTo: String, // e.g., "Scan", "Session" -> Enum? Generic?
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

        var markers = TextMarkers(start, end)
        log.info("markers (absolute): $markers")

        val location = mapOf(
            "location" to if (relativeTo == "Origin") {
                mapOf("type" to "Origin", "id" to "")
            } else {
                val (offset, offsetId) = annoRepo.findOffsetRelativeTo(
                    textSegmentsSource,
                    selector,
                    relativeTo
                )
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
                    "manifest" to iiifStore.manifest(volumeMapper.byBodyId(bodyId).imageset),
                    "canvasIds" to extractCanvasIds(annoPage)
                )
            )
        ).build()
    }

    private fun extractCanvasIds(annoPage: BodyIdSearchResult) = annoPage.targetField<String>("Canvas", "source")

    private fun getTextLines(annoPage: BodyIdSearchResult): List<String> {
        val textTargets = annoPage.target<String>("Text").filter { !it.containsKey("selector") }
        if (textTargets.size > 1) {
            log.warn("Multiple text targets (without selector) found, arbitrarily using the first: $textTargets")
        }
        return textTargets[0]["source"]?.let { fetchTextLines(it) }.orEmpty()
    }

    private fun fetchTextLines(textSourceUrl: String): List<String> {
        log.info("Fetching relevant text segments: $textSourceUrl")
        val startTime = System.currentTimeMillis()
        val resp = client.target(textSourceUrl).request().get()
        val result = resp.readEntity(object : GenericType<List<String>>() {})
        log.info("fetching took ${System.currentTimeMillis() - startTime} ms")
        return result
    }

    data class TextMarkers(val start: TextMarker, val end: TextMarker) {
        fun relativeTo(offset: Int): TextMarkers {
            return TextMarkers(start.relativeTo(offset), end.relativeTo(offset))
        }
    }


}
