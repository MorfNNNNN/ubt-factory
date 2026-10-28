package ru.ubt.familyshield.ui.guardian

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.calendar.FamilyCalendarScreen
import ru.ubt.familyshield.ui.chat.FamilyChatScreen
import ru.ubt.familyshield.ui.paywall.PaywallScreen
import ru.ubt.familyshield.ui.paywall.UpsellReason
import ru.ubt.familyshield.ui.safety.SafetySchoolScreen
import ru.ubt.familyshield.ui.theme.Brand

private enum class Tab(val label: String, val on: ImageVector, val off: ImageVector) {
    FAMILY("Семья", Icons.Rounded.Home, Icons.Outlined.Home),
    ALERTS("События", Icons.Rounded.Notifications, Icons.Outlined.Notifications),
    CHAT("Чат", Icons.AutoMirrored.Rounded.Chat, Icons.AutoMirrored.Outlined.Chat),
    SETTINGS("Ещё", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

/** Full-screen pages opened over the tabs: from home cards, settings rows and notifications. */
private object Page {
    const val WEEK = "week"
    const val CALENDAR = "calendar"
    const val PREMIUM = "premium"
    const val LESSON = "lesson:"
}

@Composable
fun GuardianRoot(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState, deepLink: String? = null, onDeepLinkHandled: () -> Unit = {}) {
    var tab by rememberSaveable { mutableStateOf(Tab.FAMILY) }
    var openMember by rememberSaveable { mutableStateOf<String?>(null) }
    var page by rememberSaveable { mutableStateOf<String?>(null) }

    // Notifications open the place they talk about.
    LaunchedEffect(deepLink) {
        when (deepLink ?: return@LaunchedEffect) {
            "alerts", "requests" -> tab = Tab.ALERTS
            "chat" -> tab = Tab.CHAT
            Page.WEEK, Page.CALENDAR -> {
                openMember = null
                page = deepLink
            }
        }
        onDeepLinkHandled()
    }

    when (val p = page) {
        Page.WEEK -> {
            WeekReportScreen(
                graph, state, onBack = { page = null },
                onOpenMember = { page = null; openMember = it },
                onLesson = { page = Page.LESSON + it },
                onPremium = { page = Page.PREMIUM },
            )
            return
        }
        Page.CALENDAR -> { FamilyCalendarScreen(graph, state) { page = null }; return }
        Page.PREMIUM -> { PaywallScreen(graph, UpsellReason.GENERAL) { page = Page.WEEK }; return }
        else -> if (p != null && p.startsWith(Page.LESSON)) {
            SafetySchoolScreen(graph, p.removePrefix(Page.LESSON).ifBlank { null }) { page = Page.WEEK }
            return
        }
    }

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
            TabBar(
                current = tab,
                badge = { t ->
                    when (t) {
                        Tab.ALERTS -> state.unread
                        Tab.FAMILY -> state.pendingRequests.size
                        else -> 0
                    }
                },
                onSelect = { tab = it },
            )
        },
    ) { padding ->
        // Consume the bars here so nested screens (and their own scaffolds) don't pad for them twice.
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) {
            when (tab) {
                Tab.FAMILY -> GuardianDashboard(
                    graph, vm, state, onOpenMember = { openMember = it }, onGoAlerts = { tab = Tab.ALERTS }, onOpenSettings = { tab = Tab.SETTINGS },
                    onOpenWeek = { page = Page.WEEK }, onOpenCalendar = { page = Page.CALENDAR },
                )
                Tab.ALERTS -> AlertsScreen(graph, vm, state, onOpenMember = { openMember = it })
                Tab.CHAT -> FamilyChatScreen(graph, state, embedded = true)
                Tab.SETTINGS -> GuardianSettings(graph, vm, state, onOpenWeek = { page = Page.WEEK }, onOpenCalendar = { page = Page.CALENDAR })
            }
        }
    }
}

/** White bar with rounded top corners; the active tab sits in a mint pill. */
@Composable
private fun TabBar(current: Tab, badge: (Tab) -> Int, onSelect: (Tab) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 18.dp,
    ) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp)) {
            Tab.entries.forEach { t ->
                TabItem(t, selected = t == current, badge = badge(t), modifier = Modifier.weight(1f)) { onSelect(t) }
            }
        }
    }
}

@Composable
private fun TabItem(tab: Tab, selected: Boolean, badge: Int, modifier: Modifier, onClick: () -> Unit) {
    val pill by animateColorAsState(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent, label = "tabPill")
    val tint by animateColorAsState(if (selected) Brand.GreenDark else MaterialTheme.colorScheme.onSurfaceVariant, label = "tabTint")
    Column(
        modifier.clip(MaterialTheme.shapes.medium)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(Modifier.size(width = 60.dp, height = 34.dp).clip(CircleShape).background(pill), contentAlignment = Alignment.Center) {
                Icon(if (selected) tab.on else tab.off, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }
            if (badge > 0) CountBadge(badge, modifier = Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = (-4).dp))
        }
        Text(
            tab.label, style = MaterialTheme.typography.labelMedium, color = tint,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
        )
    }
}
