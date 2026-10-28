package ru.ubt.familyshield.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.PersonAddAlt1
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.assigneeLine
import ru.ubt.familyshield.core.family.eventOrNull
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.core.family.reminderLeadLabel
import ru.ubt.familyshield.shared.model.EventKind
import ru.ubt.familyshield.shared.model.EventOccurrence
import ru.ubt.familyshield.shared.model.EventRepeat
import ru.ubt.familyshield.shared.model.FamilyEvent
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.occurrencesOf
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LabelChip
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SectionHeader
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.Visual
import ru.ubt.familyshield.ui.components.timeOf
import ru.ubt.familyshield.ui.guardian.ArtEmptyState
import ru.ubt.familyshield.ui.guardian.ChoiceChip
import ru.ubt.familyshield.ui.guardian.DangerAction
import ru.ubt.familyshield.ui.guardian.DetailScaffold
import ru.ubt.familyshield.ui.guardian.PillButton
import ru.ubt.familyshield.ui.guardian.RoundIconButton
import ru.ubt.familyshield.ui.onboarding.ShieldTextField
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

/*
 * «Семейный календарь»: who picks up whom, clubs, doctors and birthdays. Guardians plan, everyone it
 * concerns sees the plan on their phone and gets a reminder in time.
 */

private val RU = Locale.forLanguageTag("ru")
private const val DAY = 86_400_000L

fun eventVisual(kind: EventKind): Visual = when (kind) {
    EventKind.PICKUP -> Visual(Icons.Rounded.DirectionsCar, Gradients.Sky)
    EventKind.CLUB -> Visual(Icons.Rounded.SportsSoccer, Gradients.Sunrise)
    EventKind.SCHOOL -> Visual(Icons.Rounded.School, Gradients.Violet)
    EventKind.DOCTOR -> Visual(Icons.Rounded.LocalHospital, Gradients.Coral)
    EventKind.BIRTHDAY -> Visual(Icons.Rounded.Cake, Gradients.Gold)
    EventKind.VISIT -> Visual(Icons.Rounded.Home, Gradients.Emerald)
    EventKind.OTHER -> Visual(Icons.Rounded.Event, Gradients.Slate)
}

private fun defaultTitle(kind: EventKind): String = when (kind) {
    EventKind.PICKUP -> "Забрать из школы"
    EventKind.CLUB -> "Тренировка"
    EventKind.SCHOOL -> "Родительское собрание"
    EventKind.DOCTOR -> "Приём у врача"
    EventKind.BIRTHDAY -> "День рождения"
    EventKind.VISIT -> "Семейный ужин"
    EventKind.OTHER -> ""
}

private fun defaultTime(kind: EventKind): LocalTime = when (kind) {
    EventKind.PICKUP -> LocalTime.of(14, 0)
    EventKind.CLUB -> LocalTime.of(17, 0)
    EventKind.SCHOOL -> LocalTime.of(18, 30)
    EventKind.DOCTOR -> LocalTime.of(10, 0)
    EventKind.BIRTHDAY -> LocalTime.of(12, 0)
    EventKind.VISIT -> LocalTime.of(19, 0)
    EventKind.OTHER -> LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
}

/** "по будням", "по вт и чт", "каждый год"; null for one-off events. */
fun repeatShort(event: FamilyEvent): String? = when (event.repeat) {
    EventRepeat.ONCE -> null
    EventRepeat.YEARLY -> "каждый год"
    EventRepeat.WEEKLY -> {
        val days = event.days.ifEmpty { setOf(event.startsAt.localDate().dayOfWeek.value) }.sorted()
        when (days) {
            (1..5).toList() -> "по будням"
            (6..7).toList() -> "по выходным"
            (1..7).toList() -> "каждый день"
            else -> {
                val names = days.map { weekdayNames[it - 1] }
                "по " + if (names.size == 1) names.single() else names.dropLast(1).joinToString(", ") + " и " + names.last()
            }
        }
    }
}

/** "Сегодня", "Завтра", "Пятница, 30 октября". */
fun dayLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
    today -> "Сегодня"
    today.plusDays(1) -> "Завтра"
    else -> date.dayOfWeek.getDisplayName(TextStyle.FULL_STANDALONE, RU).replaceFirstChar { it.uppercase() } +
        ", " + date.format(DateTimeFormatter.ofPattern("d MMMM", RU))
}

private fun Long.localDate(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun LocalDate.startMillis(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

@Composable
internal fun rememberEvents(graph: AppGraph): List<FamilyEvent> {
    val events by remember { graph.db.calendar().observeAll().map { rows -> rows.mapNotNull { it.eventOrNull() } } }.collectAsState(emptyList())
    return events
}

/** Occurrences from the start of today for [days] days. */
internal fun upcoming(events: List<FamilyEvent>, days: Int, today: LocalDate = LocalDate.now()): List<EventOccurrence> {
    val from = today.startMillis()
    return occurrencesOf(events, from, from + days * DAY)
}

private class EditorRequest(val event: FamilyEvent?, val kind: EventKind)

// ---- Guardian: the calendar ----------------------------------------------------------------------------

@Composable
fun FamilyCalendarScreen(graph: AppGraph, state: FamilyUiState, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val events = rememberEvents(graph)
    var editor by remember { mutableStateOf<EditorRequest?>(null) }
    editor?.let { request ->
        EventEditor(graph, state, request.event, request.kind) { editor = null }
        return
    }
    val today = LocalDate.now()
    var selected by rememberSaveable { mutableStateOf(today.toString()) }
    val day = LocalDate.parse(selected)
    val twoWeeks = upcoming(events, 14, today)
    val me = state.settings.deviceId

    DetailScaffold(
        title = "Семейный календарь",
        onBack = onBack,
        actions = { RoundIconButton(Icons.Rounded.Add, "Добавить дело", { editor = EditorRequest(null, EventKind.PICKUP) }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (events.isEmpty()) {
                SoftCard {
                    ArtEmptyState(
                        title = "Календарь семьи",
                        text = "Кто кого забирает, кружки, врачи и дни рождения — в одном месте. Напомним каждому вовремя.",
                    ) { Illustration(Art3D.Calendar, size = 112.dp) }
                }
                SectionHeader("С чего начать")
                QuickKinds { editor = EditorRequest(null, it) }
            } else {
                WeekStrip(today, day, twoWeeks) { selected = it.toString() }
                SectionHeader(dayLabel(day, today))
                val ofDay = twoWeeks.filter { it.startsAt.localDate() == day }
                if (ofDay.isEmpty()) {
                    Text(
                        "На этот день дел нет.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp),
                    )
                } else {
                    SoftCard(padding = 12.dp) {
                        ofDay.forEach { occ ->
                            EventRow(occ, state.members, me, showDate = false,
                                onTake = takeAction(occ.event, state) { scope.launch { graph.calendar.take(occ.event.id) } },
                            ) { editor = EditorRequest(occ.event, occ.event.kind) }
                        }
                    }
                }
                // The nearest time of every event: a weekday pickup should not fill the whole list.
                val shown = ofDay.map { it.event.id }.toSet()
                val later = twoWeeks.filter { it.startsAt.localDate().isAfter(day) && it.event.id !in shown }.distinctBy { it.event.id }.take(8)
                if (later.isNotEmpty()) {
                    SectionHeader("Дальше")
                    SoftCard(padding = 12.dp) {
                        later.forEach { occ ->
                            EventRow(occ, state.members, me, showDate = true,
                                onTake = takeAction(occ.event, state) { scope.launch { graph.calendar.take(occ.event.id) } },
                            ) { editor = EditorRequest(occ.event, occ.event.kind) }
                        }
                    }
                }
                SectionHeader("Добавить")
                QuickKinds { editor = EditorRequest(null, it) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "Возьму" for guardians while nobody has taken the event. */
private fun takeAction(event: FamilyEvent, state: FamilyUiState, take: () -> Unit): (() -> Unit)? =
    if (event.assigneeDeviceId == null && state.settings.isGuardian && event.kind != EventKind.BIRTHDAY) take else null

@Composable
private fun WeekStrip(today: LocalDate, selected: LocalDate, occurrences: List<EventOccurrence>, onSelect: (LocalDate) -> Unit) {
    val busy = occurrences.map { it.startsAt.localDate() }.toSet()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0L until 7L).map { today.plusDays(it) }.forEach { d ->
            val isSelected = d == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .clickable(role = Role.Tab) { onSelect(d) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                val content = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Text(
                    d.dayOfWeek.getDisplayName(TextStyle.SHORT_STANDALONE, RU).take(2), style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) content else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("${d.dayOfMonth}", style = MaterialTheme.typography.titleMedium, color = content)
                Box(
                    Modifier.size(6.dp).clip(CircleShape)
                        .background(if (d in busy) (if (isSelected) Color.White else MaterialTheme.colorScheme.primary) else Color.Transparent),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventRow(
    occ: EventOccurrence,
    members: List<MemberEntity>,
    me: String?,
    showDate: Boolean,
    onTake: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val e = occ.event
    val visual = eventVisual(e.kind)
    val whom = forNames(e, members)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(timeOf(occ.startsAt), style = MaterialTheme.typography.titleMedium)
            if (showDate) {
                Text(
                    occ.startsAt.localDate().format(DateTimeFormatter.ofPattern("d MMM", RU)),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                )
            }
        }
        IconTile(visual.icon, visual.gradient, size = 42.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(e.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(whom, repeatShort(e), e.place.ifBlank { null }).joinToString(" · ")
            if (details.isNotBlank()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            val line = assigneeLine(e, me)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    line != null -> LabelChip(line, Brand.GreenLight, Brand.GreenDark)
                    e.kind != EventKind.BIRTHDAY -> LabelChip("Никто не взял", Brand.WarningLight, Brand.Warning)
                }
                if (onTake != null) {
                    Box(
                        Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                            .clickable(role = Role.Button, onClick = onTake).padding(horizontal = 12.dp, vertical = 4.dp),
                    ) { Text("Возьму", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary) }
                }
            }
        }
    }
}

/** "Миша, Аня" or "Вся семья". */
private fun forNames(e: FamilyEvent, members: List<MemberEntity>): String? {
    if (e.forDeviceIds.isEmpty()) return "Вся семья"
    val names = e.forDeviceIds.mapNotNull { id -> members.firstOrNull { it.deviceId == id }?.let { m -> if (m.isSelf) "Я" else m.displayName.ifBlank { null } } }
    return names.joinToString(", ").ifBlank { null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickKinds(onPick: (EventKind) -> Unit) {
    val kinds = listOf(EventKind.PICKUP, EventKind.CLUB, EventKind.DOCTOR, EventKind.BIRTHDAY, EventKind.SCHOOL, EventKind.OTHER)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        kinds.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { kind ->
                    val v = eventVisual(kind)
                    // Icon above the text: a narrow column next to the icon would break long words.
                    SoftCard(Modifier.weight(1f), onClick = { onPick(kind) }, padding = 14.dp) {
                        IconTile(v.icon, v.gradient, size = 40.dp)
                        Text(
                            if (kind == EventKind.OTHER) "Своё дело" else defaultTitle(kind), style = MaterialTheme.typography.titleSmall,
                            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

// ---- Guardian: editor ----------------------------------------------------------------------------------

private val leadOptions: List<Int?> = listOf(null, 15, 30, 60, 120, 24 * 60)
private val weekdayNames = listOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun EventEditor(graph: AppGraph, state: FamilyUiState, existing: FamilyEvent?, startKind: EventKind, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val me = state.settings.deviceId
    val zone = ZoneId.systemDefault()
    val start = existing?.let { Instant.ofEpochMilli(it.startsAt).atZone(zone) }

    var kind by remember { mutableStateOf(existing?.kind ?: startKind) }
    var title by remember { mutableStateOf(existing?.title ?: defaultTitle(startKind)) }
    var forIds by remember {
        // A pickup or a club is usually about a child, a doctor visit about the elder.
        val likely = when (startKind) {
            EventKind.PICKUP, EventKind.CLUB, EventKind.SCHOOL -> MemberRole.CHILD
            EventKind.DOCTOR -> MemberRole.ELDER
            else -> null
        }
        mutableStateOf(existing?.forDeviceIds ?: setOfNotNull(state.others.firstOrNull { likely != null && it.memberRole == likely }?.deviceId))
    }
    var assignee by remember {
        mutableStateOf(existing?.assigneeDeviceId ?: me.takeIf { startKind in setOf(EventKind.PICKUP, EventKind.DOCTOR, EventKind.VISIT) })
    }
    var date by remember {
        // A pickup at 14:00 suggested at 15:00 means tomorrow.
        val today = LocalDate.now()
        mutableStateOf(start?.toLocalDate() ?: if (today.atTime(defaultTime(startKind)).isBefore(java.time.LocalDateTime.now())) today.plusDays(1) else today)
    }
    var hour by remember { mutableIntStateOf(start?.hour ?: defaultTime(startKind).hour) }
    var minute by remember { mutableIntStateOf(start?.minute ?: defaultTime(startKind).minute) }
    var repeat by remember {
        mutableStateOf(existing?.repeat ?: when (startKind) { EventKind.CLUB -> EventRepeat.WEEKLY; EventKind.BIRTHDAY -> EventRepeat.YEARLY; else -> EventRepeat.ONCE })
    }
    var days by remember { mutableStateOf(existing?.days ?: emptySet()) }
    var lead by remember {
        mutableStateOf(if (existing != null) existing.remindMinutesBefore else when (startKind) { EventKind.BIRTHDAY -> 24 * 60; EventKind.DOCTOR -> 120; else -> 30 })
    }
    var place by remember { mutableStateOf(existing?.place.orEmpty()) }
    var note by remember { mutableStateOf(existing?.note.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    val people = state.members
    val adults = people.filter { it.memberRole == MemberRole.GUARDIAN || it.memberRole == MemberRole.ELDER }
    fun nameOf(id: String?): String = people.firstOrNull { it.deviceId == id }?.let { if (it.isSelf) state.settings.displayName else it.displayName }.orEmpty()

    DetailScaffold(title = if (existing == null) "Новое дело" else "Изменить дело", onBack = onClose) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SoftCard(padding = 16.dp) {
                EditorLabel("Что за дело")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventKind.entries.forEach { k ->
                        ChoiceChip(k.titleRu, kind == k, icon = eventVisual(k).icon) {
                            if (title.isBlank() || title == defaultTitle(kind)) title = defaultTitle(k)
                            kind = k
                        }
                    }
                }
                ShieldTextField(title, { title = it.take(80) }, "Название")
            }

            SoftCard(padding = 16.dp) {
                EditorLabel("Для кого")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Вся семья", forIds.isEmpty()) { forIds = emptySet() }
                    people.forEach { m ->
                        PersonChip(if (m.isSelf) "Я" else m.displayName, m.avatar, m.role, m.deviceId in forIds) {
                            forIds = if (m.deviceId in forIds) forIds - m.deviceId else forIds + m.deviceId
                        }
                    }
                }
                if (kind != EventKind.BIRTHDAY) {
                    EditorLabel(if (kind == EventKind.PICKUP) "Кто заберёт" else "Кто займётся")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        adults.forEach { m ->
                            PersonChip(if (m.isSelf) "Я" else m.displayName, m.avatar, m.role, assignee == m.deviceId) { assignee = m.deviceId }
                        }
                        ChoiceChip("Пока никто", assignee == null, icon = Icons.Rounded.PersonAddAlt1) { assignee = null }
                    }
                }
            }

            SoftCard(padding = 16.dp) {
                EditorLabel("Когда")
                val today = LocalDate.now()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Сегодня", date == today) { date = today }
                    ChoiceChip("Завтра", date == today.plusDays(1)) { date = today.plusDays(1) }
                    val other = date != today && date != today.plusDays(1)
                    ChoiceChip(if (other) date.format(DateTimeFormatter.ofPattern("d MMMM", RU)) else "Другой день", other, icon = Icons.Rounded.Event) { pickDate = true }
                    ChoiceChip("в %02d:%02d".format(hour, minute), true, icon = Icons.Rounded.Schedule) { pickTime = true }
                }
                val past = repeat == EventRepeat.ONCE && date.atTime(hour, minute).isBefore(java.time.LocalDateTime.now())
                if (past) {
                    Text("Это время уже прошло — выберите другой день или время.", style = MaterialTheme.typography.bodySmall, color = Brand.Warning)
                }
                EditorLabel("Повторять")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Один раз", repeat == EventRepeat.ONCE) { repeat = EventRepeat.ONCE }
                    ChoiceChip("Каждую неделю", repeat == EventRepeat.WEEKLY) { repeat = EventRepeat.WEEKLY }
                    ChoiceChip("Каждый год", repeat == EventRepeat.YEARLY) { repeat = EventRepeat.YEARLY }
                }
                if (repeat == EventRepeat.WEEKLY) {
                    val chosen = days.ifEmpty { setOf(date.dayOfWeek.value) }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        weekdayNames.forEachIndexed { i, label ->
                            val iso = i + 1
                            DayToggle(label, iso in chosen, Modifier.weight(1f)) {
                                val next = if (iso in chosen) chosen - iso else chosen + iso
                                days = next.ifEmpty { chosen }
                            }
                        }
                    }
                }
                EditorLabel("Напомнить")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    leadOptions.forEach { option ->
                        ChoiceChip(option?.let(::reminderLeadLabel) ?: "Не напоминать", lead == option) { lead = option }
                    }
                }
            }

            SoftCard(padding = 16.dp) {
                ShieldTextField(place, { place = it.take(80) }, "Где (необязательно)")
                ShieldTextField(note, { note = it.take(300) }, "Заметка: что взять, кому позвонить", singleLine = false)
            }

            val inPast = repeat == EventRepeat.ONCE && date.atTime(hour, minute).isBefore(java.time.LocalDateTime.now())
            PrimaryButton("Сохранить", enabled = title.isNotBlank() && !inPast) {
                val now = System.currentTimeMillis()
                val startsAt = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
                val event = FamilyEvent(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    title = title,
                    kind = kind,
                    startsAt = startsAt,
                    repeat = repeat,
                    days = if (repeat == EventRepeat.WEEKLY) days else emptySet(),
                    forDeviceIds = forIds,
                    assigneeDeviceId = assignee.takeIf { kind != EventKind.BIRTHDAY },
                    assigneeName = if (kind == EventKind.BIRTHDAY) "" else nameOf(assignee),
                    place = place,
                    note = note,
                    remindMinutesBefore = lead,
                    createdBy = existing?.createdBy ?: me.orEmpty(),
                    createdAt = existing?.createdAt ?: now,
                )
                scope.launch {
                    graph.calendar.save(event)
                    onClose()
                }
            }
            if (existing != null) {
                DangerAction("Удалить из календаря", Icons.Rounded.DeleteOutline, {
                    scope.launch {
                        graph.calendar.delete(existing.id)
                        onClose()
                    }
                }, Modifier.align(Alignment.CenterHorizontally))
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickDate) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    pickDate = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Отмена") } },
        ) { DatePicker(state = picker) }
    }
    if (pickTime) {
        val picker = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Время") },
            text = { TimePicker(state = picker) },
            confirmButton = {
                TextButton(onClick = {
                    hour = picker.hour
                    minute = picker.minute
                    pickTime = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Отмена") } },
            containerColor = MaterialTheme.colorScheme.surface,
        )
    }
}

@Composable
private fun EditorLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A member to choose: portrait and name in a pill. */
@Composable
private fun PersonChip(name: String, avatar: String, role: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AvatarImage(avatar, role, 28.dp)
        Text(
            name.ifBlank { "Близкий" }, style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun DayToggle(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(40.dp).clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Checkbox, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ---- Home cards --------------------------------------------------------------------------------------

/**
 * Guardian home: today's plan, or the nearest event, or an invitation to start the calendar.
 */
@Composable
fun CalendarHomeCard(graph: AppGraph, state: FamilyUiState, onOpen: () -> Unit) {
    val events = rememberEvents(graph)
    val today = LocalDate.now()
    val soon = upcoming(events, 7, today).filter { it.startsAt >= System.currentTimeMillis() - 60 * 60_000L }
    val todays = soon.filter { it.startsAt.localDate() == today }
    SoftCard(onClick = onOpen, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Illustration(Art3D.Calendar, size = 48.dp)
            Column(Modifier.weight(1f)) {
                Text(if (todays.isNotEmpty()) "Сегодня в семье" else "Семейный календарь", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        events.isEmpty() -> "Кто кого забирает, кружки и врачи — напомним всем"
                        todays.isNotEmpty() -> "${todays.size} ${ru.ubt.familyshield.shared.content.plural(todays.size, "дело", "дела", "дел")} на сегодня"
                        soon.isNotEmpty() -> "Сегодня дел нет. Ближайшее — ${dayLabel(soon.first().startsAt.localDate(), today).lowercase()}"
                        else -> "На неделе дел нет"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val shown = (todays.ifEmpty { soon }).take(3)
        shown.forEach { occ -> CompactEventLine(occ, state.settings.deviceId, showDay = todays.isEmpty()) }
        if (events.isEmpty()) {
            PillButton("Добавить дело", modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.Add, onClick = onOpen)
        }
    }
}

@Composable
private fun CompactEventLine(occ: EventOccurrence, me: String?, showDay: Boolean) {
    val e = occ.event
    val v = eventVisual(e.kind)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(timeOf(occ.startsAt), style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(44.dp))
        IconTile(v.icon, v.gradient, size = 30.dp)
        Column(Modifier.weight(1f)) {
            Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = assigneeLine(e, me)
            val nobody = line == null && e.kind != EventKind.BIRTHDAY
            val sub = listOfNotNull(if (showDay) dayLabel(occ.startsAt.localDate()) else null, line ?: "никто не взял".takeIf { nobody })
            if (sub.isNotEmpty()) {
                Text(
                    sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (nobody) Brand.Warning else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Child and elder home: what is planned for this person today and tomorrow, in plain words.
 * [large] uses bigger type for the elder's simple mode.
 */
@Composable
fun MyDayCard(graph: AppGraph, large: Boolean = false) {
    val events = rememberEvents(graph)
    val me = graph.settings.current.deviceId
    val today = LocalDate.now()
    val mine = upcoming(events.filter { me != null && it.concerns(me) }, 2, today)
        .filter { it.startsAt >= System.currentTimeMillis() - 30 * 60_000L }
    if (mine.isEmpty()) return
    SoftCard(padding = if (large) 20.dp else 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Illustration(Art3D.Calendar, size = if (large) 56.dp else 44.dp)
            Text("Мои дела", style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium)
        }
        mine.take(4).forEach { occ ->
            val e = occ.event
            val v = eventVisual(e.kind)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconTile(v.icon, v.gradient, size = if (large) 48.dp else 38.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        "${dayLabel(occ.startsAt.localDate(), today)}, ${timeOf(occ.startsAt)}",
                        style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(e.title, style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall)
                    // Large type keeps the person and the place on separate lines.
                    val details = listOfNotNull(assigneeLine(e, me), e.place.ifBlank { null })
                    val style = if (large) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall
                    if (large) details.forEach { Text(it, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else if (details.isNotEmpty()) Text(details.joinToString(" · "), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
