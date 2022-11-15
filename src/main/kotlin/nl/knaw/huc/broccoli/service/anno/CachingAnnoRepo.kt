package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.api.WebAnnoPage
import nl.knaw.huc.broccoli.service.anno.FetchingAnnoRepo.TextSelector
import nl.knaw.huc.broccoli.service.cache.LRUCache
import org.slf4j.LoggerFactory

class CachingAnnoRepo(private val delegate: AnnoRepo, capacity: Int = 10) : AnnoRepo {
    private val log = LoggerFactory.getLogger(javaClass)

    private val cachedScanPages = LRUCache<Pair<String, String>, ScanPageResult>(capacity)
    private val cachedResolutions = LRUCache<Pair<String, String>, WebAnnoPage>(capacity)

    override fun getScanAnno(containerName: String, bodyId: String): ScanPageResult {
        val key = Pair(containerName, bodyId)
        val cached = cachedScanPages.get(key)
        if (cached != null) {
            log.info("cache hit for [$key]")
            return cached
        }

        log.info("cache miss for [$key]")
        val value = delegate.getScanAnno(containerName, bodyId)
        cachedScanPages.put(key, value)
        return value
    }

    override fun findByBodyId(containerName: String, bodyId: String): WebAnnoPage {
        val key = Pair(containerName, bodyId)
        val cached = cachedResolutions.get(key)
        if (cached != null) {
            log.info("cache hit for [$key]")
            return cached
        }

        log.info("cache miss for [$key]")
        val value = delegate.findByBodyId(containerName, bodyId)
        cachedResolutions.put(key, value)
        return value
    }

    override fun findOffsetRelativeTo(
        containerName: String,
        source: String,
        selector: TextSelector,
        type: String
    ): Pair<Int, String> {
        log.info("TODO: cache findOffsetRelativeTo")
        return delegate.findOffsetRelativeTo(containerName, source, selector, type)
    }
}