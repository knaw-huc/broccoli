package nl.knaw.huc.broccoli.service.cache

import org.slf4j.LoggerFactory
import java.util.*

class LRUCache<K, V>(private val capacity: Int = 5) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val cache = HashMap<K, V>()

    // LinkedList chosen over ArrayList for O(1) removeFirst and addLast so eviction stays O(1)
    private val insertionOrder = LinkedList<K>()

    // O(1) amortised over load factor get
    fun get(key: K): V? = cache[key]

    // O(1) amortised over load factor put and eviction
    fun put(key: K, value: V): K? {
        var evictedKey: K? = null
        if (cache.size >= capacity) {
            evictedKey = insertionOrder.removeFirst()
            cache.remove(evictedKey)
            log.info("cache miss: evicted [$evictedKey] to make room for [$key]")
        }
        cache[key] = value
        insertionOrder.addLast(key)
        return evictedKey
    }

    fun clear() = cache.clear()

    fun size() = cache.size

    override fun toString(): String = cache.toString()
}
