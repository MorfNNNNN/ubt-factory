package ru.ubt.familyshield.ui.onboarding

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.R
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.components.AvatarArt
import ru.ubt.familyshield.ui.components.AvatarPicker
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.theme.Brand

/** The guardian names the family and themselves; the phone becomes the first (admin) member. */
@Composable
fun GuardianSetup(graph: AppGraph, onBack: () -> Unit, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(graph.settings.current.displayName) }
    var phone by remember { mutableStateOf(graph.settings.current.phone) }
    var family by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(graph.settings.current.avatar.ifBlank { AvatarArt.MOM.value }) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    OnboardingPage(
        title = "Создаём вашу семью",
        text = "Потом вы добавите телефоны близких по QR-коду — это займёт минуту.",
        onBack = onBack,
        art = { Illustration(R.drawable.ill_mark_shield, Modifier.align(Alignment.Center), size = 150.dp) },
    ) {
        SoftCard(padding = 18.dp) {
            Text("Ваш портрет", style = MaterialTheme.typography.titleMedium)
            AvatarPicker(avatar, MemberRole.GUARDIAN.name) { avatar = it }
        }
        SoftCard(padding = 18.dp) {
            ShieldTextField(name, { name = it.take(40) }, "Ваше имя")
            ShieldTextField(
                phone, { phone = it.take(20) }, "Ваш телефон (для кнопки SOS у близких)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
            ShieldTextField(family, { family = it.take(40) }, "Название семьи, например «Ивановы»")
        }
        error?.let { NoticeCard(it, Icons.Rounded.ErrorOutline, Brand.DangerLight, Brand.Danger, textColor = Brand.Ink) }
        PrimaryButton("Создать семью", loading = loading, enabled = name.isNotBlank()) {
            loading = true
            error = null
            scope.launch {
                try {
                    graph.settings.setProfile(name, avatar)
                    graph.settings.setPhone(phone)
                    graph.family.createFamily(family)
                    graph.scheduleWork()
                    onDone()
                } catch (e: Throwable) {
                    error = friendlyError(e)
                } finally {
                    loading = false
                }
            }
        }
    }
}
