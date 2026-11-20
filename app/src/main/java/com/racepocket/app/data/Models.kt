package com.racepocket.app.data

import java.time.Instant
import java.time.LocalDate

enum class SessionKind(val minutes: Long) {
    FP1(60), FP2(60), FP3(60), SPRINT_QUALI(45), SPRINT(60), QUALI(60), RACE(120)
}

data class SessionTime(val kind: SessionKind, val start: Instant) {
    val end: Instant get() = start.plusSeconds(kind.minutes * 60)
}

data class Race(
    val season: Int,
    val round: Int,
    val name: String,
    val circuitId: String,
    val circuitName: String,
    val locality: String,
    val country: String,
    val lat: Double,
    val lon: Double,
    val date: LocalDate,
    val raceStart: Instant?,
    val sessions: List<SessionTime>
) {
    val flag: String get() = Flags.country(country)
    val isSprint: Boolean get() = sessions.any { it.kind == SessionKind.SPRINT }
    val firstDay: LocalDate get() = sessions.firstOrNull()?.start?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() ?: date.minusDays(2)
    val lastDay: LocalDate get() = raceStart?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate() ?: date
    fun isFinished(now: Instant = Instant.now()): Boolean = (raceStart ?: date.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()).plusSeconds(3 * 3600).isBefore(now)
}

data class DriverRef(
    val id: String,
    val code: String,
    val first: String,
    val last: String,
    val nationality: String,
    val number: String
) {
    val name: String get() = "$first $last"
    val flag: String get() = Flags.nationality(nationality)
}

data class TeamRef(val id: String, val name: String, val nationality: String) {
    val flag: String get() = Flags.nationality(nationality)
    val color: Long get() = Static.teamColor(id)
}

data class DriverStanding(val pos: String, val points: String, val wins: Int, val driver: DriverRef, val team: TeamRef?)
data class TeamStanding(val pos: String, val points: String, val wins: Int, val team: TeamRef)

data class ResultRow(
    val pos: String,
    val posText: String,
    val driver: DriverRef,
    val team: TeamRef,
    val grid: Int,
    val laps: Int,
    val status: String,
    val time: String?,
    val points: String,
    val fastestRank: Int?,
    val fastestTime: String?,
    val fastestLap: Int?
) {
    val classified: Boolean get() = posText.toIntOrNull() != null
    val position: Int? get() = pos.toIntOrNull()
}

data class QualiRow(val pos: String, val driver: DriverRef, val team: TeamRef, val q1: String?, val q2: String?, val q3: String?) {
    val best: String? get() = q3 ?: q2 ?: q1
}

data class PitRow(val driverId: String, val lap: Int, val stop: Int, val duration: String)

data class HistEntry(val season: Int, val round: Int, val raceName: String, val driver: DriverRef, val team: TeamRef, val time: String?, val laps: Int)

data class CircuitHistory(
    val winners: List<HistEntry>,
    val poles: List<HistEntry>,
    val fastest: List<HistEntry>
) {
    val gpsHeld: Int get() = winners.size
    val firstGp: Int? get() = winners.minOfOrNull { it.season }
    val lastWinner: HistEntry? get() = winners.maxByOrNull { it.season * 100 + it.round }
    val lastPole: HistEntry? get() = poles.maxByOrNull { it.season * 100 + it.round }
    val lastFastest: HistEntry? get() = fastest.maxByOrNull { it.season * 100 + it.round }
    val raceRecord: HistEntry? get() = fastest.filter { parseLapTime(it.time) != null }.minByOrNull { parseLapTime(it.time)!! }
    val qualiRecord: HistEntry? get() = poles.filter { parseLapTime(it.time) != null }.minByOrNull { parseLapTime(it.time)!! }
    fun top(list: List<HistEntry>, n: Int = 5): List<Pair<HistEntry, Int>> =
        list.groupBy { it.driver.id }.map { (_, v) -> v.maxBy { it.season } to v.size }.sortedByDescending { it.second }.take(n)
}

data class Leader(val driver: DriverRef, val team: TeamRef?, val count: Int)
data class SeasonLeaders(val wins: List<Leader>, val poles: List<Leader>, val podiums: List<Leader>, val fastest: List<Leader>)

data class StatLine(
    val points: Double? = null,
    val wins: Int? = null,
    val poles: Int? = null,
    val podiums: Int? = null,
    val fastest: Int? = null,
    val starts: Int? = null,
    val avgFinish: Double? = null,
    val avgGrid: Double? = null,
    val dnfs: Int? = null,
    val titles: Int? = null,
    val bestFinish: Int? = null,
    val finishByRound: Map<Int, Int> = emptyMap(),
    val qualiByRound: Map<Int, Int> = emptyMap(),
    val form: List<String> = emptyList()
)
