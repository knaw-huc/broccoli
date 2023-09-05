package nl.knaw.huc.broccoli.service

import nl.knaw.huc.broccoli.api.IndexTaskIndex

class IndexManager() {
    fun startIndexTask(task: IndexTask): IndexTask {
        IndexTaskIndex[task.id] = task
        Thread(task).start()
        return task
    }

    fun getIndexTask(id: String): IndexTask? {
        return IndexTaskIndex[id]
    }
}
