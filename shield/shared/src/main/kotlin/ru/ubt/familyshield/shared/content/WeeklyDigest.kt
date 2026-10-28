package ru.ubt.familyshield.shared.content

import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.policy.formatMinutes
import java.time.LocalDate

/**
 * «Неделя семьи»: what the family's protection did in seven days. The guardian's phone counts events into
 * daily rows as they arrive, so the numbers survive even when the plan keeps the details for one day only.
 * Event counters are named after [AlertType]; the extra ones are below.
 */
object WeekMetric {
    /** Any HIGH or CRITICAL event. */
    const val DANGER = "DANGER"

    /** Screen minutes of the day: the latest report replaces the value, it is not a sum. */
    const val SCREEN = "SCREEN"

    /** Lessons of «Школа безопасности» passed. */
    const val LESSON = "LESSON"
}

/** One counter of one member on one day ([date] is ISO yyyy-MM-dd in the guardian's time zone). */
data class DayStat(val date: String, val deviceId: String, val metric: String, val value: Int)

data class WeekNumbers(
    val dangerous: Int = 0,
    val scamCalls: Int = 0,
    val scamMessages: Int = 0,
    val sitesBlocked: Int = 0,
    val checkIns: Int = 0,
    val tasksDone: Int = 0,
    val lessons: Int = 0,
    val sos: Int = 0,
    /** Sum of daily screen minutes and the number of member-days it covers. */
    val screenMinutes: Int = 0,
    val screenDays: Int = 0,
) {
    val avgScreenMinutes: Int? get() = if (screenDays == 0) null else screenMinutes / screenDays
    val isQuiet: Boolean get() = dangerous == 0 && sos == 0

    companion object {
        fun of(rows: Iterable<DayStat>): WeekNumbers {
            val list = rows.toList()
            fun sum(vararg metrics: String) = list.filter { it.metric in metrics }.sumOf { it.value }
            val screen = list.filter { it.metric == WeekMetric.SCREEN && it.value > 0 }
            return WeekNumbers(
                dangerous = sum(WeekMetric.DANGER),
                scamCalls = sum(AlertType.SCAM_CALL_BLOCKED.name),
                scamMessages = sum(AlertType.SCAM_MESSAGE.name, AlertType.PHISHING_LINK.name),
                sitesBlocked = sum(AlertType.BLOCKED_SITE.name),
                checkIns = sum(AlertType.CHECK_IN_OK.name),
                tasksDone = sum(AlertType.TASK_DONE.name),
                lessons = sum(WeekMetric.LESSON),
                sos = sum(AlertType.SOS.name),
                screenMinutes = screen.sumOf { it.value },
                screenDays = screen.size,
            )
        }
    }
}

/** A suggestion for the coming week, tied to a «Школа безопасности» lesson when one fits. */
data class WeekTip(val text: String, val lessonId: String? = null)

object WeeklyDigest {
    /** The seven days ending [today] (ISO dates, inclusive). */
    fun thisWeek(today: LocalDate): ClosedRange<String> = today.minusDays(6).toString()..today.toString()

    /** The seven days before [thisWeek]. */
    fun previousWeek(today: LocalDate): ClosedRange<String> = today.minusDays(13).toString()..today.minusDays(7).toString()

    fun title(w: WeekNumbers): String = when {
        w.sos > 0 -> "Неделя семьи: был сигнал SOS"
        w.dangerous > 0 -> "Неделя семьи: ${w.dangerous} ${plural(w.dangerous, "опасный момент", "опасных момента", "опасных моментов")}"
        else -> "Неделя семьи: всё спокойно"
    }

    /** Non-zero results of the week, most important first. Phrased so the verb never depends on the number. */
    fun highlights(w: WeekNumbers): List<String> = buildList {
        if (w.scamCalls > 0) add("Не пропустили ${w.scamCalls} ${plural(w.scamCalls, "звонок", "звонка", "звонков")} мошенников")
        if (w.scamMessages > 0) add("Нашли ${w.scamMessages} ${plural(w.scamMessages, "опасное сообщение", "опасных сообщения", "опасных сообщений")}")
        if (w.sitesBlocked > 0) add("Закрыли ${w.sitesBlocked} ${plural(w.sitesBlocked, "опасный сайт", "опасных сайта", "опасных сайтов")}")
        if (w.checkIns > 0) add("Получили ${w.checkIns} ${plural(w.checkIns, "отметку", "отметки", "отметок")} «Я в порядке»")
        if (w.tasksDone > 0) add("Выполнено ${w.tasksDone} ${plural(w.tasksDone, "задание", "задания", "заданий")}")
        if (w.lessons > 0) add("Прошли ${w.lessons} ${plural(w.lessons, "урок", "урока", "уроков")} безопасности")
        w.avgScreenMinutes?.let { add("Экран у детей — в среднем ${formatMinutes(it)} в день") }
    }

    /** Notification body: up to three highlights, or a calm line when the week was empty. */
    fun text(w: WeekNumbers): String =
        highlights(w).take(3).joinToString(" · ").ifBlank { "Все телефоны семьи под защитой. Загляните в итоги недели" }

    /** "+2 к прошлой неделе"; null when both weeks are empty. */
    fun trend(now: Int, before: Int): String? = when {
        now == 0 && before == 0 -> null
        now > before -> "+${now - before} к прошлой неделе"
        now < before -> "−${before - now} к прошлой неделе"
        else -> "как на прошлой неделе"
    }

    /** What to do next week, based on what happened in this one. [weekOfYear] rotates the default lesson. */
    fun tip(w: WeekNumbers, weekOfYear: Int): WeekTip = when {
        w.sos > 0 -> WeekTip("Был сигнал SOS. Проверьте вместе, что в SOS указаны нужные номера, и обсудите, как действовать в беде.")
        w.scamCalls > 0 ->
            WeekTip("Мошенники пытались дозвониться. Пройдите вместе урок о «службе безопасности банка» — это 3 минуты.", "bank_security")
        w.scamMessages > 0 ->
            WeekTip("Приходили опасные сообщения. Урок о поддельных ссылках поможет отличать их с первого взгляда.", "phishing_links")
        w.dangerous > 0 ->
            WeekTip("Были тревожные события. Откройте их в «Событиях»: у каждого есть подсказка, что делать.")
        w.avgScreenMinutes?.let { it > 180 } == true ->
            WeekTip("Экранного времени много. Предложите ребёнку задания: минуты за уборку, чтение и спорт.")
        else -> {
            val lesson = SafetySchool.lessons[Math.floorMod(weekOfYear, SafetySchool.lessons.size)]
            WeekTip("Урок недели: ${lesson.title}. Короткий рассказ и три вопроса — пройдите вместе с близкими.", lesson.id)
        }
    }
}
