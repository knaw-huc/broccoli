package nl.knaw.huc.broccoli.service.anno

import nl.knaw.huc.broccoli.api.WebAnnoPage
import nl.knaw.huc.broccoli.service.anno.FetchingAnnoRepo.TextSelector

interface AnnoRepo {
    fun findByBodyId(containerName: String, bodyId: String): WebAnnoPage

    fun fetchOverlappingAnnotations(
        containerName: String, source: String, start: Int, end: Int
    ): List<Map<String, Any>>

    fun findOffsetRelativeTo(
        containerName: String, source: String, selector: TextSelector, type: String
    ): Pair<Int, String>
}
