package nl.knaw.huc.broccoli.jobs

import io.dropwizard.jobs.Job
import io.dropwizard.jobs.annotations.Every
import nl.knaw.huc.broccoli.api.IndexTaskIndex
import org.quartz.JobExecutionContext
import org.slf4j.LoggerFactory

@Every(value = "1m", jobName = "purgeExpiredTasks")
class ExpiredTasksCleanupJob : Job() {
    private val log = LoggerFactory.getLogger(javaClass)

    init {
        log.info("ExpiredTasksCleanupJob created")
    }

    override fun doJob(context: JobExecutionContext?) {
        IndexTaskIndex.purgeExpiredTasks()
    }

}
