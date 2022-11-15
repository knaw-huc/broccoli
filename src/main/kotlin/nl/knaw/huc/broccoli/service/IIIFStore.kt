package nl.knaw.huc.broccoli.service

import java.net.URI

interface IIIFStore {
    fun manifest(imageset: String): URI
    fun getCanvasId(volume: String, opening: Int): String
}
