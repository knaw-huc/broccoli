package nl.knaw.huc.broccoli.service

abstract class IndexTask(
    val id: String
) : Runnable {
    // todo: migrate index 'work' to here
}
