package ru.ubt.familyshield.ui.elder

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.protect.ui.SosActivity
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.TrustedContact
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.ShieldViewModel
import ru.ubt.familyshield.ui.components.Banner
import ru.ubt.familyshield.ui.setup.PermissionWizard
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.tools.CheckerScreen

private data class Tile(val emoji: String, val label: String, val color: Color, val onClick: () -> Unit)

/** The elderly relative's own home: large, calm, few choices. Doubles as a launcher in Simple Mode. */
@Composable
fun ElderHome(graph: AppGraph, vm: ShieldViewModel, state: FamilyUiState, activity: ComponentActivity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf("home") }
    var contacts by remember { mutableStateOf<List<TrustedContact>>(emptyList()) }
    androidx.compose.runtime.LaunchedEffect(Unit) { contacts = graph.protection.sos.contacts() }

    when (screen) {
        "checker" -> { CheckerScreen(graph) { screen = "home" }; return }
        "permissions" -> { PermissionWizard(graph, onboarding = false, onBack = { screen = "home" }) { screen = "home" }; return }
        "settings" -> { ElderSettings(graph, state) { screen = "home" }; return }
    }

    val health = remember(state.settings) { graph.protection.health.snapshot(MemberRole.ELDER, state.settings.childPolicy, state.settings.elderPolicy) }
    val family = contacts.firstOrNull()

    fun dial(phone: String?) = phone?.let { graph.protection.sos.call(it) }
    fun open(pkg: String) = runCatching { context.startActivity(context.packageManager.getLaunchIntentForPackage(pkg)!!) }

    val tiles = buildList {
        add(Tile("👨‍👩‍👧", "Позвонить родным", Brand.Green) { dial(family?.phone) ?: run { screen = "settings" } })
        add(Tile("☎️", "Телефон", Brand.Blue) { runCatching { context.startActivity(Intent(Intent.ACTION_DIAL)) } })
        add(Tile("💬", "Сообщения", Brand.Blue) { runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)) } })
        add(Tile("📷", "Камера", Brand.Navy) { runCatching { context.startActivity(Intent("android.media.action.STILL_IMAGE_CAMERA")) } })
        add(Tile("🖼️", "Фотографии", Brand.Navy) { runCatching { context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)) } })
        add(Tile("🔍", "Проверить мошенников", Brand.Warning) { screen = "checker" })
        add(Tile("✅", "Я в порядке", Brand.Green) { scope.launch { graph.checkIn() } })
        add(Tile("🗺️", "Карты", Brand.Blue) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q="))) } })
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
        Text("Здравствуйте, ${state.settings.displayName.ifBlank { "" }}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(if (health.criticalMissing.isEmpty()) "Телефон под защитой 🛡️" else "Нажмите, чтобы завершить настройку", style = MaterialTheme.typography.titleMedium,
            color = if (health.criticalMissing.isEmpty()) Brand.GreenDark else Brand.Warning,
            modifier = Modifier.padding(vertical = 4.dp).clickable(enabled = health.criticalMissing.isNotEmpty()) { screen = "permissions" })

        // Big SOS always on top.
        Surface(color = Brand.Danger, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().height(96.dp).padding(vertical = 8.dp).clickable { context.startActivity(Intent(context, SosActivity::class.java)) }) {
            Box(contentAlignment = Alignment.Center) {
                Text("🆘  Позвать на помощь", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            items(tiles) { t ->
                Surface(color = t.color, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().aspectRatio(1.1f).clickable(onClick = t.onClick)) {
                    Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(t.emoji, fontSize = 44.sp)
                        Text(t.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Text("Настройки", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { screen = "settings" }, textAlign = TextAlign.Center)
    }
}
