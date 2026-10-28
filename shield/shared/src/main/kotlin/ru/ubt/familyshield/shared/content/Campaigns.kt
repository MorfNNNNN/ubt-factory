package ru.ubt.familyshield.shared.content

import java.time.LocalDate
import java.time.MonthDay

/** What a seasonal banner opens. */
enum class CampaignAction { LESSON, GOS, INVITE, PREMIUM, CHILD_RULES }

/**
 * A seasonal nudge on the guardian's home screen, timed to when a scheme or a family occasion peaks.
 * The range may wrap around the new year ([from] after [to]).
 */
data class Campaign(
    val id: String,
    val title: String,
    val text: String,
    val button: String,
    val action: CampaignAction,
    val from: MonthDay,
    val to: MonthDay,
    val lessonId: String? = null,
) {
    fun covers(date: LocalDate): Boolean {
        val d = MonthDay.from(date)
        return if (!from.isAfter(to)) !d.isBefore(from) && !d.isAfter(to) else !d.isBefore(from) || !d.isAfter(to)
    }
}

object Campaigns {
    /** In priority order: the first one that covers the date wins. */
    val all: List<Campaign> = listOf(
        Campaign(
            "new_year", "Сезон мошенников",
            "Перед праздниками чаще звонят «из банка» и «Госуслуг». Пройдите с родителями урок — это 3 минуты.",
            "Открыть урок", CampaignAction.LESSON, MonthDay.of(12, 1), MonthDay.of(1, 15), lessonId = "bank_security",
        ),
        Campaign(
            "pension", "Звонки «из Соцфонда»",
            "В начале года мошенники обещают перерасчёт пенсии и выплаты. Покажите родителям урок о таких звонках.",
            "Открыть урок", CampaignAction.LESSON, MonthDay.of(1, 16), MonthDay.of(2, 15), lessonId = "pension_benefits",
        ),
        Campaign(
            "credit_ban", "Самозапрет на кредиты",
            "Бесплатно закройте оформление кредитов на себя и родителей. На Госуслугах это занимает пять минут.",
            "Как включить", CampaignAction.GOS, MonthDay.of(3, 1), MonthDay.of(3, 31),
        ),
        Campaign(
            "family_day", "День семьи, любви и верности",
            "8 июля — хороший повод собрать всю семью в приложении: добавьте бабушек, дедушек и детей.",
            "Добавить близких", CampaignAction.INVITE, MonthDay.of(7, 1), MonthDay.of(7, 10),
        ),
        Campaign(
            "summer", "Лето и отпуска",
            "Летом продают «горящие туры» и билеты по поддельным ссылкам. Расскажите подростку, как это распознать.",
            "Открыть урок", CampaignAction.LESSON, MonthDay.of(6, 1), MonthDay.of(8, 14), lessonId = "marketplace",
        ),
        Campaign(
            "back_to_school", "Скоро в школу",
            "Настройте расписание «Уроки» и геозону «Школа» — вы будете знать, что ребёнок дошёл.",
            "Настроить", CampaignAction.CHILD_RULES, MonthDay.of(8, 15), MonthDay.of(9, 15),
        ),
        Campaign(
            "elders_day", "День пожилых людей",
            "1 октября — подарите родителям защиту от мошеннических звонков и спокойствие себе.",
            "Подарить защиту", CampaignAction.PREMIUM, MonthDay.of(9, 25), MonthDay.of(10, 5),
        ),
        Campaign(
            "autumn", "Осенние схемы",
            "Осенью звонят «из газовой службы» и «поликлиники» и приходят домой. Пройдите с родителями урок.",
            "Открыть урок", CampaignAction.LESSON, MonthDay.of(10, 6), MonthDay.of(11, 30), lessonId = "home_visit",
        ),
    )

    fun active(date: LocalDate = LocalDate.now()): Campaign? = all.firstOrNull { it.covers(date) }
}
