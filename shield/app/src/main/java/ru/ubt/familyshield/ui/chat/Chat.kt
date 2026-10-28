package ru.ubt.familyshield.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.db.NoteEntity
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.shared.model.QuickReply
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.components.AvatarImage
import ru.ubt.familyshield.ui.components.AvatarStack
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.timeOf
import ru.ubt.familyshield.ui.guardian.avatarStackWidth
import ru.ubt.familyshield.ui.guardian.roleColor
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Simple end-to-end encrypted family chat with one-tap quick replies for elders and kids. */
@Composable
fun FamilyChatScreen(graph: AppGraph, state: FamilyUiState, embedded: Boolean, onBack: (() -> Unit)? = null) {
    if (embedded) {
        ChatBody(graph, state, showHeader = true)
    } else {
        ScreenScaffold(title = "Семейный чат", onBack = onBack) { padding ->
            Box(Modifier.padding(padding)) { ChatBody(graph, state, showHeader = false) }
        }
    }
}

/** A chat row: a day separator or a message. */
private sealed class ChatRow(val key: String) {
    class Day(val date: LocalDate) : ChatRow("day-$date")
    class Message(val note: NoteEntity) : ChatRow(note.id)
}

@Composable
private fun ChatBody(graph: AppGraph, state: FamilyUiState, showHeader: Boolean) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val rows = remember(state.notes) {
        buildList {
            var day: LocalDate? = null
            state.notes.forEach { note ->
                val d = dayOf(note.createdAt)
                if (d != day) { add(ChatRow.Day(d)); day = d }
                add(ChatRow.Message(note))
            }
        }
    }
    LaunchedEffect(state.notes.size) { if (rows.isNotEmpty()) listState.animateScrollToItem(rows.lastIndex) }

    fun send(body: String, quick: QuickReply? = null) {
        if (body.isBlank()) return
        scope.launch { graph.guardian.note(body, quick) }
        text = ""
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        if (showHeader) ChatHeader(state)
        if (state.notes.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyChat(state)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    when (row) {
                        is ChatRow.Day -> DayChip(row.date)
                        is ChatRow.Message -> Bubble(row.note, state)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickReply.entries.take(3).forEach { q -> QuickChip(q) { send(q.textRu, q) } }
        }
        Composer(text, onText = { text = it.take(1000) }, onSend = { send(text) })
    }
}

@Composable
private fun ChatHeader(state: FamilyUiState) {
    val family = state.others
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Семейный чат", style = MaterialTheme.typography.headlineLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = Brand.Green, modifier = Modifier.size(14.dp))
                Text("Сообщения защищены шифрованием", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (family.isNotEmpty()) {
            val shown = family.take(3)
            Box(Modifier.width(avatarStackWidth(shown.size, 38.dp))) {
                AvatarStack(shown.map { it.avatar to it.role }, size = 38.dp)
            }
        }
    }
}

@Composable
private fun EmptyChat(state: FamilyUiState) {
    Column(Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val family = state.members.take(4)
        if (family.isNotEmpty()) {
            Box(Modifier.width(avatarStackWidth(family.size, 64.dp))) {
                AvatarStack(family.map { it.avatar to it.role }, size = 64.dp)
            }
        }
        Text("Напишите близким пару тёплых слов", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            "Или отправьте быстрый ответ — он ниже, в одно касание.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DayChip(date: LocalDate) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            dayLabel(date), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun Bubble(note: NoteEntity, state: FamilyUiState) {
    val mine = note.mine || note.senderDeviceId == state.settings.deviceId
    val sender: MemberEntity? = state.members.firstOrNull { it.deviceId == note.senderDeviceId }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            AvatarImage(sender?.avatar, sender?.role, 34.dp)
            Spacer(Modifier.width(8.dp))
        }
        val shape = if (mine) RoundedCornerShape(22.dp, 22.dp, 6.dp, 22.dp) else RoundedCornerShape(22.dp, 22.dp, 22.dp, 6.dp)
        val fill: Brush = if (mine) Gradients.Emerald else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))
        Column(
            Modifier.widthIn(max = 280.dp)
                .shadow(if (mine) 6.dp else 4.dp, shape, ambientColor = Brand.Green.copy(alpha = 0.18f), spotColor = Brand.Green.copy(alpha = 0.18f))
                .clip(shape).background(fill)
                .padding(start = 14.dp, end = 14.dp, top = 9.dp, bottom = 7.dp),
        ) {
            if (!mine) {
                Text(
                    sender?.displayName ?: "Близкий", style = MaterialTheme.typography.labelMedium,
                    color = sender?.let { roleColor(it.memberRole) } ?: MaterialTheme.colorScheme.primary,
                )
            }
            Text(note.text, color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
            Text(
                timeOf(note.createdAt), style = MaterialTheme.typography.labelSmall,
                color = if (mine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun QuickChip(q: QuickReply, onClick: () -> Unit) {
    Row(
        Modifier.clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 10.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(quickIcon(q), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(q.textRu, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Composer(text: String, onText: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = text, onValueChange = onText, placeholder = { Text("Сообщение") },
            modifier = Modifier.weight(1f), maxLines = 4, shape = RoundedCornerShape(26.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
        )
        val ready = text.isNotBlank()
        Box(
            Modifier.size(52.dp)
                .shadow(if (ready) 8.dp else 0.dp, CircleShape, ambientColor = Brand.Green.copy(alpha = 0.35f), spotColor = Brand.Green.copy(alpha = 0.35f))
                .clip(CircleShape)
                .background(if (ready) Gradients.Emerald else Gradients.Slate)
                .clickable(role = Role.Button, onClick = onSend),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Отправить", tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

private fun quickIcon(q: QuickReply): ImageVector = when (q) {
    QuickReply.ALL_GOOD -> Icons.Rounded.CheckCircle
    QuickReply.CALL_ME -> Icons.Rounded.Call
    QuickReply.COME_HOME -> Icons.Rounded.SupportAgent
    QuickReply.ON_MY_WAY -> Icons.Rounded.DirectionsCar
    QuickReply.LOVE -> Icons.Rounded.Favorite
    QuickReply.BUSY -> Icons.Rounded.Schedule
}

private fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()

private fun dayLabel(day: LocalDate, today: LocalDate = LocalDate.now()): String = when (day) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> day.format(DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMMM" else "d MMMM yyyy", Locale("ru")))
}
