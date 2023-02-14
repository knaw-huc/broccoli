package nl.knaw.huc.broccoli.service.text

import org.slf4j.LoggerFactory

class TextRepo(val uri: String, val apiKey: String?) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun canResolve(sourceUrl: String): Boolean {
        log.info("canResolve: textRepo.uri=$uri, sourceUrl=$sourceUrl")
        return sourceUrl.startsWith(uri)
    }
}
