package com.f1pocket.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.f1pocket.app.R
import com.f1pocket.app.data.live.RaceMsg
import com.f1pocket.app.data.live.TowerRow

@Composable
fun TowerHeader() {
    val m = pal().muted
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.pos_short), fontSize = 10.sp, color = m, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
        Box(Modifier.width(13.dp))
        Text(stringResource(R.string.driver), fontSize = 10.sp, color = m, modifier = Modifier.width(54.dp))
        Text(stringResource(R.string.gap_interval), fontSize = 10.sp, color = m, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Text(stringResource(R.string.last_best), fontSize = 10.sp, color = m, modifier = Modifier.width(84.dp), textAlign = TextAlign.End)
        Text(stringResource(R.string.tyre), fontSize = 10.sp, color = m, modifier = Modifier.width(48.dp), textAlign = TextAlign.Center)
        Text(stringResource(R.string.stops_short), fontSize = 10.sp, color = m, modifier = Modifier.width(18.dp), textAlign = TextAlign.End)
    }
}

@Composable
fun TowerLine(r: TowerRow) {
    val p = pal()
    Divider()
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${r.position}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (r.out) p.muted else p.text, modifier = Modifier.width(22.dp), textAlign = TextAlign.End)
        Box(Modifier.width(13.dp), contentAlignment = Alignment.Center) { TeamBar(hexColor(r.teamColor), 24.dp) }
        Column(Modifier.width(54.dp)) {
            Text(r.tla, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (r.out) p.muted else p.text)
            val tag = when {
                r.out -> stringResource(R.string.out_tag)
                r.inPit -> stringResource(R.string.in_pit)
                else -> r.name
            }
            Text(tag, fontSize = 10.sp, color = if (r.inPit || r.out) Color_Amber else p.muted, maxLines = 1)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Mono(if (r.position == 1) stringResource(R.string.leader) else r.gap, 12.sp, FontWeight.Normal)
            Mono(if (r.position == 1) "" else r.interval, 10.sp, FontWeight.Normal, p.muted)
        }
        Column(Modifier.width(84.dp), horizontalAlignment = Alignment.End) {
            Mono(r.last.ifBlank { "—" }, 12.sp, FontWeight.Normal, if (r.lastPersonalBest) p.green else p.text)
            Mono(r.best.ifBlank { "—" }, 10.sp, FontWeight.Normal, if (r.bestOverall) p.purple else p.muted)
        }
        Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) { TyreBadge(r.compound, r.tyreAge) }
        Text(r.stops?.toString() ?: "", fontSize = 12.sp, color = p.text, modifier = Modifier.width(18.dp), textAlign = TextAlign.End)
    }
}

private val Color_Amber = androidx.compose.ui.graphics.Color(0xFFFFB000)

@Composable
fun RaceControlList(msgs: List<RaceMsg>, max: Int = 40) {
    val p = pal()
    CardBox {
        Text(stringResource(R.string.race_control), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = p.text)
        if (msgs.isEmpty()) Muted(stringResource(R.string.no_messages), 12.sp, Modifier.padding(top = 6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            msgs.take(max).forEach { m ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mono(listOfNotNull(m.lap?.let { "V$it" }, m.time.takeIf { it.isNotBlank() }).joinToString(" · "), 10.sp, FontWeight.Normal, p.muted)
                    Text(m.text, fontSize = 11.sp, color = p.text, lineHeight = 15.sp)
                }
            }
        }
    }
}

@Composable
fun FlagPill(status: String) {
    val (text, color) = when (status) {
        "1" -> stringResource(R.string.flag_green) to androidx.compose.ui.graphics.Color(0xFF1F7A3E)
        "2" -> stringResource(R.string.flag_yellow) to androidx.compose.ui.graphics.Color(0xFFB58900)
        "4" -> stringResource(R.string.safety_car) to androidx.compose.ui.graphics.Color(0xFFB58900)
        "5" -> stringResource(R.string.flag_red) to androidx.compose.ui.graphics.Color(0xFFC0392B)
        "6", "7" -> stringResource(R.string.vsc) to androidx.compose.ui.graphics.Color(0xFFB58900)
        else -> return
    }
    Text(
        text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White,
        modifier = Modifier
            .background(color, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}
