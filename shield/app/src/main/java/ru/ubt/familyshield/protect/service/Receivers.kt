package ru.ubt.familyshield.protect.service

import android.app.admin.DeviceAdminReceiver
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch
import ru.ubt.familyshield.ShieldApp
import ru.ubt.familyshield.core.family.FamilyCalendar
import ru.ubt.familyshield.protect.Reminders
import ru.ubt.familyshield.shared.model.AlertType

/** Restarts protection after a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as ShieldApp).graph
        graph.protection.apply()
        graph.scheduleWork()
    }
}

/** Uninstall protection: the app cannot be removed while it is a device admin; disabling it alerts the family. */
class ShieldDeviceAdmin : DeviceAdminReceiver() {
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        val p = (context.applicationContext as ShieldApp).graph.protection
        p.alerts.raise(AlertType.UNINSTALL_ATTEMPT, "Попытка снять защиту от удаления", "На телефоне открыто отключение администратора «Семейного щита»", dedupeKey = "admin-off", cooldownMs = 10 * 60_000L)
        return "Близкие получат уведомление об отключении защиты."
    }

    override fun onDisabled(context: Context, intent: Intent) {
        val p = (context.applicationContext as ShieldApp).graph.protection
        p.alerts.raise(AlertType.TAMPER, "Защита от удаления отключена", "Администратор устройства «Семейный щит» выключен", dedupeKey = "admin-disabled", cooldownMs = 10 * 60_000L)
    }
}

/** Medication, check-in and family calendar alarms and their notification actions. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as ShieldApp).graph
        val r = graph.protection.reminders
        val id = intent.getStringExtra(Reminders.EXTRA_ID).orEmpty()
        val slot = intent.getStringExtra(Reminders.EXTRA_SLOT).orEmpty()
        val pending = goAsync()
        graph.appScope.launch {
            try {
                when (intent.action) {
                    Reminders.ACTION_MED -> r.onMedication(id, slot)
                    Reminders.ACTION_MED_TAKEN -> r.onMedicationTaken(id, slot)
                    Reminders.ACTION_MED_SNOOZE -> r.snooze(id, slot)
                    Reminders.ACTION_MED_CHECK -> r.checkMedicationTaken(id, slot)
                    Reminders.ACTION_CHECKIN -> r.onCheckInReminder(slot)
                    Reminders.ACTION_CHECKIN_DONE -> graph.checkIn(answered = slot == Reminders.SLOT_ASKED)
                    Reminders.ACTION_CHECKIN_CHECK -> r.checkCheckInDone(slot)
                    FamilyCalendar.ACTION_ALARM -> graph.calendar.onAlarm()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
