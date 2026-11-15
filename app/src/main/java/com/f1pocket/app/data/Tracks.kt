package com.f1pocket.app.data

import android.content.Context
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.math.abs

data class Corner(val number: Int, val label: String, val angle: Float, val x: Float, val y: Float)
data class ZonePos(val corner: Int, val meters: Float)
data class TrackZones(val straights: List<Pair<ZonePos, ZonePos>>, val detection: ZonePos?, val activation: ZonePos?)

class TrackGeometry(
    val name: String,
    val year: Int,
    val xs: FloatArray,
    val ys: FloatArray,
    val s1: Int,
    val s2: Int,
    val corners: List<Corner>,
    val rotation: Float,
    val exactSectors: Boolean
)

/** Track maps: traced from the fastest qualifying lap (TracingInsights), cached on the phone. */
object Tracks {
    private lateinit var dir: File
    private var zones: Map<String, TrackZones> = emptyMap()
    private val mem = HashMap<String, TrackGeometry>()

    fun init(context: Context) {
        dir = File(context.filesDir, "tracks").apply { mkdirs() }
        zones = runCatching { loadZones(context) }.getOrDefault(emptyMap())
    }

    private fun loadZones(context: Context): Map<String, TrackZones> {
        val text = context.assets.open("pistas.json").bufferedReader().use { it.readText() }
        val root = Http.json.parseToJsonElement(text) as JsonObject
        fun pos(e: kotlinx.serialization.json.JsonElement?): ZonePos? {
            val c = e.child("curva").int() ?: return null
            return ZonePos(c, e.child("m").dbl()?.toFloat() ?: 0f)
        }
        return root.filterValues { it is JsonObject }.mapValues { (_, v) ->
            TrackZones(
                straights = v.child("straight_mode").arr().mapNotNull { z -> val a = pos(z.child("de")); val b = pos(z.child("ate")); if (a != null && b != null) a to b else null },
                detection = pos(v.path("overtake", "deteccao")),
                activation = pos(v.path("overtake", "ativacao"))
            )
        }
    }

    fun zonesFor(name: String?): TrackZones? = name?.let { n -> zones[n] ?: zones.entries.firstOrNull { it.key.equals(n, true) }?.value }

    private fun file(event: String) = File(dir, "track-" + event.lowercase().replace(Regex("[^a-z0-9]+"), "-") + ".json")

    fun cached(event: String): TrackGeometry? {
        mem[event]?.let { return it }
        val f = file(event)
        if (!f.exists()) return null
        return runCatching { decode(f.readText()) }.getOrNull()?.also { mem[event] = it }
    }

    /** Map for an event ("Australian Grand Prix"), trying this year and the two before. */
    suspend fun get(event: String, year: Int, refresh: Boolean = false): TrackGeometry {
        if (!refresh) cached(event)?.let { return it }
        var lastError: Exception? = null
        for (y in year downTo year - 2) {
            try {
                val g = TI.withEvent(event, y) { build(it, y) }
                if (g.exactSectors || y == year - 2) {
                    file(event).writeText(encode(g))
                    mem[event] = g
                }
                return g
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("no map")
    }

    private suspend fun build(event: String, year: Int): TrackGeometry {
        val c = TI.corners(year, event, "Qualifying")
        val name = c.child("CircuitName").str() ?: event
        val rotation = c.child("Rotation").dbl()?.toFloat() ?: 0f
        val nums = c.child("CornerNumber").arr()
        val cx = c.child("X").arr()
        val cy = c.child("Y").arr()
        val ang = c.child("Angle").arr()
        val letters = c.child("CornerLetter").arr()
        val corners = nums.indices.mapNotNull { i ->
            val n = nums[i].int() ?: return@mapNotNull null
            Corner(n, "$n${letters.getOrNull(i).str() ?: ""}", ang.getOrNull(i).dbl()?.toFloat() ?: 0f, cx.getOrNull(i).dbl()?.toFloat() ?: return@mapNotNull null, cy.getOrNull(i).dbl()?.toFloat() ?: return@mapNotNull null)
        }
        // fastest qualifying lap: real sectors
        val codes = runCatching { TI.drivers(year, event, "Qualifying").map { it.code } }.getOrDefault(emptyList())
        val best = if (codes.isNotEmpty()) TI.bestLap(year, event, "Qualifying", codes) else null
        if (best != null) {
            val tel = TI.tel(year, event, "Qualifying", best.first, best.second.lap)
            val idx = tel.x.indices.filter { !tel.x[it].isNaN() && !tel.y[it].isNaN() }
            if (idx.size > 50) {
                val xs = FloatArray(idx.size) { tel.x[idx[it]] }
                val ys = FloatArray(idx.size) { tel.y[idx[it]] }
                val ts = FloatArray(idx.size) { tel.time[idx[it]] }
                fun near(t: Double): Int = ts.indices.minBy { abs(ts[it] - t) }
                val s1 = near(best.second.s1 ?: 0.0)
                val s2 = near((best.second.s1 ?: 0.0) + (best.second.s2 ?: 0.0))
                return TrackGeometry(name, year, xs, ys, s1, s2, corners, rotation, true)
            }
        }
        // fallback: the outline stored with the corners, sectors split in thirds
        val tx = c.child("TrackX").floats()
        val ty = c.child("TrackY").floats()
        if (tx.size < 50) throw IllegalStateException("no outline")
        return TrackGeometry(name, year, tx, ty, tx.size / 3, tx.size * 2 / 3, corners, rotation, false)
    }

    private fun encode(g: TrackGeometry): String = buildJsonObject {
        put("name", g.name); put("year", g.year); put("s1", g.s1); put("s2", g.s2)
        put("rotation", g.rotation); put("exact", g.exactSectors)
        put("xs", JsonArray(g.xs.map { JsonPrimitive(it) }))
        put("ys", JsonArray(g.ys.map { JsonPrimitive(it) }))
        put("corners", JsonArray(g.corners.map { k ->
            buildJsonObject { put("n", k.number); put("l", k.label); put("a", k.angle); put("x", k.x); put("y", k.y) }
        }))
    }.toString()

    private fun decode(s: String): TrackGeometry {
        val j = Http.json.parseToJsonElement(s)
        return TrackGeometry(
            name = j.child("name").str() ?: "",
            year = j.child("year").int() ?: 0,
            xs = j.child("xs").floats(),
            ys = j.child("ys").floats(),
            s1 = j.child("s1").int() ?: 0,
            s2 = j.child("s2").int() ?: 0,
            corners = j.child("corners").arr().map {
                Corner(it.child("n").int() ?: 0, it.child("l").str() ?: "", it.child("a").dbl()?.toFloat() ?: 0f, it.child("x").dbl()?.toFloat() ?: 0f, it.child("y").dbl()?.toFloat() ?: 0f)
            },
            rotation = j.child("rotation").dbl()?.toFloat() ?: 0f,
            exactSectors = j.child("exact").bool()
        )
    }
}
