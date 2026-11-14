package com.f1pocket.app.data.live

import com.f1pocket.app.data.Http
import com.f1pocket.app.data.bool
import com.f1pocket.app.data.child
import com.f1pocket.app.data.fields
import com.f1pocket.app.data.int
import com.f1pocket.app.data.items
import com.f1pocket.app.data.mergeJson
import com.f1pocket.app.data.parseInstant
import com.f1pocket.app.data.parseLapTime
import com.f1pocket.app.data.path
import com.f1pocket.app.data.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * F1's free live timing feed (SignalR Core), the same one the Pit Wall site uses.
 * The phone connects directly, so there is no CORS and no server in between.
 * Car positions (GPS) are not part of the free feed.
 */
class LiveTimingClient(private val scope: CoroutineScope) {
    sealed interface Status {
        data object Idle : Status
        data object Connecting : Status
        data object Connected : Status
        data class Failed(val reason: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()
    private val _data = MutableStateFlow<Map<String, JsonElement>>(emptyMap())
    val data: StateFlow<Map<String, JsonElement>> = _data.asStateFlow()

    private val topics = HashMap<String, JsonElement>()
    private val lock = Any()
    @Volatile private var dirty = false
    @Volatile private var opened = false
    @Volatile private var closedByUser = false
    private var ws: WebSocket? = null
    private val jobs = java.util.concurrent.CopyOnWriteArrayList<Job>()

    private val TOPICS = listOf("TimingData", "TimingAppData", "DriverList", "RaceControlMessages", "TrackStatus", "LapCount", "SessionInfo", "SessionStatus", "ExtrapolatedClock", "TimingStats")
    private val RS = '\u001e'

    fun connect() {
        if (_status.value is Status.Connecting || _status.value is Status.Connected) return
        closedByUser = false
        jobs += scope.launch { open() }
        jobs += scope.launch {
            while (isActive) {
                delay(700)
                if (dirty) {
                    dirty = false
                    _data.value = synchronized(lock) { HashMap(topics) }
                }
            }
        }
    }

    fun disconnect() {
        closedByUser = true
        jobs.forEach { it.cancel() }
        jobs.clear()
        ws?.close(1000, null)
        ws = null
        opened = false
        synchronized(lock) { topics.clear() }
        _data.value = emptyMap()
        _status.value = Status.Idle
    }

    private suspend fun negotiate(): String? = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://livetiming.formula1.com/signalrcore/negotiate?negotiateVersion=1")
                .post("".toRequestBody("text/plain".toMediaType()))
                .header("User-Agent", Http.UA)
                .build()
            Http.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return@use null
                val j = Http.json.parseToJsonElement(r.body?.string() ?: "{}")
                j.child("connectionToken").str() ?: j.child("connectionId").str()
            }
        }.getOrNull()
    }

    private suspend fun open() {
        _status.value = Status.Connecting
        opened = false
        val id = negotiate()
        val url = "wss://livetiming.formula1.com/signalrcore" + (id?.let { "?id=" + java.net.URLEncoder.encode(it, "UTF-8") } ?: "")
        val req = Request.Builder().url(url).header("User-Agent", Http.UA).build()
        ws = Http.client.newWebSocket(req, listener)
        jobs += scope.launch {
            delay(12_000)
            if (!opened && !closedByUser) {
                ws?.cancel()
                _status.value = Status.Failed("timeout")
            }
        }
    }

    private fun send(ws: WebSocket, obj: JsonElement) {
        ws.send(obj.toString() + RS)
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            send(webSocket, buildJsonObject { put("protocol", "json"); put("version", 1) })
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            for (raw in text.split(RS)) {
                if (raw.isBlank()) continue
                val m = runCatching { Http.json.parseToJsonElement(raw) }.getOrNull() as? JsonObject ?: continue
                val type = m["type"].int()
                when {
                    type == null && !opened -> {
                        // handshake accepted: subscribe and keep the connection alive
                        send(webSocket, buildJsonObject {
                            put("type", 1); put("target", "Subscribe"); put("invocationId", "0")
                            put("arguments", JsonArray(listOf(JsonArray(TOPICS.map { JsonPrimitive(it) }))))
                        })
                        jobs += scope.launch {
                            while (isActive) {
                                delay(15_000)
                                send(webSocket, buildJsonObject { put("type", 6) })
                            }
                        }
                    }
                    type == 3 && m["invocationId"].str() == "0" -> {
                        if (m["error"] != null && m["error"].str() != null) {
                            _status.value = Status.Failed(m["error"].str() ?: "error")
                            return
                        }
                        opened = true
                        synchronized(lock) {
                            topics.clear()
                            m["result"].fields().forEach { (k, v) -> topics[k] = v }
                        }
                        dirty = true
                        _status.value = Status.Connected
                    }
                    type == 1 && m["target"].str() == "feed" -> {
                        val args = m["arguments"].items()
                        val topic = args.getOrNull(0).str() ?: continue
                        val payload = args.getOrNull(1) ?: continue
                        synchronized(lock) {
                            if (topic == "RaceControlMessages") {
                                val cur = topics[topic].child("Messages").items()
                                val add = payload.child("Messages").items()
                                topics[topic] = JsonObject(mapOf("Messages" to JsonArray(cur + add)))
                            } else {
                                topics[topic] = mergeJson(topics[topic], payload)
                            }
                        }
                        dirty = true
                    }
                    type == 7 -> webSocket.close(1000, null)
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            if (closedByUser) return
            if (!opened) {
                _status.value = Status.Failed(t.message ?: "ws")
            } else {
                scope.launch { delay(3000); if (!closedByUser) open() }
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!closedByUser && opened) scope.launch { delay(3000); if (!closedByUser) open() }
        }
    }
}

data class TowerRow(
    val position: Int,
    val number: String,
    val tla: String,
    val name: String,
    val teamColor: String,
    val gap: String,
    val interval: String,
    val last: String,
    val best: String,
    val lastPersonalBest: Boolean,
    val bestOverall: Boolean,
    val compound: String?,
    val tyreAge: Int?,
    val stops: Int?,
    val inPit: Boolean,
    val out: Boolean
)

data class RaceMsg(val time: String, val lap: Int?, val text: String, val flag: String?)

data class LiveView(
    val hasTiming: Boolean = false,
    val meeting: String = "",
    val session: String = "",
    val sessionType: String = "",
    val status: String = "",
    val lap: Int? = null,
    val totalLaps: Int? = null,
    val remaining: String? = null,
    val trackStatus: String = "",
    val rows: List<TowerRow> = emptyList(),
    val messages: List<RaceMsg> = emptyList(),
    val year: Int? = null,
    val countryCode: String? = null
)

object LiveParser {
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    fun parse(t: Map<String, JsonElement>): LiveView {
        val info = t["SessionInfo"]
        val drivers = t["DriverList"].fields()
        val lines = t["TimingData"].child("Lines").fields()
        val app = t["TimingAppData"].child("Lines").fields()
        val raw = lines.mapNotNull { (num, l) ->
            val pos = l.child("Position").int() ?: return@mapNotNull null
            val d = drivers[num]
            val st = app[num].child("Stints").items().lastOrNull()
            TowerRow(
                position = pos,
                number = num,
                tla = d.child("Tla").str() ?: num,
                name = d.child("LastName").str() ?: "",
                teamColor = d.child("TeamColour").str() ?: "8A929C",
                gap = l.child("GapToLeader").str() ?: l.path("Stats").items().lastOrNull().child("TimeDiffToFastest").str() ?: "",
                interval = l.path("IntervalToPositionAhead", "Value").str() ?: l.path("Stats").items().lastOrNull().child("TimeDifftoPositionAhead").str() ?: "",
                last = l.path("LastLapTime", "Value").str() ?: "",
                best = l.path("BestLapTime", "Value").str() ?: "",
                lastPersonalBest = l.path("LastLapTime", "PersonalFastest").bool(),
                bestOverall = false,
                compound = st.child("Compound").str(),
                tyreAge = st.child("TotalLaps").int(),
                stops = l.child("NumberOfPitStops").int(),
                inPit = l.child("InPit").bool(),
                out = l.child("Retired").bool() || l.child("Stopped").bool()
            )
        }.sortedBy { it.position }
        val fastest = raw.mapNotNull { r -> parseLapTime(r.best)?.let { r.number to it } }.minByOrNull { it.second }?.first
        val rows = raw.map { it.copy(bestOverall = it.number == fastest) }
        val msgs = t["RaceControlMessages"].child("Messages").items().map {
            RaceMsg(
                time = parseInstant(it.child("Utc").str())?.let { i -> timeFmt.format(i) } ?: "",
                lap = it.child("Lap").int(),
                text = it.child("Message").str() ?: "",
                flag = it.child("Flag").str()
            )
        }.reversed()
        return LiveView(
            hasTiming = rows.isNotEmpty(),
            meeting = info.path("Meeting", "Name").str() ?: "",
            session = info.child("Name").str() ?: "",
            sessionType = info.child("Type").str() ?: "",
            status = t["SessionStatus"].child("Status").str() ?: info.child("SessionStatus").str() ?: "",
            lap = t["LapCount"].child("CurrentLap").int(),
            totalLaps = t["LapCount"].child("TotalLaps").int(),
            remaining = t["ExtrapolatedClock"].child("Remaining").str(),
            trackStatus = t["TrackStatus"].child("Status").str() ?: "",
            rows = rows,
            messages = msgs,
            year = info.child("StartDate").str()?.take(4)?.toIntOrNull(),
            countryCode = info.path("Meeting", "Country", "Code").str()
        )
    }
}
