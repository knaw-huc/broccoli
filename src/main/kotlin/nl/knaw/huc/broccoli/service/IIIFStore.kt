package nl.knaw.huc.broccoli.service

interface IIIFStore {
    fun getCanvasId(volume: String, opening: Int): String
}
