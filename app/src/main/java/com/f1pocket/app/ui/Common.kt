package com.f1pocket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.f1pocket.app.R
import com.f1pocket.app.data.FreshFetch
import com.f1pocket.app.data.HttpException
import com.f1pocket.app.data.LiveWindowException
import com.f1pocket.app.data.SessionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// ---------- loading state ----------

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
    data class Err(val error: Throwable) : Load<Nothing>
}

val <T> Load<T>.valueOrNull: T? get() = (this as? Load.Ok<T>)?.value

abstract class BaseVM : ViewModel() {
    /** True while a pull to refresh is running (drives the spinner at the top). */
    val refreshing = MutableStateFlow(false)
    private var pulling = false
    private val pullJobs = mutableListOf<Job>()

    protected fun <T> load(target: MutableStateFlow<Load<T>>, block: suspend () -> T): Job {
        val pull = pulling
        val job = viewModelScope.launch(if (pull) FreshFetch() else EmptyCoroutineContext) {
            // on a pull, keep showing what is on screen until the new data arrives
            if (!pull || target.value !is Load.Ok) target.value = Load.Loading
            val r = try {
                Load.Ok(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Load.Err(e)
            }
            if (!(pull && r is Load.Err && target.value is Load.Ok)) target.value = r
        }
        if (pull) pullJobs += job
        return job
    }

    /** Pull to refresh: runs [action], skipping the cache, and keeps the spinner until its loads finish. */
    fun pull(action: () -> Unit) {
        if (refreshing.value) return
        refreshing.value = true
        pulling = true
        pullJobs.clear()
        try { action() } finally { pulling = false }
        val jobs = pullJobs.toList()
        viewModelScope.launch {
            if (jobs.isEmpty()) delay(700) else jobs.joinAll()
            refreshing.value = false
        }
    }
}

/** Wraps a screen's list so that pulling it down reloads the page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullRefresh(vm: BaseVM, onRefresh: () -> Unit, content: @Composable () -> Unit) {
    val refreshing by vm.refreshing.collectAsState()
    val state = rememberPullToRefreshState()
    val p = pal()
    PullToRefreshBox(
        isRefreshing = refreshing, onRefresh = { vm.pull(onRefresh) }, state = state, modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter),
                containerColor = p.card, color = p.accent
            )
        }
    ) { content() }
}

@Composable
fun errorText(e: Throwable): String = when (e) {
    is LiveWindowException -> stringResource(R.string.err_live_window)
    is HttpException -> if (e.code == 404) stringResource(R.string.err_not_found) else stringResource(R.string.err_http, e.code)
    is IOException -> stringResource(R.string.err_network)
    else -> stringResource(R.string.err_generic) + (e.message?.let { " ($it)" } ?: "")
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = pal().accent, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
    }
}

@Composable
fun ErrorBox(e: Throwable, onRetry: (() -> Unit)?) {
    CardBox {
        Text(stringResource(R.string.err_title), fontWeight = FontWeight.Bold, color = pal().text, fontSize = 14.sp)
        Spacer(Modifier.height(4.dp))
        Muted(errorText(e), 13.sp)
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry), color = pal().accentText) }
        }
    }
}

@Composable
fun <T> LoadBox(state: Load<T>, onRetry: (() -> Unit)?, content: @Composable (T) -> Unit) {
    when (state) {
        is Load.Loading -> LoadingBox()
        is Load.Err -> ErrorBox(state.error, onRetry)
        is Load.Ok -> content(state.value)
    }
}

@Composable
fun EmptyBox(text: String) {
    CardBox { Muted(text, 13.sp) }
}

// ---------- building blocks ----------

@Composable
fun CardBox(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: Dp = 14.dp,
    color: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val p = pal()
    var m = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color ?: p.card)
    if (onClick != null) m = m.clickable(onClick = onClick)
    Column(m.padding(padding), content = content)
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = pal().text, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal().accentText, modifier = Modifier.clickable(onClick = onAction).padding(8.dp))
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: (() -> Unit)? = null, subtitle: String? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = pal().text)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = if (onBack != null) 22.sp else 28.sp, fontWeight = FontWeight.ExtraBold, color = pal().text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Muted(subtitle, 12.sp)
        }
        trailing()
    }
}

@Composable
fun Muted(text: String, size: TextUnit = 12.sp, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE) {
    Text(text, fontSize = size, color = pal().muted, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
fun Mono(text: String, size: TextUnit = 13.sp, weight: FontWeight = FontWeight.SemiBold, color: Color? = null, modifier: Modifier = Modifier, align: TextAlign? = null, decoration: TextDecoration? = null) {
    Text(text, fontSize = size, fontWeight = weight, fontFamily = FontFamily.Monospace, color = color ?: pal().text, modifier = modifier, textAlign = align, maxLines = 1, textDecoration = decoration)
}

@Composable
fun Flag(flag: String, size: TextUnit = 16.sp) {
    if (flag.isNotEmpty()) Text(flag, fontSize = size)
}

@Composable
fun TeamBar(color: Color, height: Dp = 20.dp) {
    Box(Modifier.width(3.dp).height(height).clip(RoundedCornerShape(2.dp)).background(color))
}

@Composable
fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(pal().divider))
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    val p = pal()
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) p.text else Color.Transparent)
            .border(1.dp, if (selected) p.text else p.divider, RoundedCornerShape(50))
            .clickable(onClick = onClick).heightIn(min = 36.dp).padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) p.bg else p.muted)
    }
}

@Composable
fun PillRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEachIndexed { i, s -> Pill(s, i == selected) { onSelect(i) } }
    }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val p = pal()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(p.card).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, s ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (i == selected) p.text else Color.Transparent)
                    .clickable { onSelect(i) }.heightIn(min = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(s, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (i == selected) p.bg else p.muted, maxLines = 1)
            }
        }
    }
}

@Composable
fun <T> Picker(label: String, options: List<T>, selected: T?, text: (T) -> String, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val p = pal()
    Box(modifier) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(p.card).border(1.dp, p.divider, RoundedCornerShape(10.dp))
                .clickable(enabled = options.isNotEmpty()) { open = true }.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(label, fontSize = 10.sp, color = p.muted, fontWeight = FontWeight.SemiBold)
            Text((selected?.let(text) ?: "—") + " ▾", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(text(o)) }, onClick = { open = false; onSelect(o) })
            }
        }
    }
}

@Composable
fun TyreBadge(compound: String?, age: Int?, fixed: Boolean = false) {
    val (letter, color) = tyreStyle(compound)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.size(18.dp).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
            // no font padding, line box trimmed: the letter sits in the optical centre of the ring
            Text(
                letter, fontWeight = FontWeight.ExtraBold, color = pal().text, textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = 9.sp, lineHeight = 9.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)
                )
            )
        }
        // in tables the age gets a fixed slot, so the circle stays in the same place on every row
        if (fixed) Box(Modifier.width(16.dp), contentAlignment = Alignment.CenterStart) {
            if (age != null) Text("$age", fontSize = 10.sp, color = pal().muted, maxLines = 1)
        } else if (age != null) Text("$age", fontSize = 10.sp, color = pal().muted)
    }
}

fun tyreStyle(compound: String?): Pair<String, Color> = when (compound?.uppercase()) {
    "SOFT" -> "S" to Color(0xFFE8002D)
    "MEDIUM" -> "M" to Color(0xFFFFD12E)
    "HARD" -> "H" to Color(0xFFE6E6EA)
    "INTERMEDIATE" -> "I" to Color(0xFF43B02A)
    "WET" -> "W" to Color(0xFF0067AD)
    else -> "?" to Color(0xFF8A929C)
}

@Composable
fun Avatar(url: String?, initials: String, size: Dp, border: Color) {
    val p = pal()
    Box(
        Modifier.size(size).clip(CircleShape).background(p.card2).border(3.dp, border, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = p.muted)
        if (url != null) {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, sub: String? = null, flag: String = "", color: Color? = null, suffix: String? = null) {
    val p = pal()
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(color ?: p.card).padding(horizontal = 10.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, fontSize = 10.sp, color = p.muted, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Flag(flag, 13.sp)
            Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (suffix != null) Text(suffix, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        if (!sub.isNullOrBlank()) Text(sub, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun KeyValueRow(left: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), content = left)
}

/** Places gained (green ▲) or lost (red ▼) since the previous round. */
@Composable
fun PositionDelta(delta: Int?) {
    val d = delta ?: 0
    Text(
        when {
            d > 0 -> "▲$d"
            d < 0 -> "▼${-d}"
            else -> ""
        },
        fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1,
        color = if (d > 0) Color(0xFF2ECC71) else Color(0xFFFF4D4D),
        modifier = Modifier.width(26.dp)
    )
}

/** Ranked row used by standings and lists. */
@Composable
fun RankRow(
    pos: String, color: Color, flag: String, title: String, subtitle: String?, value: String,
    extra: String? = null, onClick: (() -> Unit)? = null, delta: Int? = null, showDelta: Boolean = false
) {
    var m = Modifier.fillMaxWidth()
    if (onClick != null) m = m.clickable(onClick = onClick)
    Row(m.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(pos, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = pal().text, textAlign = TextAlign.End, modifier = Modifier.widthIn(min = 20.dp))
        if (showDelta) PositionDelta(delta)
        TeamBar(color)
        Flag(flag, 15.sp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = pal().text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Muted(subtitle, 10.sp, maxLines = 1)
        }
        if (extra != null) Mono(extra, 12.sp, FontWeight.Normal, pal().muted)
        Mono(value, 13.sp, FontWeight.Bold, modifier = Modifier.widthIn(min = 36.dp), align = TextAlign.End)
    }
}

fun hexColor(s: String?): Color = try {
    Color(android.graphics.Color.parseColor("#" + (s ?: "8A929C").removePrefix("#")))
} catch (_: Exception) {
    Color(0xFF8A929C)
}

fun teamColor(argb: Long): Color = Color(argb)

// ---------- formats ----------

object Fmt {
    private fun loc(): Locale = Locale.getDefault()
    private fun up(s: String) = s.replace(".", "").uppercase(loc())
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun dayMonth(d: LocalDate): String = up(DateTimeFormatter.ofPattern("dd MMM", loc()).format(d))
    fun weekdayDayMonth(d: LocalDate): String = up(DateTimeFormatter.ofPattern("EEE dd MMM", loc()).format(d))
    fun weekdayTime(i: Instant): String = up(DateTimeFormatter.ofPattern("EEE dd MMM · HH:mm", loc()).withZone(zone).format(i))
    fun time(i: Instant): String = DateTimeFormatter.ofPattern("HH:mm").withZone(zone).format(i)
    fun range(a: LocalDate, b: LocalDate): String =
        if (a.month == b.month) String.format(loc(), "%02d–%s", a.dayOfMonth, dayMonth(b)) else "${dayMonth(a)}–${dayMonth(b)}"
    fun raceName(name: String): String = name.replace("Grand Prix", "GP")
    fun points(s: String): String = s.removeSuffix(".0")
}

@Composable
fun sessionName(kind: SessionKind): String = stringResource(
    when (kind) {
        SessionKind.FP1 -> R.string.s_fp1
        SessionKind.FP2 -> R.string.s_fp2
        SessionKind.FP3 -> R.string.s_fp3
        SessionKind.SPRINT_QUALI -> R.string.s_sprint_quali
        SessionKind.SPRINT -> R.string.s_sprint
        SessionKind.QUALI -> R.string.s_quali
        SessionKind.RACE -> R.string.s_race
    }
)

@Composable
fun openF1SessionName(name: String): String = when (name) {
    "Practice 1" -> stringResource(R.string.s_fp1)
    "Practice 2" -> stringResource(R.string.s_fp2)
    "Practice 3" -> stringResource(R.string.s_fp3)
    "Sprint Qualifying", "Sprint Shootout" -> stringResource(R.string.s_sprint_quali)
    "Sprint" -> stringResource(R.string.s_sprint)
    "Qualifying" -> stringResource(R.string.s_quali)
    "Race" -> stringResource(R.string.s_race)
    else -> name
}
