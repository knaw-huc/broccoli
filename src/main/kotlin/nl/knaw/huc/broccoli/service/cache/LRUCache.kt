package nl.knaw.huc.broccoli.service.cache

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

class LRUCache<K, V>(private val capacity: Int = 5) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val cache = ConcurrentHashMap<K & Any, V & Any>()

    // LinkedList chosen over ArrayList for O(1) removeFirst and addLast so eviction stays O(1)
    private val insertionOrder = LinkedBlockingDeque<K & Any>()

    // O(1) amortised over load factor get
    fun get(key: K): V? = cache[key]

    // O(1) amortised over load factor put and eviction
    fun put(key: K & Any, value: V & Any): K? {
        var evictedKey: K? = null
        if (cache.size >= capacity) {
            evictedKey = insertionOrder.removeFirst()
            cache.remove(evictedKey)
            log.info("cache miss: evicted [$evictedKey] to make room for [$key]")
        }
        if (insertionOrder.offer(key)) {
            cache[key] = value
        }
        return evictedKey
    }

    fun clear() = cache.clear()

    fun size() = cache.size

    override fun toString(): String = cache.toString()
}
