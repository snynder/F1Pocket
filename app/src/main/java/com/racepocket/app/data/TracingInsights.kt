package com.racepocket.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonElement
import java.net.URLEncoder

/**
 * TracingInsights open dataset on GitHub: per-lap telemetry with X/Y, corners and lap times for every session.
 * It is static files, so it also works during live sessions (when OpenF1 blocks free access).
 * Folder names are the official event names, e.g. "Australian Grand Prix" / "Qualifying".
 */
object TI {
    val SESSIONS = listOf("Qualifying", "Race", "Sprint", "Sprint Qualifying", "Practice 1", "Practice 2", "Practice 3")

    /**
     * Folder names the dataset may use for an event. Jolpica and the dataset sometimes disagree:
     * Brazil is "São Paulo Grand Prix" there, and in 2026 the Sepang (Malaysia) round is filed as "Bahrain Grand Prix".
     */
    fun candidates(event: String, year: Int): List<String> {
        val extra = when {
            event.contains("Brazil", true) -> listOf("São Paulo Grand Prix")
            event.contains("Paulo", true) -> listOf("Brazilian Grand Prix")
            event.contains("Malaysia", true) && year == 2026 -> listOf("Bahrain Grand Prix")
            event == "Mexican Grand Prix" -> listOf("Mexico City Grand Prix")
            event == "Mexico City Grand Prix" -> listOf("Mexican Grand Prix")
            else -> emptyList()
        }
        return (listOf(event) + extra).distinct()
    }

    /** Runs [block] with the first folder name that works for this event. */
    suspend fun <T> withEvent(event: String, year: Int, block: suspend (String) -> T): T {
        var last: Exception? = null
        for (name in candidates(event, year)) {
            try {
                return block(name)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IllegalStateException("no data")
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    fun base(year: Int, event: String, session: String) =
        "https://raw.githubusercontent.com/TracingInsights/$year/main/${enc(event)}/${enc(session)}"

    data class Driver(val code: String, val team: String, val number: String, val name: String, val color: String, val photo: String?)
    data class TLap(val lap: Int, val time: Double, val s1: Double?, val s2: Double?, val s3: Double?, val compound: String?, val deleted: Boolean)
    class Tel(
        val distance: FloatArray, val time: FloatArray, val speed: FloatArray, val throttle: FloatArray,
        val brake: FloatArray, val gear: FloatArray, val x: FloatArray, val y: FloatArray
    )

    suspend fun drivers(year: Int, event: String, session: String): List<Driver> =
        Http.getJson("${base(year, event, session)}/drivers.json").child("drivers").arr().map {
            Driver(
                code = it.child("driver").str() ?: "",
                team = it.child("team").str() ?: "",
                number = it.child("dn").str() ?: "",
                name = listOfNotNull(it.child("fn").str(), it.child("ln").str()).joinToString(" "),
                color = it.child("tc").str() ?: "8A929C",
                photo = it.child("url").str()
            )
        }

    suspend fun laps(year: Int, event: String, session: String, code: String): List<TLap> {
        val j = Http.getJson("${base(year, event, session)}/${enc(code)}/laptimes.json")
        val t = j.child("time").arr()
        val lap = j.child("lap").arr()
        val s1 = j.child("s1").arr()
        val s2 = j.child("s2").arr()
        val s3 = j.child("s3").arr()
        val comp = j.child("compound").arr()
        val del = j.child("del").arr()
        return t.indices.mapNotNull { i ->
            val time = t[i].dbl() ?: return@mapNotNull null
            TLap(lap.getOrNull(i).int() ?: (i + 1), time, s1.getOrNull(i).dbl(), s2.getOrNull(i).dbl(), s3.getOrNull(i).dbl(), comp.getOrNull(i).str(), del.getOrNull(i).bool())
        }
    }

    suspend fun tel(year: Int, event: String, session: String, code: String, lap: Int): Tel {
        val raw = Http.getJson("${base(year, event, session)}/${enc(code)}/${lap}_tel.json")
        val j: JsonElement? = raw.child("tel") ?: raw
        return Tel(
            j.child("distance").floats(), j.child("time").floats(), j.child("speed").floats(), j.child("throttle").floats(),
            j.child("brake").floats(), j.child("gear").floats(), j.child("x").floats(), j.child("y").floats()
        )
    }

    suspend fun corners(year: Int, event: String, session: String = "Qualifying"): JsonElement =
        Http.getJson("${base(year, event, session)}/corners.json")

    /** Fastest valid lap among the given drivers. */
    suspend fun bestLap(year: Int, event: String, session: String, codes: List<String>): Pair<String, TLap>? = coroutineScope {
        val sem = Semaphore(6)
        codes.map { code ->
            async {
                sem.withPermit {
                    runCatching { laps(year, event, session, code) }.getOrDefault(emptyList())
                        .filter { !it.deleted && it.s1 != null && it.s2 != null }
                        .minByOrNull { it.time }?.let { code to it }
                }
            }
        }.awaitAll().filterNotNull().minByOrNull { it.second.time }
    }
}
