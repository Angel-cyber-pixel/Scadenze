package com.scadenzeinvisibili.app

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Regola degli avvisi: si comincia 7 giorni prima della scadenza.
 * Ogni 3 giorni finché mancano 2 o più giorni, ogni giorno quando manca 1 giorno o meno,
 * poi ogni 3 giorni se è già scaduto.
 */
fun shouldNotify(item: Item, todayDay: Long): Boolean {
    val left = item.expiryDay - todayDay
    if (left > 7L) return false
    val gap = if (left in 0L..1L) 1L else 3L
    return item.lastNotifiedDay < 0L || todayDay - item.lastNotifiedDay >= gap
}

object Notifier {
    const val CHANNEL_ID = "scadenze"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Scadenze",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        channel.description = "Avvisi su ciò che sta per scadere"
        nm.createNotificationChannel(channel)
    }

    /** Controllo giornaliero, la prima volta alle 9 del mattino. */
    fun schedule(context: Context) {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(9, 0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val delayMinutes = Duration.between(now, next).toMinutes()

        val request = PeriodicWorkRequestBuilder<ExpiryWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "expiry-check",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun show(context: Context, title: String, lines: List<String>) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val intent = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        )
        val pending = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(Notification.BigTextStyle().bigText(lines.joinToString("\n")))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        nm.notify(1, notification)
    }
}

class ExpiryWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): ListenableWorker.Result {
        val ctx = applicationContext

        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return ListenableWorker.Result.success()
        }

        val today = LocalDate.now()
        val todayDay = today.toEpochDay()
        val due = Store.load(ctx)
            .filter { shouldNotify(it, todayDay) }
            .sortedBy { it.expiryDay }

        if (due.isEmpty()) return ListenableWorker.Result.success()

        val lines = due.map { "${it.emoji} ${it.name}: ${statusText(it.daysLeft(today))}" }
        val title = if (due.size == 1) "Scadenza in arrivo" else "${due.size} scadenze in arrivo"
        Notifier.createChannel(ctx)
        Notifier.show(ctx, title, lines)

        val ids = due.map { it.id }.toSet()
        val updated = Store.load(ctx).map {
            if (it.id in ids) it.copy(lastNotifiedDay = todayDay) else it
        }
        Store.save(ctx, updated)

        return ListenableWorker.Result.success()
    }
}
