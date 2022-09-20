package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.api.TextMarker
import nl.knaw.huc.broccoli.api.WebAnnoPage
import nl.knaw.huc.broccoli.config.RepublicVolume

interface AnnoRepo {
    fun getScanAnno(volume: RepublicVolume, opening: Int): ScanPageResult

    fun getRepublicBodyId(volume: RepublicVolume, opening: Int, bodyId: String): BodyIdResult

    fun getBodyId(volume: String, bodyId: String): WebAnnoPage
}

data class ScanPageResult(
    val anno: List<Map<String, Any>>,
    val text: List<String>
)

data class BodyIdResult(
    val start: TextMarker,
    val end: TextMarker,
    val text: List<String>
)