package nl.knaw.huc.broccoli.service

import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader

object ResourceLoader {
    fun asStream(path: String): InputStream? {
        return Thread.currentThread().contextClassLoader.getResourceAsStream(path)
    }

    fun asReader(path: String): Reader? {
        return asStream(path)?.let { InputStreamReader(it) }
    }
}