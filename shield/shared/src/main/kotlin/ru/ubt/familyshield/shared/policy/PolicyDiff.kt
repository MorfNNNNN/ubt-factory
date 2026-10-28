package ru.ubt.familyshield.shared.policy

import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.model.TimeWindow

/**
 * Plain-Russian lines for the «Журнал изменений»: what a guardian changed in a family member's rules.
 * The journal is visible to the whole family, so lines speak about rules, never about private data.
 */
object PolicyDiff {
    fun child(old: ChildPolicy, new: ChildPolicy): List<String> = buildList {
        limit("Лимит в будни", old.dailyLimitWeekdayMinutes, new.dailyLimitWeekdayMinutes)?.let(::add)
        limit("Лимит в выходные", old.dailyLimitWeekendMinutes, new.dailyLimitWeekendMinutes)?.let(::add)

        val oldSchedules = old.schedules.associateBy { it.id }
        val newSchedules = new.schedules.associateBy { it.id }
        newSchedules.values.filter { it.id !in oldSchedules }.forEach { add("Добавлено расписание «${it.name}» ${window(it.window)}") }
        oldSchedules.values.filter { it.id !in newSchedules }.forEach { add("Удалено расписание «${it.name}»") }
        newSchedules.values.forEach { s ->
            val before = oldSchedules[s.id] ?: return@forEach
            when {
                before.enabled != s.enabled -> add(if (s.enabled) "Включено расписание «${s.name}»" else "Выключено расписание «${s.name}»")
                before.window != s.window -> add("Расписание «${s.name}»: ${window(s.window)}")
                before != s -> add("Изменено расписание «${s.name}»")
            }
        }

        val oldBonus = old.bonuses.map { it.id }.toSet()
        new.bonuses.filter { it.id !in oldBonus }.forEach { add("Подарено +${it.minutes} мин" + it.reason.takeIf { r -> r.isNotBlank() }?.let { r -> " — $r" }.orEmpty()) }

        if (old.pausedUntil == null && new.pausedUntil != null) add("Телефон поставлен на паузу")
        if (old.pausedUntil != null && new.pausedUntil == null) add("Пауза снята")

        toggle(old.requireApprovalForNewApps, new.requireApprovalForNewApps, "Новые приложения — только с разрешения", "Новые приложения можно ставить без разрешения")?.let(::add)
        val approved = new.approvedPackages - old.approvedPackages
        if (approved.isNotEmpty()) add("Разрешено новых приложений: ${approved.size}")
        if (old.appRules != new.appRules) add("Изменены правила для приложений")
        if (old.categoryLimits != new.categoryLimits) add("Изменены лимиты по категориям приложений")

        toggle(old.webFilter.enabled, new.webFilter.enabled, "Безопасный интернет включён", "Безопасный интернет выключен")?.let(::add)
        if (old.webFilter.enabled == new.webFilter.enabled && old.webFilter != new.webFilter) add("Изменены настройки безопасного интернета")
        if (old.messageMonitoring != new.messageMonitoring) add("Изменены настройки оповещений об опасных переписках")

        geofences(old.geofences.map { it.name }, new.geofences.map { it.name }).forEach(::add)
        toggle(old.uninstallProtection, new.uninstallProtection, "Включена защита от удаления", "Выключена защита от удаления")?.let(::add)
        if (old.parentPin != new.parentPin) add(if (new.parentPin == null) "PIN родителя удалён" else "Задан новый PIN родителя")
        if (old.sosContacts != new.sosContacts) add("Изменены контакты для SOS")
        if (old.locationIntervalMinutes != new.locationIntervalMinutes) add("Местоположение обновляется раз в ${new.locationIntervalMinutes} мин")
    }

    fun elder(old: ElderPolicy, new: ElderPolicy): List<String> = buildList {
        val oc = old.callShield
        val nc = new.callShield
        toggle(oc.enabled && oc.blockKnownSpam, nc.enabled && nc.blockKnownSpam, "Включена блокировка мошеннических номеров", "Выключена блокировка мошеннических номеров")?.let(::add)
        toggle(oc.warnUnknown, nc.warnUnknown, "Включены подсказки о незнакомых номерах", "Выключены подсказки о незнакомых номерах")?.let(::add)
        toggle(oc.blockInternational, nc.blockInternational, "Звонки из-за рубежа блокируются", "Звонки из-за рубежа разрешены")?.let(::add)
        toggle(oc.blockHiddenNumbers, nc.blockHiddenNumbers, "Скрытые номера блокируются", "Скрытые номера разрешены")?.let(::add)
        toggle(old.inCallBankGuard, new.inCallBankGuard, "Включена тревога «банк во время звонка»", "Выключена тревога «банк во время звонка»")?.let(::add)
        toggle(old.otpGuard, new.otpGuard, "Включена защита кодов из СМС", "Выключена защита кодов из СМС")?.let(::add)
        toggle(old.remoteAccessGuard, new.remoteAccessGuard, "Включена защита от удалённого доступа", "Выключена защита от удалённого доступа")?.let(::add)
        if (new.remoteAccessAllowedUntil != null && new.remoteAccessAllowedUntil != old.remoteAccessAllowedUntil) {
            add("Разрешены программы удалённого доступа до ${formatTime(new.remoteAccessAllowedUntil)}")
        }
        toggle(old.messageScan, new.messageScan, "Включена проверка сообщений", "Выключена проверка сообщений")?.let(::add)
        toggle(old.linkScan, new.linkScan, "Включена проверка ссылок", "Выключена проверка ссылок")?.let(::add)
        toggle(old.sideloadGuard, new.sideloadGuard, "Включена защита от опасных приложений", "Выключена защита от опасных приложений")?.let(::add)
        toggle(old.bigDebitAlert, new.bigDebitAlert, "Включены оповещения о списаниях от ${rub(new.bigDebitThresholdRub)}", "Выключены оповещения о крупных списаниях")?.let(::add)
        if (old.bigDebitAlert && new.bigDebitAlert && old.bigDebitThresholdRub != new.bigDebitThresholdRub) add("Оповещать о списаниях от ${rub(new.bigDebitThresholdRub)}")
        toggle(old.checkIn.enabled, new.checkIn.enabled, "Включено ежедневное «Я в порядке» в ${formatMinuteOfDay(new.checkIn.reminderMinute)}", "Выключено ежедневное «Я в порядке»")?.let(::add)
        if (old.checkIn.enabled && new.checkIn.enabled && old.checkIn.reminderMinute != new.checkIn.reminderMinute) {
            add("«Я в порядке» теперь в ${formatMinuteOfDay(new.checkIn.reminderMinute)}")
        }
        if (old.inactivityHours != new.inactivityHours) add(new.inactivityHours?.let { "Тревога, если телефон не брали $it ч" } ?: "Тревога о бездействии выключена")
        toggle(old.fallDetection, new.fallDetection, "Включено определение падения", "Выключено определение падения")?.let(::add)
        val oldMeds = old.medications.associateBy { it.id }
        val newMeds = new.medications.associateBy { it.id }
        newMeds.values.filter { it.id !in oldMeds }.forEach { add("Добавлено напоминание о лекарстве «${it.name}»") }
        oldMeds.values.filter { it.id !in newMeds }.forEach { add("Удалено напоминание о лекарстве «${it.name}»") }
        toggle(old.simpleMode.enabled, new.simpleMode.enabled, "Включён простой режим", "Выключен простой режим")?.let(::add)
        if (old.coolingOffHours != new.coolingOffHours) add("Отключить защиту можно только через ${new.coolingOffHours} ч")
        geofences(old.geofences.map { it.name }, new.geofences.map { it.name }).forEach(::add)
        if (old.trustedContacts != new.trustedContacts) add("Изменены контакты «Позвонить родным»")
        if (old.medical != new.medical) add("Обновлена медицинская карта")
        toggle(old.webFilter.enabled, new.webFilter.enabled, "Включена защита от опасных сайтов", "Выключена защита от опасных сайтов")?.let(::add)
    }

    private fun limit(label: String, old: Int?, new: Int?): String? = when {
        old == new -> null
        new == null -> "$label снят"
        old == null -> "$label: ${formatMinutes(new)}"
        else -> "$label: ${formatMinutes(old)} → ${formatMinutes(new)}"
    }

    private fun toggle(old: Boolean, new: Boolean, on: String, off: String): String? = if (old == new) null else if (new) on else off

    private fun rub(amount: Int): String = formatRub(amount.toLong())

    private fun formatTime(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

    private fun window(w: TimeWindow) = "${formatMinuteOfDay(w.startMinute)}–${formatMinuteOfDay(w.endMinute)}"

    private fun geofences(old: List<String>, new: List<String>): List<String> =
        (new - old.toSet()).map { "Добавлена геозона «$it»" } + (old - new.toSet()).map { "Удалена геозона «$it»" }
}
