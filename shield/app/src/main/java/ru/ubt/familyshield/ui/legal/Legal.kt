package ru.ubt.familyshield.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.ui.components.ScreenScaffold

/** In-app copies of the legal texts and help, kept in sync with docs/ in the repository. */
object LegalScreen {
    @Composable
    fun Privacy(onBack: () -> Unit) = Article("Конфиденциальность", PRIVACY, onBack)

    @Composable
    fun Terms(onBack: () -> Unit) = Article("Условия использования", TERMS, onBack)

    @Composable
    fun Help(onBack: () -> Unit) = Article("Как это работает", HELP, onBack)

    @Composable
    private fun Article(title: String, blocks: List<Pair<String, String>>, onBack: () -> Unit) {
        ScreenScaffold(title = title, onBack = onBack) { padding ->
            Column(Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                blocks.forEach { (h, body) ->
                    if (h.isNotEmpty()) Text(h, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(body, style = MaterialTheme.typography.bodyLarge)
                }
                Text("Дата последнего обновления: 20.10.2026", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("", Modifier.padding(bottom = 24.dp))
            }
        }
    }

    private val PRIVACY = listOf(
        "" to "«Семейный щит» создан, чтобы защищать семью, а не следить за ней. Мы собираем минимум данных и шифруем их так, что даже мы не можем их прочитать.",
        "Что остаётся только на телефоне" to "Анализ звонков, сообщений и ссылок выполняется прямо на устройстве. Тексты сообщений, список контактов и содержимое экрана никуда не передаются.",
        "Что видит семья" to "Другие участники семьи получают то, что нужно для заботы: предупреждения об угрозах, краткие выдержки из опасных сообщений, местоположение, экранное время и список приложений. Всё это передаётся в зашифрованном виде.",
        "Сквозное шифрование" to "Данные шифруются ключом, который хранится только на телефонах вашей семьи. Наш сервер передаёт зашифрованные сообщения, но не может их расшифровать.",
        "Что хранит сервер" to "Только анонимный идентификатор устройства, зашифрованные сообщения до доставки и обезличенные жалобы на мошеннические номера (в виде необратимых хешей). Ни имён, ни телефонов, ни переписки.",
        "Дети" to "Приложение на детском телефоне устанавливает взрослый. Ребёнок видит честное уведомление о том, что телефон под защитой семьи, и что именно видят родители.",
        "Ваши права" to "Вы можете в любой момент выйти из семьи и удалить все данные устройства одной кнопкой в настройках. Данные обрабатываются на серверах в России в соответствии с 152-ФЗ.",
        "Контакты" to (ru.ubt.familyshield.BuildConfig.SUPPORT_EMAIL.takeIf { it.isNotBlank() }?.let { "Вопросы о данных: $it" }
            ?: "Контакты оператора персональных данных указаны на странице приложения в RuStore."),
    )

    private val TERMS = listOf(
        "" to "Используя «Семейный щит», вы соглашаетесь с этими условиями.",
        "Назначение" to "Приложение предназначено для защиты членов вашей семьи с их ведома. Устанавливать контроль на чужой телефон без согласия владельца запрещено. Для детей согласие даёт родитель или законный представитель.",
        "Чего приложение не делает" to "«Семейный щит» не гарантирует стопроцентную защиту от мошенников и не заменяет внимательность. Это помощник, а не замена здравому смыслу.",
        "Подписки" to "Платные функции предоставляются по подписке или разовой покупкой через RuStore. Подписка продлевается автоматически; отменить её можно в RuStore. Условия возврата регулируются правилами RuStore.",
        "Ответственность" to "Мы не несём ответственности за решения, принятые на основе предупреждений приложения, за действия третьих лиц и за сбои в работе сети или устройства.",
        "Изменения" to "Условия могут обновляться. Продолжая пользоваться приложением, вы принимаете обновлённые условия.",
    )

    private val HELP = listOf(
        "Нужен ли интернет?" to "Да, чтобы телефоны семьи обменивались предупреждениями. Защита от звонков и анализ сообщений работают и без интернета, а данные отправятся, когда связь появится.",
        "Видит ли приложение мою переписку?" to "Нет. Приложение само ищет на телефоне признаки мошенничества и угроз. Родителям приходит только предупреждение и короткая выдержка, а не весь разговор.",
        "Почему так много разрешений?" to "Каждое разрешение включает конкретную защиту: статистика приложений — для экранного времени, доступ к звонкам — для блокировки мошенников, геолокация — для SOS и карты. Мастер настройки объясняет каждое и позволяет включить не все сразу.",
        "Как подключить телефон близкого?" to "Создайте приглашение, дайте близкому установить приложение и отсканировать QR-код. Всё настроится автоматически.",
        "Безопасно ли это?" to "Данные шифруются на устройствах вашим семейным ключом. Даже наш сервер не может их прочитать.",
        "Если родственник далеко?" to "Всё настраивается удалённо. Для пожилых есть простой режим с крупными кнопками, чтобы им было удобно.",
    )
}
