package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.api.WebAnnoPage
import nl.knaw.huc.broccoli.config.RepublicVolume
import nl.knaw.huc.broccoli.service.anno.FetchingAnnoRepo.TextMarkers
import nl.knaw.huc.broccoli.service.anno.FetchingAnnoRepo.TextSelector

interface AnnoRepo {
    fun getScanAnno(volume: RepublicVolume, opening: Int): ScanPageResult

    fun getRepublicBodyId(volume: RepublicVolume, opening: Int, bodyId: String): BodyIdResult

    fun findByBodyId(volume: String, bodyId: String): WebAnnoPage

    fun findOffsetRelativeTo(volume: String, source: String, selector: TextSelector, type: String): Pair<Int, String>
}

data class ScanPageResult(val anno: List<Map<String, Any>>, val text: List<String>)

data class BodyIdResult(val markers: TextMarkers, val text: List<String>)