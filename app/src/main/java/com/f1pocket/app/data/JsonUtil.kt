package com.f1pocket.app.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Locale

fun JsonElement?.child(key: String): JsonElement? = (this as? JsonObject)?.get(key)

fun JsonElement?.path(vararg keys: String): JsonElement? {
    var e: JsonElement? = this
    for (k in keys) e = e.child(k)
    return e
}

fun JsonElement?.str(): String? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    val c = p.contentOrNull ?: return null
    return if (c == "None" || c == "null" || c == "NaN") null else c
}

fun JsonElement?.int(): Int? = str()?.toDoubleOrNull()?.toInt()
fun JsonElement?.long(): Long? = str()?.toDoubleOrNull()?.toLong()
fun JsonElement?.dbl(): Double? = str()?.let { s ->
    when {
        s.equals("true", true) -> 1.0
        s.equals("false", true) -> 0.0
        else -> s.toDoubleOrNull()
    }
}
fun JsonElement?.bool(): Boolean = str().equals("true", true)
fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray) ?: emptyList()

/** A list from either a JSON array or an object keyed by "0", "1", ... (the F1 feed uses both). */
fun JsonElement?.items(): List<JsonElement> = when (this) {
    is JsonArray -> this
    is JsonObject -> entries.sortedBy { it.key.toIntOrNull() ?: Int.MAX_VALUE }.map { it.value }
    else -> emptyList()
}

fun JsonElement?.fields(): Map<String, JsonElement> = (this as? JsonObject) ?: emptyMap()

fun JsonElement?.floats(): FloatArray {
    val a = arr()
    return FloatArray(a.size) { a[it].dbl()?.toFloat() ?: Float.NaN }
}

/** Deep merge used by the live timing feed, which sends partial updates. */
fun mergeJson(base: JsonElement?, patch: JsonElement): JsonElement = when {
    patch is JsonObject && base is JsonObject -> {
        val m = LinkedHashMap<String, JsonElement>(base)
        for ((k, v) in patch) {
            if (k == "_deleted") {
                v.items().forEach { d -> d.str()?.let { m.remove(it) } }
                continue
            }
            m[k] = mergeJson(m[k], v)
        }
        JsonObject(m)
    }
    patch is JsonObject && base is JsonArray -> {
        val l = base.toMutableList()
        for ((k, v) in patch) {
            val i = k.toIntOrNull() ?: continue
            while (l.size <= i) l.add(JsonNull)
            l[i] = mergeJson(l[i], v)
        }
        JsonArray(l)
    }
    else -> patch
}

fun parseInstant(s: String?): Instant? {
    if (s.isNullOrBlank()) return null
    return try {
        OffsetDateTime.parse(s).toInstant()
    } catch (_: Exception) {
        try {
            Instant.parse(s)
        } catch (_: Exception) {
            try {
                LocalDateTime.parse(s.take(23)).toInstant(ZoneOffset.UTC)
            } catch (_: Exception) {
                null
            }
        }
    }
}

/** "1:19.813" or "79.813" -> seconds. */
fun parseLapTime(s: String?): Double? {
    if (s.isNullOrBlank()) return null
    val p = s.trim().split(":")
    return try {
        when (p.size) {
            1 -> p[0].toDouble()
            2 -> p[0].toInt() * 60 + p[1].toDouble()
            3 -> p[0].toInt() * 3600 + p[1].toInt() * 60 + p[2].toDouble()
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

fun fmtLap(sec: Double?): String {
    if (sec == null || sec.isNaN()) return "—"
    val m = (sec / 60).toInt()
    val s = sec - m * 60
    return if (m > 0) String.format(Locale.US, "%d:%06.3f", m, s) else String.format(Locale.US, "%.3f", s)
}

fun fmtGap(sec: Double?): String = if (sec == null) "" else String.format(Locale.US, "+%.3f", sec)
