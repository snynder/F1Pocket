package com.racepocket.app.notify

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.racepocket.app.MainActivity
import com.racepocket.app.R
import com.racepocket.app.data.Jolpica
import com.racepocket.app.data.Repo
import com.racepocket.app.data.SessionKind
import java.time.Instant
import java.time.Year
import java.util.concurrent.TimeUnit

object Notifier {
    const val CHANNEL = "sessions"
    private const val PREFS = "alarms"

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.notif_channel), NotificationManager.IMPORTANCE_HIGH))
        }
    }

    fun schedulePeriodic(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork("reschedule", ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS).build())
        rescheduleSoon(context)
    }

    fun rescheduleSoon(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork("reschedule_now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RescheduleWorker>().build())
    }

    fun sessionLabel(context: Context, kind: SessionKind): String = context.getString(
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

    /** Cancels every scheduled alarm and sets them again for the next three weeks. */
    suspend fun reschedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet("ids", emptySet())?.forEach { id ->
            am.cancel(pending(context, id.toInt(), "", ""))
        }
        prefs.edit().putStringSet("ids", emptySet()).apply()

        val s = Repo.settings.current()
        if (!s.notifEnabled) return
        val now = Instant.now()
        val year = Year.now().value
        val races = runCatching { Jolpica.calendar(year) }.getOrDefault(emptyList()) +
            runCatching { if (now.atZone(java.time.ZoneOffset.UTC).monthValue >= 11) Jolpica.calendar(year + 1) else emptyList() }.getOrDefault(emptyList())
        val ids = mutableSetOf<String>()
        for (race in races) for (session in race.sessions) {
            if (s.notif[session.kind] != true) continue
            val at = session.start.minusSeconds(s.leadMinutes * 60L)
            if (at.isBefore(now) || at.isAfter(now.plusSeconds(21L * 86400))) continue
            val id = (race.season * 1000 + race.round * 10 + session.kind.ordinal)
            val title = "${race.flag} ${race.name}".trim()
            val text = context.getString(R.string.notif_text, sessionLabel(context, session.kind), s.leadMinutes)
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending(context, id, title, text))
            ids += id.toString()
        }
        prefs.edit().putStringSet("ids", ids).apply()
    }

    private fun pending(context: Context, id: Int, title: String, text: String): PendingIntent {
        val i = Intent(context, SessionAlarmReceiver::class.java).putExtra("id", id).putExtra("title", title).putExtra("text", text)
        return PendingIntent.getBroadcast(context, id, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(context: Context, id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }
}

class SessionAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.show(context, intent.getIntExtra("id", 0), intent.getStringExtra("title") ?: "", intent.getStringExtra("text") ?: "")
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) Notifier.rescheduleSoon(context)
    }
}

class RescheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            Notifier.reschedule(applicationContext)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
