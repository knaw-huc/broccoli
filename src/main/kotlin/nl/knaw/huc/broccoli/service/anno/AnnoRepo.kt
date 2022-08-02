package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.config.RepublicVolume

interface AnnoRepo {
    fun getScanAnno(volume: RepublicVolume, opening: Int): Map<String, Any>
}