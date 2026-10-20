package ru.ubt.familyshield.ui.guardian

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.chat.FamilyChatScreen

private enum class Tab(val label: String, val on: ImageVector, val off: ImageVector) {
    FAMILY("Семья", Icons.Filled.Home, Icons.Outlined.Home),
    ALERTS("События", Icons.Filled.Notifications, Icons.Outlined.Notifications),
    CHAT("Чат", Icons.Filled.Chat, Icons.Outlined.Chat),
    SETTINGS("Ещё", Icons.Filled.Settings, Icons.Outlined.Settings),
}

@Composable
fun GuardianRoot(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState) {
    var tab by rememberSaveable { mutableStateOf(Tab.FAMILY) }
    var openMember by rememberSaveable { mutableStateOf<String?>(null) }

    val memberId = openMember
    if (memberId != null) {
        val member = state.members.firstOrNull { it.deviceId == memberId }
        if (member == null) {
            openMember = null
        } else {
            MemberDetailScreen(graph, vm, state, member) { openMember = null }
            return
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {
                            val badge = when (t) {
                                Tab.ALERTS -> state.unread
                                Tab.FAMILY -> state.pendingRequests.size
                                else -> 0
                            }
                            BadgedBox(badge = { if (badge > 0) Badge { Text(badge.coerceAtMost(99).toString()) } }) {
                                Icon(if (tab == t) t.on else t.off, contentDescription = t.label)
                            }
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                Tab.FAMILY -> GuardianDashboard(graph, vm, state, onOpenMember = { openMember = it }, onGoAlerts = { tab = Tab.ALERTS })
                Tab.ALERTS -> AlertsScreen(graph, vm, state, onOpenMember = { openMember = it })
                Tab.CHAT -> FamilyChatScreen(graph, state, embedded = true)
                Tab.SETTINGS -> GuardianSettings(graph, vm, state)
            }
        }
    }
}
