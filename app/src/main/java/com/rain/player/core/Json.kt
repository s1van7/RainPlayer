package com.rain.player.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** 宽松的 JSON 解析工具：服务端字段缺失、类型漂移都不会崩。 */
object J {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun parse(text: String): JsonElement? = runCatching { json.parseToJsonElement(text) }.getOrNull()

    fun obj(text: String): JsonObject? = parse(text) as? JsonObject
}

fun JsonElement?.asObj(): JsonObject? = this as? JsonObject
fun JsonElement?.asArr(): JsonArray? = this as? JsonArray

fun JsonObject?.str(key: String): String? {
    val p = this?.get(key) as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    val raw = p.content
    return raw.takeIf { it.isNotEmpty() && it != "null" }
}

fun JsonObject?.strOr(key: String, def: String): String = str(key) ?: def

fun JsonObject?.int(key: String): Int? {
    val p = this?.get(key) as? JsonPrimitive ?: return null
    return p.intOrNull ?: p.content.toIntOrNull()
}

fun JsonObject?.long(key: String): Long? {
    val p = this?.get(key) as? JsonPrimitive ?: return null
    return p.content.toLongOrNull()
}

fun JsonObject?.dbl(key: String): Double? {
    val p = this?.get(key) as? JsonPrimitive ?: return null
    return p.doubleOrNull ?: p.content.toDoubleOrNull()
}

fun JsonObject?.bool(key: String): Boolean? {
    val p = this?.get(key) as? JsonPrimitive ?: return null
    return p.booleanOrNull ?: when (p.content.lowercase()) {
        "1", "true", "yes" -> true
        "0", "false", "no" -> false
        else -> null
    }
}

fun JsonObject?.arr(key: String): JsonArray? = (this?.get(key) as? JsonArray)

fun JsonObject?.obj(key: String): JsonObject? = (this?.get(key) as? JsonObject)

fun JsonObject?.stringList(key: String): List<String> =
    arr(key)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()

/** ticks(100ns) -> 毫秒 */
fun ticksToMs(ticks: Long?): Long = (ticks ?: 0L) / 10_000L

fun msToTicks(ms: Long): Long = ms * 10_000L
