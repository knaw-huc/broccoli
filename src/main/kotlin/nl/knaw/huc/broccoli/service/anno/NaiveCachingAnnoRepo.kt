package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.config.RepublicVolume

class CachingAnnoRepo(private val delegate: AnnoRepo) : AnnoRepo {
    private val cachedScanPages = HashMap<Pair<String, Int>, ScanPageResult>()

    private val cachedAnnoDetails = HashMap<Triple<String,Int,String>, BodyIdResult>()

    override fun getScanAnno(volume: RepublicVolume, opening: Int): ScanPageResult {
        val key = Pair(volume.name, opening)
        if (!cachedScanPages.contains(key)) {
            cachedScanPages[key] = delegate.getScanAnno(volume, opening)
        }
        return cachedScanPages[key]!!
    }

    override fun getBodyId(volume: RepublicVolume, opening: Int, bodyId: String): BodyIdResult {
        val key = Triple(volume.name, opening, bodyId)
        if (!cachedAnnoDetails.containsKey(key)) {
            cachedAnnoDetails[key] = delegate.getBodyId(volume, opening, bodyId)
        }
        return cachedAnnoDetails[key]!!
    }
}