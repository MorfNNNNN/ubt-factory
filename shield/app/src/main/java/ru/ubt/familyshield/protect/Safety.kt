package ru.ubt.familyshield.protect

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.ubt.familyshield.core.db.ShieldDatabase
import ru.ubt.familyshield.core.notify.Notifier
import ru.ubt.familyshield.core.prefs.SettingsStore
import ru.ubt.familyshield.protect.service.ReminderReceiver
import ru.ubt.familyshield.protect.ui.RingActivity
import ru.ubt.familyshield.shared.detect.PhoneNumbers
import ru.ubt.familyshield.shared.model.AlertSeverity
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.GeoPoint
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.TrustedContact
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.sqrt

fun mapLink(p: GeoPoint): String = "https://yandex.ru/maps/?pt=${p.lon},${p.lat}&z=17&l=map"

/** SOS: location to the family, SMS to emergency contacts and an automatic call. Always free. */
class SosManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val db: ShieldDatabase,
    private val reporter: Reporter,
    private val alerts: AlertCenter,
    private val scope: CoroutineScope,
) {
    /** SOS contacts from the policy plus guardians who shared their phone number. */
    suspend fun contacts(): List<TrustedContact> {
        val s = settings.current
        val fromPolicy = when (s.role) {
            MemberRole.ELDER -> s.elderPolicy.trustedContacts.filter { it.isSosTarget }
            MemberRole.CHILD -> s.childPolicy.sosContacts
            else -> emptyList()
        }
        val guardians = db.members().all().filter { it.role == MemberRole.GUARDIAN.name && !it.isSelf }
            .mapNotNull { m -> m.phone?.takeIf { it.isNotBlank() }?.let { TrustedContact(m.displayName, it, true, m.avatar) } }
        return (fromPolicy + guardians).distinctBy { PhoneNumbers.parse(it.phone).normalized ?: it.phone }
    }

    fun trigger(type: AlertType = AlertType.SOS, callFirst: Boolean = true) {
        scope.launch {
            val point = withTimeoutOrNull(20_000) { reporter.sendLocation(urgent = true) } ?: reporter.let { null }
            val where = point?.let { "\nГде я: ${mapLink(it)}" }.orEmpty()
            val title = if (type == AlertType.FALL_DETECTED) "Возможно, падение!" else "SOS! Нужна помощь"
            alerts.raise(type, title, "Сработала тревожная кнопка.$where", dedupeKey = "sos-${System.currentTimeMillis()}", cooldownMs = 0,
                severity = AlertSeverity.CRITICAL, location = point)
            val list = contacts()
            sendSms(list, "$title ${settings.current.displayName}.$where")
            if (callFirst) list.firstOrNull()?.let { call(it.phone) }
        }
    }

    private fun sendSms(contacts: List<TrustedContact>, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) return
        val sms = if (Build.VERSION.SDK_INT >= 31) context.getSystemService(SmsManager::class.java) else @Suppress("DEPRECATION") SmsManager.getDefault()
        for (c in contacts.take(5)) {
            runCatching { sms.sendMultipartTextMessage(c.phone, null, sms.divideMessage(text), null, null) }
        }
    }

    fun call(phone: String) {
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
        context.startActivitySafely(intent)
    }
}

/** Free-fall followed by an impact and stillness: a fall. The user gets 30 seconds to cancel. */
class FallDetector(private val context: Context, private val onFall: () -> Unit) : SensorEventListener {
    private val sm = context.getSystemService(SensorManager::class.java)
    private var freeFallAt = 0L
    private var impactAt = 0L
    private val stillSamples = ArrayList<Double>()
    private var running = false

    fun start() {
        if (running) return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        running = sm.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!running) return
        sm.unregisterListener(this)
        running = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
        val g = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
        val now = event.timestamp / 1_000_000
        when {
            impactAt > 0 -> {
                stillSamples += g
                if (now - impactAt > 2500) {
                    val mean = stillSamples.average()
                    val variance = stillSamples.map { (it - mean) * (it - mean) }.average()
                    if (sqrt(variance) < 0.18 && mean in 0.7..1.3) onFall()
                    impactAt = 0
                    freeFallAt = 0
                    stillSamples.clear()
                }
            }
            g < 0.4 -> if (freeFallAt == 0L) freeFallAt = now
            freeFallAt > 0 && g > 2.7 && now - freeFallAt in 60..1200 -> {
                impactAt = now
                stillSamples.clear()
            }
            freeFallAt > 0 && now - freeFallAt > 1500 -> freeFallAt = 0
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

}

/** Medication and "Я в порядке" reminders scheduled with AlarmManager (exact when allowed). */
class Reminders(
    private val context: Context,
    private val settings: SettingsStore,
    private val db: ShieldDatabase,
    private val notifier: Notifier,
    private val alerts: AlertCenter,
) {
    private val am = context.getSystemService(AlarmManager::class.java)

    companion object {
        const val ACTION_MED = "ru.ubt.familyshield.MED"
        const val ACTION_MED_TAKEN = "ru.ubt.familyshield.MED_TAKEN"
        const val ACTION_MED_SNOOZE = "ru.ubt.familyshield.MED_SNOOZE"
        const val ACTION_MED_CHECK = "ru.ubt.familyshield.MED_CHECK"
        const val ACTION_CHECKIN = "ru.ubt.familyshield.CHECKIN"
        const val ACTION_CHECKIN_DONE = "ru.ubt.familyshield.CHECKIN_DONE"
        const val ACTION_CHECKIN_CHECK = "ru.ubt.familyshield.CHECKIN_CHECK"
        const val EXTRA_ID = "id"
        const val EXTRA_SLOT = "slot"
    }

    private fun pending(action: String, id: String, slot: String, request: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id).putExtra(EXTRA_SLOT, slot)
        return PendingIntent.getBroadcast(context, request, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun setAlarm(at: Long, pi: PendingIntent) {
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        runCatching {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun nextAt(minuteOfDay: Int, days: Set<Int>): LocalDateTime? {
        val now = LocalDateTime.now()
        for (d in 0..7) {
            val date = now.toLocalDate().plusDays(d.toLong())
            val t = date.atStartOfDay().plusMinutes(minuteOfDay.toLong())
            if (t.isAfter(now) && date.dayOfWeek.value in days) return t
        }
        return null
    }

    private fun LocalDateTime.millis() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** Re-schedules every reminder from the current elder policy (idempotent). */
    fun rescheduleAll() {
        val s = settings.current
        if (s.role != MemberRole.ELDER) return
        val policy = s.elderPolicy
        policy.medications.forEachIndexed { i, med ->
            med.minutesOfDay.forEachIndexed { j, minute ->
                val at = nextAt(minute, med.days) ?: return@forEachIndexed
                val slot = at.toString()
                setAlarm(at.millis(), pending(ACTION_MED, med.id, slot, 10_000 + i * 50 + j))
            }
        }
        if (policy.checkIn.enabled) {
            nextAt(policy.checkIn.reminderMinute, (1..7).toSet())?.let { setAlarm(it.millis(), pending(ACTION_CHECKIN, "checkin", it.toString(), 9_000)) }
        }
    }

    fun onMedication(id: String, slot: String) {
        val med = settings.current.elderPolicy.medications.firstOrNull { it.id == id } ?: return
        val taken = NotificationCompat.Action(0, "Принял(а)", pending(ACTION_MED_TAKEN, id, slot, ("t$id$slot").hashCode()))
        val snooze = NotificationCompat.Action(0, "Через 10 минут", pending(ACTION_MED_SNOOZE, id, slot, ("s$id$slot").hashCode()))
        notifier.reminder(id.hashCode(), "Пора принять: ${med.name}", med.dose.ifBlank { "Нажмите «Принял(а)», когда примете лекарство" },
            notifier.openAppIntent("elder"), listOf(taken, snooze))
        if (med.notifyGuardianIfMissed) setAlarm(System.currentTimeMillis() + 60 * 60_000L, pending(ACTION_MED_CHECK, id, slot, ("c$id$slot").hashCode()))
        rescheduleAll()
    }

    suspend fun onMedicationTaken(id: String, slot: String) {
        notifier.cancel(id.hashCode())
        val med = settings.current.elderPolicy.medications.firstOrNull { it.id == id }
        db.events().insert(ru.ubt.familyshield.core.db.EventEntity(type = "MED_TAKEN:$id:$slot", title = "Лекарство принято: ${med?.name.orEmpty()}", createdAt = System.currentTimeMillis()))
        alerts.raise(AlertType.MEDICATION_TAKEN, "Лекарство принято", med?.name.orEmpty(), dedupeKey = "med-$id-$slot", cooldownMs = 3_600_000L)
    }

    fun snooze(id: String, slot: String) {
        notifier.cancel(id.hashCode())
        setAlarm(System.currentTimeMillis() + 10 * 60_000L, pending(ACTION_MED, id, slot, ("z$id$slot").hashCode()))
    }

    suspend fun checkMedicationTaken(id: String, slot: String) {
        val taken = db.events().countSince("MED_TAKEN:$id:$slot", 0) > 0
        if (taken) return
        val med = settings.current.elderPolicy.medications.firstOrNull { it.id == id } ?: return
        alerts.raise(AlertType.MEDICATION_MISSED, "Пропущен приём лекарства", "${med.name} — не отмечено в течение часа", dedupeKey = "missed-$id-$slot", cooldownMs = 3_600_000L)
    }

    fun onCheckInReminder(slot: String) {
        val done = NotificationCompat.Action(0, "Я в порядке", pending(ACTION_CHECKIN_DONE, "checkin", slot, 9_001))
        notifier.reminder(Notifier.ID_CHECK_IN, "Как вы себя чувствуете?", "Нажмите «Я в порядке», чтобы близкие не волновались", notifier.openAppIntent("elder"), listOf(done))
        val grace = settings.current.elderPolicy.checkIn.graceMinutes
        setAlarm(System.currentTimeMillis() + grace * 60_000L, pending(ACTION_CHECKIN_CHECK, "checkin", slot, 9_002))
        rescheduleAll()
    }

    suspend fun checkCheckInDone(slot: String) {
        val since = runCatching { LocalDateTime.parse(slot).millis() }.getOrDefault(System.currentTimeMillis() - 3 * 3_600_000L)
        if (settings.current.lastCheckInAt >= since) return
        alerts.raise(AlertType.CHECK_IN_MISSED, "Нет отметки «Я в порядке»", "Близкий не отметился после напоминания. Позвоните ему.",
            dedupeKey = "checkin-${LocalDate.now()}", cooldownMs = 12 * 3_600_000L)
    }
}

/** "Найти телефон": loud alarm even in silent mode, plus a full-screen notice. */
class Ringer(private val context: Context) {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun ring(seconds: Int) {
        stop()
        val audio = context.getSystemService(AudioManager::class.java)
        runCatching { audio.setStreamVolume(AudioManager.STREAM_ALARM, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        handler.postDelayed({ stop() }, seconds.coerceIn(5, 120) * 1000L)
        context.startActivitySafely(Intent(context, RingActivity::class.java))
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
    }
}
