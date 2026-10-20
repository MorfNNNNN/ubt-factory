package ru.ubt.familyshield.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.AlertEntity
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.db.NoteEntity
import ru.ubt.familyshield.core.db.RequestEntity
import ru.ubt.familyshield.core.prefs.AppSettings
import ru.ubt.familyshield.shared.billing.Entitlement

data class FamilyUiState(
    val settings: AppSettings,
    val entitlement: Entitlement,
    val members: List<MemberEntity>,
    val alerts: List<AlertEntity>,
    val unread: Int,
    val pendingRequests: List<RequestEntity>,
    val notes: List<NoteEntity>,
    val outboxCount: Int,
) {
    val self: MemberEntity? get() = members.firstOrNull { it.isSelf }
    val others: List<MemberEntity> get() = members.filter { !it.isSelf }
    val protectedMembers: List<MemberEntity> get() = others.filter { it.role != "GUARDIAN" }
}

class ShieldViewModel(val graph: AppGraph) : ViewModel() {
    private data class Core(
        val settings: AppSettings,
        val entitlement: Entitlement,
        val members: List<MemberEntity>,
        val alerts: List<AlertEntity>,
        val unread: Int,
    )

    val state: StateFlow<FamilyUiState> = kotlinx.coroutines.flow.combine(
        graph.settings.state,
        graph.entitlements.entitlement,
        graph.db.members().observeAll(),
        graph.db.alerts().observeRecent(),
        graph.db.alerts().observeUnread(),
    ) { settings, entitlement, members, alerts, unread ->
        Core(settings, entitlement, members, alerts, unread)
    }.let { core ->
        kotlinx.coroutines.flow.combine(
            core,
            graph.db.requests().observePending(),
            graph.db.notes().observeAll(),
            graph.db.outbox().observeCount(),
        ) { c, requests, notes, outbox ->
            FamilyUiState(
                settings = c.settings,
                entitlement = c.entitlement,
                members = c.members,
                alerts = c.alerts,
                unread = c.unread,
                pendingRequests = requests,
                notes = notes,
                outboxCount = outbox,
            )
        }
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000),
        FamilyUiState(graph.settings.current, graph.entitlements.current, emptyList(), emptyList(), 0, emptyList(), emptyList(), 0),
    )

    fun refresh() = viewModelScope.launch {
        runCatching { graph.sync.syncNow() }
        runCatching { graph.family.refreshMembers() }
        runCatching { graph.entitlements.refresh() }
    }

    fun markAlertsRead() = viewModelScope.launch { graph.db.alerts().markAllRead() }

    fun completeOnboarding() = viewModelScope.launch { graph.settings.setOnboardingDone(true) }

    companion object {
        fun factory(graph: AppGraph) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ShieldViewModel(graph) as T
        }
    }
}
