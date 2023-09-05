package nl.knaw.huc.broccoli.api

import nl.knaw.huc.broccoli.service.IndexTask
import org.slf4j.LoggerFactory

object IndexTaskIndex {
    private val log = LoggerFactory.getLogger(javaClass)

    private val index: MutableMap<String, IndexTask> = mutableMapOf()

    operator fun get(id: String): IndexTask? = index[id]

    operator fun set(id: String, task: IndexTask) {
        index[id] = task
    }

    fun purgeExpiredTasks() {
        // collect expired taskIds prior to removing, to avoid removal concurrently to traversing 'index'
        val expiredTaskIds = index.entries
            .asSequence()
            // .filter { only those that are done }
            // .filter { only those that are expired }
            .map { it.key }
            .toList()

        if (expiredTaskIds.isNotEmpty()) {
            log.info("purging expired tasks: $expiredTaskIds")
            expiredTaskIds.forEach { index.remove(it) }
        }
    }
}
