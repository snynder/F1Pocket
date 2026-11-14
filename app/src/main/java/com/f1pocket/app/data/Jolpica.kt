package com.f1pocket.app.data

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import java.time.LocalDate

/** Jolpica (Ergast-compatible) API: every season since 1950. */
object Jolpica {
    private const val B = "https://api.jolpi.ca/ergast/f1"

    private suspend fun mr(path: String): JsonElement = Http.getJson("$B/$path").child("MRData") ?: JsonNull

    private fun sep(path: String) = if (path.contains('?')) '&' else '?'

    /** Pages through RaceTable results; a race split across pages is merged back. */
    private suspend fun racesPaged(path: String, key: String): List<JsonObject> {
        val out = LinkedHashMap<String, JsonObject>()
        var offset = 0
        while (true) {
            val m = mr("$path${sep(path)}limit=100&offset=$offset")
            val total = m.child("total").int() ?: 0
            for (race in m.path("RaceTable", "Races").arr()) {
                val o = race as? JsonObject ?: continue
                val k = "${o["season"].str()}-${o["round"].str()}"
                val prev = out[k]
                out[k] = if (prev == null) o else JsonObject(prev + (key to JsonArray(prev[key].arr() + o[key].arr())))
            }
            offset += 100
            if (offset >= total || offset > 3000) break
        }
        return out.values.toList()
    }

    fun driverRef(e: JsonElement?): DriverRef {
        val last = e.child("familyName").str() ?: ""
        return DriverRef(
            id = e.child("driverId").str() ?: "",
            code = e.child("code").str() ?: last.take(3).uppercase(),
            first = e.child("givenName").str() ?: "",
            last = last,
            nationality = e.child("nationality").str() ?: "",
            number = e.child("permanentNumber").str() ?: ""
        )
    }

    fun teamRef(e: JsonElement?) = TeamRef(
        id = e.child("constructorId").str() ?: "",
        name = e.child("name").str() ?: "",
        nationality = e.child("nationality").str() ?: ""
    )

    private fun instantOf(e: JsonElement?): java.time.Instant? {
        val d = e.child("date").str() ?: return null
        val t = e.child("time").str() ?: return null
        return parseInstant("${d}T$t")
    }

    fun parseRace(r: JsonElement): Race {
        val c = r.child("Circuit")
        val loc = c.child("Location")
        val sessions = buildList {
            instantOf(r.child("FirstPractice"))?.let { add(SessionTime(SessionKind.FP1, it)) }
            instantOf(r.child("SecondPractice"))?.let { add(SessionTime(SessionKind.FP2, it)) }
            instantOf(r.child("ThirdPractice"))?.let { add(SessionTime(SessionKind.FP3, it)) }
            instantOf(r.child("SprintQualifying") ?: r.child("SprintShootout"))?.let { add(SessionTime(SessionKind.SPRINT_QUALI, it)) }
            instantOf(r.child("Sprint"))?.let { add(SessionTime(SessionKind.SPRINT, it)) }
            instantOf(r.child("Qualifying"))?.let { add(SessionTime(SessionKind.QUALI, it)) }
            instantOf(r)?.let { add(SessionTime(SessionKind.RACE, it)) }
        }.sortedBy { it.start }
        return Race(
            season = r.child("season").int() ?: 0,
            round = r.child("round").int() ?: 0,
            name = r.child("raceName").str() ?: "",
            circuitId = c.child("circuitId").str() ?: "",
            circuitName = c.child("circuitName").str() ?: "",
            locality = loc.child("locality").str() ?: "",
            country = loc.child("country").str() ?: "",
            lat = loc.child("lat").dbl() ?: 0.0,
            lon = loc.child("long").dbl() ?: 0.0,
            date = runCatching { LocalDate.parse(r.child("date").str()) }.getOrDefault(LocalDate.now()),
            raceStart = instantOf(r),
            sessions = sessions
        )
    }

    fun resultRow(x: JsonElement) = ResultRow(
        pos = x.child("position").str() ?: "",
        posText = x.child("positionText").str() ?: "",
        driver = driverRef(x.child("Driver")),
        team = teamRef(x.child("Constructor")),
        grid = x.child("grid").int() ?: 0,
        laps = x.child("laps").int() ?: 0,
        status = x.child("status").str() ?: "",
        time = x.path("Time", "time").str(),
        points = x.child("points").str() ?: "0",
        fastestRank = x.path("FastestLap", "rank").int(),
        fastestTime = x.path("FastestLap", "Time", "time").str(),
        fastestLap = x.path("FastestLap", "lap").int()
    )

    private fun qualiRow(x: JsonElement) = QualiRow(
        pos = x.child("position").str() ?: "",
        driver = driverRef(x.child("Driver")),
        team = teamRef(x.child("Constructor")),
        q1 = x.child("Q1").str()?.takeIf { it.isNotBlank() },
        q2 = x.child("Q2").str()?.takeIf { it.isNotBlank() },
        q3 = x.child("Q3").str()?.takeIf { it.isNotBlank() }
    )

    suspend fun calendar(year: Int): List<Race> =
        mr("$year.json?limit=100").path("RaceTable", "Races").arr().map { parseRace(it) }

    private suspend fun standingsList(year: Int, round: Int?, kind: String): JsonElement? =
        mr(if (round == null) "$year/$kind.json?limit=100" else "$year/$round/$kind.json?limit=100")
            .path("StandingsTable", "StandingsLists").arr().firstOrNull()

    /** Positions after the round before the latest one, to show places gained or lost. Id -> position. */
    suspend fun previousPositions(year: Int, teams: Boolean): Map<String, Int> {
        val kind = if (teams) "constructorStandings" else "driverStandings"
        val round = standingsList(year, null, kind).child("round").int() ?: return emptyMap()
        if (round <= 1) return emptyMap()
        return if (teams) constructorStandings(year, round - 1).mapNotNull { s -> s.pos.toIntOrNull()?.let { s.team.id to it } }.toMap()
        else driverStandings(year, round - 1).mapNotNull { s -> s.pos.toIntOrNull()?.let { s.driver.id to it } }.toMap()
    }

    suspend fun driverStandings(year: Int, round: Int? = null): List<DriverStanding> =
        standingsList(year, round, "driverStandings")
            .child("DriverStandings").arr().map {
                DriverStanding(
                    pos = it.child("position").str() ?: it.child("positionText").str() ?: "-",
                    points = it.child("points").str() ?: "0",
                    wins = it.child("wins").int() ?: 0,
                    driver = driverRef(it.child("Driver")),
                    team = it.child("Constructors").arr().lastOrNull()?.let { c -> teamRef(c) }
                )
            }

    suspend fun constructorStandings(year: Int, round: Int? = null): List<TeamStanding> =
        standingsList(year, round, "constructorStandings")
            .child("ConstructorStandings").arr().map {
                TeamStanding(
                    pos = it.child("position").str() ?: it.child("positionText").str() ?: "-",
                    points = it.child("points").str() ?: "0",
                    wins = it.child("wins").int() ?: 0,
                    team = teamRef(it.child("Constructor"))
                )
            }

    suspend fun raceResults(year: Int, round: Int): Pair<Race?, List<ResultRow>> {
        val r = mr("$year/$round/results.json?limit=100").path("RaceTable", "Races").arr().firstOrNull()
            ?: return null to emptyList()
        return parseRace(r) to r.child("Results").arr().map { resultRow(it) }
    }

    suspend fun sprintResults(year: Int, round: Int): List<ResultRow> =
        mr("$year/$round/sprint.json?limit=100").path("RaceTable", "Races").arr().firstOrNull()
            .child("SprintResults").arr().map { resultRow(it) }

    suspend fun qualifying(year: Int, round: Int): List<QualiRow> =
        mr("$year/$round/qualifying.json?limit=100").path("RaceTable", "Races").arr().firstOrNull()
            .child("QualifyingResults").arr().map { qualiRow(it) }

    suspend fun pitStops(year: Int, round: Int): List<PitRow> =
        mr("$year/$round/pitstops.json?limit=100").path("RaceTable", "Races").arr().firstOrNull()
            .child("PitStops").arr().map {
                PitRow(it.child("driverId").str() ?: "", it.child("lap").int() ?: 0, it.child("stop").int() ?: 0, it.child("duration").str() ?: "")
            }

    /** Every race result of a season (paged). */
    suspend fun seasonResults(year: Int): List<Pair<Race, List<ResultRow>>> =
        racesPaged("$year/results.json", "Results").map { parseRace(it) to it["Results"].arr().map { r -> resultRow(r) } }

    suspend fun seasonSprints(year: Int): List<Pair<Race, List<ResultRow>>> =
        racesPaged("$year/sprint.json", "SprintResults").map { parseRace(it) to it["SprintResults"].arr().map { r -> resultRow(r) } }

    suspend fun seasonLeaders(year: Int): SeasonLeaders = coroutineScope {
        val res = async { seasonResults(year) }
        val poles = async { racesPaged("$year/qualifying/1.json", "QualifyingResults") }
        val all = res.await().flatMap { it.second }
        fun rank(rows: List<ResultRow>): List<Leader> =
            rows.groupBy { it.driver.id }.map { (_, v) -> Leader(v.first().driver, v.last().team, v.size) }
                .sortedByDescending { it.count }.take(3)
        val poleRows = poles.await().mapNotNull { it["QualifyingResults"].arr().firstOrNull() }.map { qualiRow(it) }
        SeasonLeaders(
            wins = rank(all.filter { it.pos == "1" }),
            poles = poleRows.groupBy { it.driver.id }.map { (_, v) -> Leader(v.first().driver, v.last().team, v.size) }
                .sortedByDescending { it.count }.take(3),
            podiums = rank(all.filter { (it.position ?: 99) <= 3 }),
            fastest = rank(all.filter { it.fastestRank == 1 })
        )
    }

    suspend fun circuitHistory(id: String): CircuitHistory = coroutineScope {
        val w = async { racesPaged("circuits/$id/results/1.json", "Results") }
        val p = async { racesPaged("circuits/$id/qualifying/1.json", "QualifyingResults") }
        val f = async { runCatching { racesPaged("circuits/$id/fastest/1/results.json", "Results") }.getOrDefault(emptyList()) }
        fun entry(race: JsonObject, x: JsonElement, time: String?) = HistEntry(
            season = race["season"].int() ?: 0, round = race["round"].int() ?: 0, raceName = race["raceName"].str() ?: "",
            driver = driverRef(x.child("Driver")), team = teamRef(x.child("Constructor")), time = time, laps = x.child("laps").int() ?: 0
        )
        CircuitHistory(
            winners = w.await().mapNotNull { r -> r["Results"].arr().firstOrNull()?.let { entry(r, it, it.path("Time", "time").str()) } },
            poles = p.await().mapNotNull { r -> r["QualifyingResults"].arr().firstOrNull()?.let { x -> entry(r, x, x.child("Q3").str() ?: x.child("Q2").str() ?: x.child("Q1").str()) } },
            fastest = f.await().mapNotNull { r -> r["Results"].arr().firstOrNull()?.let { entry(r, it, it.path("FastestLap", "Time", "time").str()) } }
        )
    }

    /** Winner of every round of a season. */
    suspend fun seasonWinners(year: Int): Map<Int, ResultRow> =
        racesPaged("$year/results/1.json", "Results").mapNotNull { r -> r["Results"].arr().firstOrNull()?.let { (r["round"].int() ?: 0) to resultRow(it) } }.toMap()

    suspend fun allCircuits(): List<Triple<String, String, String>> =
        mr("circuits.json?limit=100").path("CircuitTable", "Circuits").arr().map {
            Triple(it.child("circuitId").str() ?: "", it.child("circuitName").str() ?: "", it.path("Location", "country").str() ?: "")
        }.sortedBy { it.second }

    suspend fun circuitInfo(id: String): Triple<String, String, String>? =
        mr("circuits/$id.json").path("CircuitTable", "Circuits").arr().firstOrNull()?.let {
            Triple(it.child("circuitName").str() ?: id, it.path("Location", "locality").str() ?: "", it.path("Location", "country").str() ?: "")
        }

    suspend fun seasonDrivers(year: Int): List<DriverRef> =
        mr("$year/drivers.json?limit=100").path("DriverTable", "Drivers").arr().map { driverRef(it) }

    suspend fun seasonTeams(year: Int): List<TeamRef> =
        mr("$year/constructors.json?limit=100").path("ConstructorTable", "Constructors").arr().map { teamRef(it) }

    private suspend fun total(path: String): Int = mr("$path${sep(path)}limit=1").child("total").int() ?: 0

    // ---------- comparisons ----------

    private fun statsFromRaces(races: List<JsonObject>, key: String, qualiRaces: List<JsonObject>, teamMode: Boolean): StatLine {
        val rows = races.flatMap { r -> r[key].arr().map { (r["round"].int() ?: 0) to resultRow(it) } }
        val finishes = rows.mapNotNull { (_, x) -> x.posText.toIntOrNull() }
        val grids = rows.mapNotNull { (_, x) -> x.grid.takeIf { it > 0 } }
        val q = qualiRaces.flatMap { r -> r["QualifyingResults"].arr().map { (r["round"].int() ?: 0) to qualiRow(it) } }
        return StatLine(
            points = rows.sumOf { it.second.points.toDoubleOrNull() ?: 0.0 },
            wins = rows.count { it.second.pos == "1" },
            podiums = rows.count { (it.second.position ?: 99) <= 3 },
            fastest = rows.count { it.second.fastestRank == 1 },
            poles = q.count { it.second.pos == "1" },
            starts = if (teamMode) races.size else rows.size,
            avgFinish = finishes.takeIf { it.isNotEmpty() }?.average(),
            avgGrid = grids.takeIf { it.isNotEmpty() }?.average(),
            dnfs = rows.count { !it.second.classified },
            bestFinish = finishes.minOrNull(),
            finishByRound = if (teamMode) emptyMap() else rows.mapNotNull { (rd, x) -> x.position?.let { rd to it } }.toMap(),
            qualiByRound = if (teamMode) emptyMap() else q.mapNotNull { (rd, x) -> x.pos.toIntOrNull()?.let { rd to it } }.toMap(),
            form = rows.takeLast(5).map { it.second.posText }
        )
    }

    suspend fun driverSeasonStats(id: String, year: Int): StatLine = coroutineScope {
        val r = async { racesPaged("$year/drivers/$id/results.json", "Results") }
        val q = async { racesPaged("$year/drivers/$id/qualifying.json", "QualifyingResults") }
        statsFromRaces(r.await(), "Results", q.await(), false)
    }

    suspend fun teamSeasonStats(id: String, year: Int): StatLine = coroutineScope {
        val r = async { racesPaged("$year/constructors/$id/results.json", "Results") }
        val q = async { racesPaged("$year/constructors/$id/qualifying/1.json", "QualifyingResults") }
        statsFromRaces(r.await(), "Results", q.await(), true)
    }

    suspend fun driverCareerStats(id: String): StatLine = coroutineScope {
        val standings = async { mr("drivers/$id/driverStandings.json?limit=100").path("StandingsTable", "StandingsLists").arr() }
        val wins = async { total("drivers/$id/results/1.json") }
        val p2 = async { total("drivers/$id/results/2.json") }
        val p3 = async { total("drivers/$id/results/3.json") }
        val poles = async { total("drivers/$id/qualifying/1.json") }
        val fl = async { total("drivers/$id/fastest/1/results.json") }
        val starts = async { total("drivers/$id/results.json") }
        val s = standings.await().map { it.child("DriverStandings").arr().firstOrNull() }
        StatLine(
            points = s.sumOf { it.child("points").dbl() ?: 0.0 },
            wins = wins.await(), podiums = wins.await() + p2.await() + p3.await(), poles = poles.await(),
            fastest = fl.await(), starts = starts.await(), titles = s.count { it.child("position").str() == "1" }
        )
    }

    suspend fun teamCareerStats(id: String): StatLine = coroutineScope {
        val standings = async { mr("constructors/$id/constructorStandings.json?limit=100").path("StandingsTable", "StandingsLists").arr() }
        val wins = async { total("constructors/$id/results/1.json") }
        val p2 = async { total("constructors/$id/results/2.json") }
        val p3 = async { total("constructors/$id/results/3.json") }
        val poles = async { total("constructors/$id/qualifying/1.json") }
        val fl = async { total("constructors/$id/fastest/1/results.json") }
        val races = async { total("constructors/$id/races.json") }
        val s = standings.await().map { it.child("ConstructorStandings").arr().firstOrNull() }
        StatLine(
            points = s.sumOf { it.child("points").dbl() ?: 0.0 },
            wins = wins.await(), podiums = wins.await() + p2.await() + p3.await(), poles = poles.await(),
            fastest = fl.await(), starts = races.await(), titles = s.count { it.child("position").str() == "1" }
        )
    }

    data class YearSummary(val year: Int, val champion: DriverStanding?, val teamChampion: TeamStanding?, val races: Int, val winners: Int, val sprints: Int)

    suspend fun yearSummary(year: Int): YearSummary = coroutineScope {
        val d = async { driverStandings(year) }
        val t = async { runCatching { constructorStandings(year) }.getOrDefault(emptyList()) }
        val cal = async { calendar(year) }
        val wins = async { racesPaged("$year/results/1.json", "Results") }
        YearSummary(
            year, d.await().firstOrNull(), t.await().firstOrNull(), cal.await().size,
            wins.await().mapNotNull { it["Results"].arr().firstOrNull()?.path("Driver", "driverId").str() }.toSet().size,
            cal.await().count { it.isSprint }
        )
    }

    // ---------- records ----------

    data class Champion(val year: Int, val driver: DriverRef, val team: TeamRef?, val teamChampion: TeamRef?)
    data class Records(
        val champions: List<Champion>,
        val titles: List<Pair<DriverRef, Int>>,
        val teamTitles: List<Pair<TeamRef, Int>>,
        val wins: List<Pair<DriverRef, Int>>,
        val teamWins: List<Pair<TeamRef, Int>>,
        val poleStarts: List<Pair<DriverRef, Int>>,
        val streaks: List<Triple<DriverRef, Int, String>>
    )

    suspend fun records(): Records = coroutineScope {
        val dChamp = async { mr("driverStandings/1.json?limit=100").path("StandingsTable", "StandingsLists").arr() }
        val cChamp = async { mr("constructorStandings/1.json?limit=100").path("StandingsTable", "StandingsLists").arr() }
        val winsAll = async { racesPaged("results/1.json", "Results") }
        val poleAll = async { racesPaged("grid/1/results.json", "Results") }
        val teamByYear = cChamp.await().associate { (it.child("season").int() ?: 0) to it.child("ConstructorStandings").arr().firstOrNull()?.child("Constructor")?.let { c -> teamRef(c) } }
        val champions = dChamp.await().mapNotNull { l ->
            val y = l.child("season").int() ?: return@mapNotNull null
            val s = l.child("DriverStandings").arr().firstOrNull() ?: return@mapNotNull null
            Champion(y, driverRef(s.child("Driver")), s.child("Constructors").arr().lastOrNull()?.let { teamRef(it) }, teamByYear[y])
        }.sortedByDescending { it.year }
        val winRows = winsAll.await().sortedBy { (it["season"].int() ?: 0) * 100 + (it["round"].int() ?: 0) }
            .mapNotNull { r -> r["Results"].arr().firstOrNull()?.let { (r["season"].int() ?: 0) to resultRow(it) } }
        val streaks = mutableListOf<Triple<DriverRef, Int, String>>()
        var i = 0
        while (i < winRows.size) {
            var j = i
            while (j + 1 < winRows.size && winRows[j + 1].second.driver.id == winRows[i].second.driver.id) j++
            val n = j - i + 1
            if (n >= 5) streaks += Triple(winRows[i].second.driver, n, if (winRows[i].first == winRows[j].first) "${winRows[i].first}" else "${winRows[i].first}–${winRows[j].first}")
            i = j + 1
        }
        Records(
            champions = champions,
            titles = champions.groupBy { it.driver.id }.map { (_, v) -> v.first().driver to v.size }.sortedByDescending { it.second }.take(10),
            teamTitles = champions.mapNotNull { it.teamChampion }.groupBy { it.id }.map { (_, v) -> v.first() to v.size }.sortedByDescending { it.second }.take(10),
            wins = winRows.groupBy { it.second.driver.id }.map { (_, v) -> v.first().second.driver to v.size }.sortedByDescending { it.second }.take(10),
            teamWins = winRows.groupBy { it.second.team.id }.map { (_, v) -> v.first().second.team to v.size }.sortedByDescending { it.second }.take(10),
            poleStarts = poleAll.await().mapNotNull { it["Results"].arr().firstOrNull()?.let { x -> resultRow(x).driver } }
                .groupBy { it.id }.map { (_, v) -> v.first() to v.size }.sortedByDescending { it.second }.take(10),
            streaks = streaks.sortedByDescending { it.second }.take(10)
        )
    }
}
