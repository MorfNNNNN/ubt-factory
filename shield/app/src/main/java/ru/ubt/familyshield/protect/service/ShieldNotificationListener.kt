package ru.ubt.familyshield.protect.service

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import ru.ubt.familyshield.ShieldApp
import ru.ubt.familyshield.protect.ProtectionCenter

/**
 * Reads incoming SMS and messenger notifications on the device to spot fraud (elders) and danger
 * (children). Analysis is local; only the resulting alert with a short excerpt reaches the family.
 */
class ShieldNotificationListener : NotificationListenerService() {
    private var p: ProtectionCenter? = null

    override fun onListenerConnected() {
        p = (application as ShieldApp).graph.protection
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val center = p ?: return
        if (sbn.packageName == packageName || sbn.isOngoing) return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val parts = ArrayList<String>()
        val messages = if (Build.VERSION.SDK_INT >= 24) extras.getParcelableArray(Notification.EXTRA_MESSAGES) else null
        if (!messages.isNullOrEmpty()) {
            // MessagingStyle: analyse only the newest message to avoid re-reporting the history.
            (messages.last() as? android.os.Bundle)?.getCharSequence("text")?.let { parts += it.toString() }
        } else {
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.let { parts += it.toString() }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.lastOrNull()?.let { parts += it.toString() }
        }
        val text = parts.distinct().joinToString("\n").take(2000)
        if (text.isBlank()) return
        runCatching { center.messages.onMessage(sbn.packageName, title, text) }
    }
}
