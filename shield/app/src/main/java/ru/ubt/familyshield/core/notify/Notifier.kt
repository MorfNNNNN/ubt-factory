package ru.ubt.familyshield.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ru.ubt.familyshield.R
import ru.ubt.familyshield.shared.model.AlertSeverity

/** Notification channels and builders. Deep links open the relevant screen in MainActivity. */
class Notifier(private val context: Context) {

    companion object {
        const val CH_CRITICAL = "critical"
        const val CH_ALERTS = "alerts"
        const val CH_REQUESTS = "requests"
        const val CH_CHAT = "chat"
        const val CH_REMINDERS = "reminders"
        const val CH_WARNINGS = "warnings"
        const val CH_PROTECTION = "protection"
        const val CH_INFO = "info"

        const val EXTRA_ROUTE = "route"
        const val ID_FOREGROUND = 1001
        const val ID_WARNING = 1002
        const val ID_CHECK_IN = 1003
        const val ID_LIMIT = 1004

        private const val MAIN_ACTIVITY = "ru.ubt.familyshield.ui.MainActivity"
    }

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        fun channel(id: String, name: String, importance: Int, description: String, vibrate: Boolean = true) =
            NotificationChannel(id, name, importance).apply {
                this.description = description
                enableVibration(vibrate)
                if (id == CH_CRITICAL) {
                    vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 600)
                    lightColor = Color.RED
                    enableLights(true)
                }
            }
        nm.createNotificationChannels(
            listOf(
                channel(CH_CRITICAL, "Тревоги: SOS и мошенничество", NotificationManager.IMPORTANCE_HIGH, "SOS, падение, звонок мошенников прямо сейчас"),
                channel(CH_ALERTS, "Оповещения о близких", NotificationManager.IMPORTANCE_HIGH, "Подозрительные сообщения, новые приложения, геозоны"),
                channel(CH_REQUESTS, "Запросы от детей", NotificationManager.IMPORTANCE_DEFAULT, "Просьбы о дополнительном времени и новых приложениях"),
                channel(CH_CHAT, "Семейный чат", NotificationManager.IMPORTANCE_DEFAULT, "Сообщения от членов семьи"),
                channel(CH_REMINDERS, "Напоминания", NotificationManager.IMPORTANCE_HIGH, "Лекарства и ежедневная отметка «Я в порядке»"),
                channel(CH_WARNINGS, "Предупреждения о мошенничестве", NotificationManager.IMPORTANCE_HIGH, "Предупреждения на этом телефоне"),
                channel(CH_PROTECTION, "Фоновая защита", NotificationManager.IMPORTANCE_LOW, "Постоянное уведомление о работе защиты", vibrate = false),
                channel(CH_INFO, "Прочее", NotificationManager.IMPORTANCE_LOW, "Сводки и служебные сообщения", vibrate = false),
            ),
        )
    }

    fun canPost(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun openAppIntent(route: String? = null, requestCode: Int = 0): PendingIntent {
        val intent = Intent().setClassName(context, MAIN_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (route != null) intent.putExtra(EXTRA_ROUTE, route)
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun base(channel: String, title: String, text: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_shield)
        .setColor(ContextCompat.getColor(context, R.color.brand_primary))
        .setContentTitle(title)
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setAutoCancel(true)

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        if (!canPost()) return
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
        }
    }

    /** Guardian side: an alert about a family member. */
    fun memberAlert(id: String, memberName: String, title: String, body: String, severity: AlertSeverity) {
        val channel = if (severity == AlertSeverity.CRITICAL) CH_CRITICAL else CH_ALERTS
        val b = base(channel, "$memberName: $title", body)
            .setContentIntent(openAppIntent("alerts", id.hashCode()))
            .setPriority(if (severity >= AlertSeverity.HIGH) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (severity == AlertSeverity.CRITICAL) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_MESSAGE)
        post(id.hashCode(), b)
    }

    fun request(id: String, title: String, body: String) =
        post(id.hashCode(), base(CH_REQUESTS, title, body).setContentIntent(openAppIntent("requests", id.hashCode())))

    fun note(id: String, from: String, text: String) =
        post(id.hashCode(), base(CH_CHAT, from, text).setContentIntent(openAppIntent("chat", id.hashCode())).setCategory(NotificationCompat.CATEGORY_MESSAGE))

    /** On the protected device itself: a scam warning. */
    fun warning(title: String, text: String, fullScreen: PendingIntent? = null) {
        val b = base(CH_WARNINGS, title, text)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(fullScreen ?: openAppIntent("journal", ID_WARNING))
        if (fullScreen != null) b.setFullScreenIntent(fullScreen, true)
        post(ID_WARNING + (System.currentTimeMillis() % 1000).toInt(), b)
    }

    fun reminder(id: Int, title: String, text: String, contentIntent: PendingIntent, actions: List<NotificationCompat.Action> = emptyList()) {
        val b = base(CH_REMINDERS, title, text).setContentIntent(contentIntent).setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
        actions.forEach { b.addAction(it) }
        post(id, b)
    }

    fun info(id: Int, title: String, text: String, route: String? = null) =
        post(id, base(CH_INFO, title, text).setContentIntent(openAppIntent(route, id)))

    fun cancel(id: Int) = manager.cancel(id)

    fun foreground(title: String, text: String) = NotificationCompat.Builder(context, CH_PROTECTION)
        .setSmallIcon(R.drawable.ic_stat_shield)
        .setColor(ContextCompat.getColor(context, R.color.brand_primary))
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setShowWhen(false)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(openAppIntent(null, ID_FOREGROUND))
        .build()
}
