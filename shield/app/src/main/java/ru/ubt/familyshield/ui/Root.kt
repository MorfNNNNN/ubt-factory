package ru.ubt.familyshield.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.MutableStateFlow
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.child.ChildHome
import ru.ubt.familyshield.ui.elder.ElderHome
import ru.ubt.familyshield.ui.guardian.GuardianRoot
import ru.ubt.familyshield.ui.onboarding.OnboardingFlow
import ru.ubt.familyshield.ui.theme.ShieldTheme
import ru.ubt.familyshield.ui.theme.ShieldUiConfig

object Routes {
    const val ONBOARDING = "onboarding"
    const val GUARDIAN = "guardian"
    const val CHILD = "child"
    const val ELDER = "elder"
}

@Composable
fun ShieldRoot(graph: AppGraph, activity: ComponentActivity, routeRequests: MutableStateFlow<String?>) {
    val vm: ShieldViewModel = viewModel(factory = ShieldViewModel.factory(graph))
    val state by vm.state.collectAsState()
    val pendingRoute by routeRequests.collectAsState()
    val s = state.settings

    val elderMode = s.role == MemberRole.ELDER
    val config = ShieldUiConfig(
        fontScale = if (elderMode && s.elderPolicy.simpleMode.enabled) s.elderPolicy.simpleMode.fontScale.coerceIn(1f, 1.6f) else 1f,
        elderMode = elderMode,
    )

    ShieldTheme(config = config) {
        val nav = rememberNavController()
        val start = when {
            !s.onboardingDone && !s.isPaired && s.role == null -> Routes.ONBOARDING
            s.role == MemberRole.CHILD -> Routes.CHILD
            s.role == MemberRole.ELDER -> Routes.ELDER
            else -> Routes.GUARDIAN
        }
        LaunchedEffect(Unit) { vm.refresh() }
        LaunchedEffect(pendingRoute) {
            val route = pendingRoute ?: return@LaunchedEffect
            navigateForDeepLink(nav, route, s.role)
            routeRequests.value = null
        }

        NavHost(navController = nav, startDestination = start) {
            composable(Routes.ONBOARDING) {
                val link = (pendingRoute?.takeIf { it.startsWith("pair:") }?.removePrefix("pair:"))
                OnboardingFlow(graph, link) {
                    vm.completeOnboarding()
                    routeRequests.value = null
                    val dest = when (graph.settings.current.role) {
                        MemberRole.CHILD -> Routes.CHILD
                        MemberRole.ELDER -> Routes.ELDER
                        else -> Routes.GUARDIAN
                    }
                    nav.navigate(dest) { popUpTo(Routes.ONBOARDING) { inclusive = true }; launchSingleTop = true }
                }
            }
            composable(Routes.GUARDIAN) { GuardianRoot(graph, vm, state) }
            composable(Routes.CHILD) { ChildHome(graph, vm, state) }
            composable(Routes.ELDER) { ElderHome(graph, vm, state, activity) }
        }
    }
}

private fun navigateForDeepLink(nav: NavHostController, route: String, role: MemberRole?) {
    val base = when (role) {
        MemberRole.CHILD -> Routes.CHILD
        MemberRole.ELDER -> Routes.ELDER
        null -> return
        else -> Routes.GUARDIAN
    }
    if (nav.currentDestination?.route != base) runCatching { nav.navigate(base) }
}
