package nl.knaw.huc.broccoli.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.ws.rs.core.Response

fun <K, V> List<Map<K, V>>.groupByKey(): Map<K, V> = flatMap { it.asSequence() }.associate { it.key to it.value }

fun Response.readEntityAsJsonString(): String = readEntity(String::class.java) ?: ""

fun Map<String, Any>.toJsonString(): String = jacksonObjectMapper().writeValueAsString(this)
