package ru.ubt.familyshield.ui.elder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.NoteAlt
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.shared.model.MedicalCard
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.guardian.DetailScaffold
import ru.ubt.familyshield.ui.onboarding.ShieldTextField
import ru.ubt.familyshield.ui.theme.Gradients

/*
 * «Медицинская карта»: blood type, allergies, illnesses and medicines for paramedics. The family fills it
 * in; the elder's phone shows it in large type next to SOS.
 */

private class MedicalLine(val title: String, val icon: ImageVector, val gradient: Brush, val value: (MedicalCard) -> String)

private val lines = listOf(
    MedicalLine("Группа крови", Icons.Rounded.Bloodtype, Gradients.Danger) { it.bloodType },
    MedicalLine("Аллергии", Icons.Rounded.Warning, Gradients.Sunrise) { it.allergies },
    MedicalLine("Заболевания", Icons.Rounded.MedicalServices, Gradients.Coral) { it.conditions },
    MedicalLine("Принимает лекарства", Icons.Rounded.Medication, Gradients.Violet) { it.medicines },
    MedicalLine("Телефон врача", Icons.Rounded.Call, Gradients.Emerald) { it.doctorPhone },
    MedicalLine("Важно знать", Icons.Rounded.NoteAlt, Gradients.Slate) { it.notes },
)

/** Read-only card in large type, for the elder's phone and for anyone helping in an emergency. */
@Composable
fun MedicalCardScreen(name: String, card: MedicalCard, onBack: () -> Unit) {
    DetailScaffold(title = "Медицинская карта", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            HeroCard(
                title = name.ifBlank { "Медицинская карта" },
                subtitle = "Покажите эту карту врачу или скорой помощи",
                gradient = Gradients.Danger,
                art = { IconTile(Icons.Rounded.MedicalServices, Gradients.Coral, size = 96.dp) },
            )
            lines.filter { it.value(card).isNotBlank() }.forEach { line ->
                SoftCard(padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(line.icon, line.gradient, size = 44.dp)
                        Column(Modifier.weight(1f)) {
                            Text(line.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(line.value(card), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            if (card.isEmpty) {
                Text(
                    "Карта пока не заполнена. Её заполняют близкие в приложении — так безопаснее.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** The guardian fills the card in; it is delivered to the elder's phone end-to-end encrypted. */
@Composable
fun MedicalCardEditor(name: String, card: MedicalCard, onBack: () -> Unit, onSave: (MedicalCard) -> Unit) {
    var blood by remember { mutableStateOf(card.bloodType) }
    var allergies by remember { mutableStateOf(card.allergies) }
    var conditions by remember { mutableStateOf(card.conditions) }
    var medicines by remember { mutableStateOf(card.medicines) }
    var doctor by remember { mutableStateOf(card.doctorPhone) }
    var notes by remember { mutableStateOf(card.notes) }
    DetailScaffold(title = "Медкарта: $name", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Карта появится на телефоне в крупном виде рядом с SOS. Врачи скорой увидят главное за секунды.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SoftCard(padding = 16.dp) {
                ShieldTextField(blood, { blood = it.take(20) }, "Группа крови, например «II (A) Rh+»")
                ShieldTextField(allergies, { allergies = it.take(200) }, "Аллергии")
                ShieldTextField(conditions, { conditions = it.take(300) }, "Заболевания", singleLine = false)
                ShieldTextField(medicines, { medicines = it.take(300) }, "Принимает лекарства", singleLine = false)
                ShieldTextField(doctor, { doctor = it.take(30) }, "Телефон врача", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                ShieldTextField(notes, { notes = it.take(300) }, "Важно знать (кардиостимулятор, слух…)", singleLine = false)
            }
            PrimaryButton("Сохранить") {
                onSave(MedicalCard(blood.trim(), allergies.trim(), conditions.trim(), medicines.trim(), doctor.trim(), notes.trim()))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
