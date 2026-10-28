package ru.ubt.familyshield.shared.content

import ru.ubt.familyshield.shared.model.AlertType

/**
 * «Почему мы так советуем»: for every event a guardian gets, why it matters and what to do right now.
 * [lessonId] points to a «Школа безопасности» lesson that explains the scheme; [callFirst] means the
 * best first step is to call the person.
 */
data class AlertAdvice(val why: String, val steps: List<String>, val lessonId: String? = null, val callFirst: Boolean = false)

object AlertAdvices {
    fun of(type: AlertType): AlertAdvice? = when (type) {
        AlertType.SOS -> AlertAdvice(
            "Близкий нажал кнопку SOS — так делают, когда нужна помощь прямо сейчас.",
            listOf("Позвоните сразу", "Если не отвечает — откройте местоположение", "Не дозвонились за 5 минут — звоните 112"),
            callFirst = true,
        )
        AlertType.FALL_DETECTED -> AlertAdvice(
            "Телефон зафиксировал резкое падение, а человек не ответил «Я в порядке».",
            listOf("Позвоните", "Посмотрите, где телефон", "Нет ответа — попросите соседей заглянуть или звоните 112"),
            callFirst = true,
        )
        AlertType.CHECK_IN_MISSED, AlertType.INACTIVITY -> AlertAdvice(
            "Близкий не отметился вовремя и давно не брал телефон в руки. Чаще всего телефон просто разрядился или остался в другой комнате.",
            listOf("Позвоните", "Проверьте заряд и время последней связи", "Договоритесь о привычном времени отметки"),
            callFirst = true,
        )
        AlertType.LOW_BATTERY -> AlertAdvice(
            "Когда телефон разряжен, защита не работает, а до близкого не дозвониться.",
            listOf("Напомните поставить телефон на зарядку", "Предложите держать зарядку у кровати"),
        )
        AlertType.SCAM_CALL_BLOCKED -> AlertAdvice(
            "Номер есть в базе жалоб: с него звонят мошенники. Мы не пропустили звонок — ничего делать не нужно.",
            listOf("Расскажите близкому, что такие звонки — обман", "Пройдите вместе урок о «службе безопасности банка»"),
            lessonId = "bank_security",
        )
        AlertType.SUSPICIOUS_CALL, AlertType.LONG_CALL_UNKNOWN -> AlertAdvice(
            "Долгий разговор с незнакомым номером — главный признак обмана: мошенники держат человека на линии и не дают подумать.",
            listOf("Позвоните близкому — второй звонок прерывает внушение", "Спросите, кто звонил и что просили", "Напомните: банк и полиция никогда не просят переводить деньги"),
            lessonId = "officials", callFirst = true,
        )
        AlertType.BANK_APP_DURING_CALL -> AlertAdvice(
            "Банк открыт во время разговора с незнакомцем. Так мошенники заставляют самому перевести деньги или назвать коды.",
            listOf("Позвоните немедленно", "Попросите положить трубку и ничего не переводить", "Если деньги ушли — сразу звоните в банк по номеру на карте"),
            lessonId = "bank_security", callFirst = true,
        )
        AlertType.OTP_DURING_CALL -> AlertAdvice(
            "Код из СМС пришёл во время звонка с незнакомого номера. Код — это подпись под операцией: кто его узнал, тот получил доступ.",
            listOf("Позвоните — пусть не называет код", "Если код уже назвали — блокируйте карту в банке", "Проверьте вход в Госуслуги"),
            lessonId = "gosuslugi", callFirst = true,
        )
        AlertType.REMOTE_ACCESS_APP -> AlertAdvice(
            "Открыта программа удалённого доступа. Через неё чужой человек видит экран, коды и может управлять телефоном.",
            listOf("Позвоните и попросите закрыть программу", "Удалите её, если её поставили по просьбе звонящего", "Смените пароль от банка"),
            lessonId = "remote_access", callFirst = true,
        )
        AlertType.SCAM_MESSAGE -> AlertAdvice(
            "В сообщении — типичные приёмы мошенников: срочность, просьба о деньгах или коде, ссылка «для отмены».",
            listOf("Предупредите близкого не отвечать и не переходить по ссылкам", "Проверьте отправителя звонком по известному номеру"),
            lessonId = "relative_trouble",
        )
        AlertType.PHISHING_LINK -> AlertAdvice(
            "Ссылка ведёт на поддельный сайт: он копирует банк или Госуслуги, чтобы украсть пароль и данные карты.",
            listOf("Попросите не вводить на сайте никакие данные", "Если ввели — смените пароль и позвоните в банк"),
            lessonId = "phishing_links",
        )
        AlertType.RISKY_APP_INSTALLED -> AlertAdvice(
            "Приложение установлено не из магазина. Так распространяются вирусы, которые читают СМС и крадут деньги.",
            listOf("Узнайте, кто попросил его установить", "Удалите приложение", "Запустите проверку безопасности телефона"),
            lessonId = "remote_access",
        )
        AlertType.NEW_APP_INSTALLED -> AlertAdvice(
            "На телефоне появилось новое приложение. Это повод спокойно поговорить, что это и зачем.",
            listOf("Посмотрите, что за приложение", "Если сомневаетесь — ограничьте его в правилах"),
        )
        AlertType.DEVICE_SECURITY_ISSUE -> AlertAdvice(
            "На телефоне найдена уязвимая настройка: без блокировки экрана или с устаревшей системой проще украсть данные.",
            listOf("Откройте «Безопасность телефона»", "Помогите исправить пункты по списку"),
        )
        AlertType.PROTECTION_CHANGE_REQUESTED -> AlertAdvice(
            "Кто-то пытается отключить защиту. Мошенники часто уговаривают «временно выключить антивирус».",
            listOf("Позвоните и спросите, кто попросил", "Не одобряйте, если просьба пришла от звонящего"),
            lessonId = "remote_access", callFirst = true,
        )
        AlertType.CHILD_RISK_MESSAGE -> AlertAdvice(
            "В переписке найдены признаки травли, угроз или вымогательства. Ребёнку сейчас может быть страшно.",
            listOf("Поговорите спокойно, без упрёков", "Не удаляйте переписку — она может понадобиться", "При угрозах обратитесь в школу или полицию"),
        )
        AlertType.BLOCKED_SITE -> AlertAdvice(
            "Фильтр не пустил на опасный сайт. Ничего делать не нужно, но это хороший повод поговорить.",
            listOf("Спросите, как ребёнок попал на сайт", "Если сайт нужен для учёбы — разрешите его"),
        )
        AlertType.FILTER_BYPASS -> AlertAdvice(
            "Похоже, на телефоне включили VPN или анонимайзер — они отключают защиту от опасных сайтов.",
            listOf("Поговорите о причинах", "Договоритесь о правилах вместо запретов"),
        )
        AlertType.LIMIT_REACHED -> AlertAdvice(
            "Время на сегодня закончилось. Ребёнок может попросить ещё — ответ придёт к вам.",
            listOf("Если есть повод — добавьте минуты", "Предложите задание за дополнительное время"),
        )
        AlertType.GEOFENCE_ENTER, AlertType.GEOFENCE_EXIT -> AlertAdvice(
            "Телефон пересёк границу геозоны, которую вы настроили.",
            listOf("Откройте хронологию дня, чтобы увидеть маршрут"),
        )
        AlertType.TAMPER, AlertType.PERMISSION_REVOKED, AlertType.UNINSTALL_ATTEMPT -> AlertAdvice(
            "Защиту пытались отключить или удалить. Без неё вы не узнаете об опасности.",
            listOf("Позвоните и спросите, что случилось", "Помогите снова включить защиту"),
            callFirst = true,
        )
        AlertType.MEDICATION_MISSED -> AlertAdvice(
            "Напоминание о лекарстве осталось без ответа.",
            listOf("Позвоните и мягко напомните", "Проверьте, удобное ли время напоминания"),
            callFirst = true,
        )
        AlertType.CHECK_IN_OK, AlertType.MEDICATION_TAKEN, AlertType.TASK_DONE -> null
    }
}
