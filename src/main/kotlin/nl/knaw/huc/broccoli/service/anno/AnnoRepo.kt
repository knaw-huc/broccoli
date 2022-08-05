package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.config.RepublicVolume

interface AnnoRepo {
    fun getScanAnno(volume: RepublicVolume, opening: Int): ScanPageResult
}

data class ScanPageResult(
    val anno: List<Map<String,Any>>,
    val text: List<String>
)
