package ru.ubt.familyshield.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.NoteEntity
import ru.ubt.familyshield.shared.model.QuickReply
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.components.ScreenScaffold
import ru.ubt.familyshield.ui.components.timeOf

/** Simple end-to-end encrypted family chat with one-tap quick replies for elders and kids. */
@Composable
fun FamilyChatScreen(graph: AppGraph, state: FamilyUiState, embedded: Boolean, onBack: (() -> Unit)? = null) {
    if (embedded) {
        ChatBody(graph, state)
    } else {
        ScreenScaffold(title = "Семейный чат", onBack = onBack) { padding ->
            Box(Modifier.padding(padding)) { ChatBody(graph, state) }
        }
    }
}

@Composable
private fun ChatBody(graph: AppGraph, state: FamilyUiState) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.notes.size) { if (state.notes.isNotEmpty()) listState.animateScrollToItem(state.notes.lastIndex) }

    fun send(body: String, quick: QuickReply? = null) {
        if (body.isBlank()) return
        scope.launch { graph.guardian.note(body, quick) }
        text = ""
    }

    Column(Modifier.fillMaxSize()) {
        if (state.notes.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Напишите близким пару тёплых слов 💚", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(state.notes, key = { it.id }) { note -> Bubble(note, graph, state) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuickReply.entries.take(3).forEach { q ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50), onClick = { send(q.textRu, q) }) {
                    Text(q.textRu, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = text, onValueChange = { text = it.take(1000) }, placeholder = { Text("Сообщение") }, modifier = Modifier.weight(1f), maxLines = 4)
            IconButton(onClick = { send(text) }) { Icon(Icons.AutoMirrored.Filled.Send, "Отправить", tint = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun Bubble(note: NoteEntity, graph: AppGraph, state: FamilyUiState) {
    val mine = note.mine || note.senderDeviceId == state.settings.deviceId
    val sender = state.members.firstOrNull { it.deviceId == note.senderDeviceId }?.displayName ?: "Близкий"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            color = if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                if (!mine) Text(sender, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(note.text, color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                Text(timeOf(note.createdAt), style = MaterialTheme.typography.labelSmall, color = if (mine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}
