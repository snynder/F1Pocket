package com.f1pocket.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.Year

/**
 * Everything the app downloads is kept on the phone (HTTP disk cache + saved track maps),
 * and is used whenever there is no internet. This downloads a whole season in one go,
 * so it is available offline even for screens that were never opened.
 */
object Offline {
    sealed interface State {
        data object Idle : State
        data class Running(val done: Int, val total: Int) : State
        data class Done(val failed: Int) : State
    }

    val state = MutableStateFlow<State>(State.Idle)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    fun downloadSeason(year: Int = Year.now().value) {
        if (job?.isActive == true) return
        job = scope.launch {
            state.value = State.Running(0, 1)
            val now = Instant.now()
            val races = runCatching { Jolpica.calendar(year) }.getOrDefault(emptyList())
            val finished = races.filter { it.isFinished(now) }
            val steps = ArrayList<suspend () -> Unit>()
            steps.add { Jolpica.driverStandings(year) }
            steps.add { Jolpica.constructorStandings(year) }
            steps.add { Jolpica.seasonLeaders(year) }
            steps.add { Jolpica.calendar(year + 1) }
            steps.add { Jolpica.records() }
            steps.add { Jolpica.allCircuits() }
            for (r in finished) {
                steps.add { Jolpica.raceResults(r.season, r.round) }
                steps.add { Jolpica.qualifying(r.season, r.round) }
                steps.add { Jolpica.pitStops(r.season, r.round) }
                if (r.isSprint) steps.add { Jolpica.sprintResults(r.season, r.round) }
            }
            for (r in races) {
                steps.add { Jolpica.circuitHistory(r.circuitId) }
                steps.add { Tracks.get(r.name, minOf(r.season, Year.now().value)) }
            }
            var failed = 0
            steps.forEachIndexed { i, step ->
                state.value = State.Running(i, steps.size)
                if (runCatching { step() }.isFailure) failed++
            }
            state.value = State.Done(failed)
        }
    }
}
