package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.api.WebAnnoPage
import nl.knaw.huc.broccoli.config.RepublicVolume
import nl.knaw.huc.broccoli.service.anno.FetchingAnnoRepo.TextSelector
import nl.knaw.huc.broccoli.service.cache.LRUCache
import org.slf4j.LoggerFactory

class CachingAnnoRepo(private val delegate: AnnoRepo, capacity: Int = 10) : AnnoRepo {
    private val log = LoggerFactory.getLogger(javaClass)

    private val cachedScanPages = LRUCache<Pair<String, Int>, ScanPageResult>(capacity)
    private val cachedAnnoDetails = LRUCache<Triple<String, Int, String>, BodyIdResult>(capacity)
    private val cachedResolutions = LRUCache<Pair<String, String>, WebAnnoPage>(capacity)

    override fun getScanAnno(volume: RepublicVolume, opening: Int): ScanPageResult {
        val key = Pair(volume.name, opening)
        val cached = cachedScanPages.get(key)
        if (cached != null) {
            log.info("cache hit for [$key]")
            return cached
        }

        log.info("cache miss for [$key]")
        val value = delegate.getScanAnno(volume, opening)
        cachedScanPages.put(key, value)
        return value
    }

    override fun getRepublicBodyId(volume: RepublicVolume, opening: Int, bodyId: String): BodyIdResult {
        val key = Triple(volume.name, opening, bodyId)
        val cached = cachedAnnoDetails.get(key)
        if (cached != null) {
            log.info("cache hit for [$key]")
            return cached
        }

        log.info("cache miss for [$key]")
        val value = delegate.getRepublicBodyId(volume, opening, bodyId)
        cachedAnnoDetails.put(key, value)
        return value
    }

    override fun getBodyId(volume: String, bodyId: String): WebAnnoPage {
        val key = Pair(volume, bodyId)
        val cached = cachedResolutions.get(key)
        if (cached != null) {
            log.info("cache hit for [$key]")
            return cached
        }

        log.info("cache miss for [$key]")
        val value = delegate.getBodyId(volume, bodyId)
        cachedResolutions.put(key, value)
        return value
    }

    override fun findOffsetRelativeTo(
        volume: String,
        source: String,
        selector: TextSelector,
        type: String
    ): Pair<Int, String> {
        log.info("TODO: cache findOffsetRelativeTo")
        return delegate.findOffsetRelativeTo(volume, source, selector, type)
    }
}