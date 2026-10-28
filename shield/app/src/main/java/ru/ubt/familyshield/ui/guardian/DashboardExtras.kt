package ru.ubt.familyshield.ui.guardian

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Elderly
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GppMaybe
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneDisabled
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.core.family.elderPolicy
import ru.ubt.familyshield.core.family.memberRole
import ru.ubt.familyshield.shared.content.Campaign
import ru.ubt.familyshield.shared.content.CampaignAction
import ru.ubt.familyshield.shared.content.WeekNumbers
import ru.ubt.familyshield.shared.content.WeeklyDigest
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.IconTile
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.ProgressRing
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.components.TileRow
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

/*
 * Extra blocks on the guardian's home screen: the seasonal nudge, the launch checklist that walks a new
 * family to full protection, and the weekly summary that shows the value of the app every week.
 */

// ---- Seasonal campaign -------------------------------------------------------------------------------

@DrawableRes
private fun campaignArt(c: Campaign): Int = when (c.id) {
    "new_year" -> Art3D.BellFestive
    "pension" -> Art3D.Wallet
    "credit_ban" -> Art3D.Locker
    "family_day" -> Art3D.Heart
    "summer" -> Art3D.Travel
    "back_to_school" -> Art3D.Notebook
    "elders_day" -> Art3D.Gift
    "autumn" -> Art3D.Tools
    else -> Art3D.Megaphone
}

@Composable
internal fun CampaignCard(campaign: Campaign, onAction: (Campaign) -> Unit) {
    SoftCard(onClick = { onAction(campaign) }, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Illustration(campaignArt(campaign), size = 64.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(campaign.title, style = MaterialTheme.typography.titleMedium)
                Text(campaign.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PillButton(
            campaign.button, modifier = Modifier.fillMaxWidth(),
            fill = if (campaign.action == CampaignAction.PREMIUM) Gradients.Gold else Gradients.Emerald,
            contentColor = if (campaign.action == CampaignAction.PREMIUM) Brand.Ink else Color.White) { onAction(campaign) }
    }
}

// ---- Launch checklist --------------------------------------------------------------------------------

internal enum class SetupStep(val title: String, val hint: String, val icon: ImageVector, val gradient: Brush) {
    ELDER("Подключите телефон родителей", "Защита от мошеннических звонков", Icons.Rounded.Elderly, Gradients.Violet),
    CHILD("Подключите телефон ребёнка", "Экранное время, интернет и геолокация", Icons.Rounded.ChildCare, Gradients.Sky),
    PHONE("Укажите свой номер для SOS", "Близкие смогут позвонить вам одной кнопкой", Icons.Rounded.Phone, Gradients.Coral),
    HOME_ZONE("Настройте геозону «Дом»", "Сообщим, когда близкий пришёл домой", Icons.Rounded.Home, Gradients.Ocean),
    LESSON("Пройдите урок о мошенниках", "3 минуты — и вы знаете главные схемы", Icons.Rounded.School, Gradients.Violet),
    CREDIT_BAN("Оформите самозапрет на кредиты", "Бесплатно, на Госуслугах", Icons.Rounded.AccountBalance, Gradients.Emerald),
}

internal fun setupProgress(state: FamilyUiState): Map<SetupStep, Boolean> {
    val members = state.others
    val learning = state.settings.learning
    return mapOf(
        SetupStep.ELDER to members.any { it.memberRole == MemberRole.ELDER },
        SetupStep.CHILD to members.any { it.memberRole == MemberRole.CHILD },
        SetupStep.PHONE to state.settings.phone.isNotBlank(),
        SetupStep.HOME_ZONE to members.any {
            when (it.memberRole) {
                MemberRole.CHILD -> it.childPolicy().geofences.isNotEmpty()
                MemberRole.ELDER -> it.elderPolicy().geofences.isNotEmpty()
                else -> false
            }
        },
        SetupStep.LESSON to (learning.passedCount > 0),
        SetupStep.CREDIT_BAN to ("credit_ban" in learning.steps),
    )
}

@Composable
internal fun SetupChecklist(progress: Map<SetupStep, Boolean>, onStep: (SetupStep) -> Unit) {
    val done = progress.count { it.value }
    val left = progress.filterValues { !it }.keys.toList()
    SoftCard(padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ProgressRing(
                done / progress.size.toFloat(), 56.dp, stroke = 6.dp,
                color = Brand.Green, track = MaterialTheme.colorScheme.surfaceVariant,
            ) { Text("$done/${progress.size}", style = MaterialTheme.typography.labelLarge) }
            Column(Modifier.weight(1f)) {
                Text("Настройте защиту семьи", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Осталось ${left.size} ${plural(left.size, "шаг", "шага", "шагов")} — около ${left.size * 2} минут",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            left.take(3).forEach { step -> TileRow(step.title, subtitle = step.hint, icon = step.icon, gradient = step.gradient) { onStep(step) } }
        }
    }
}

// ---- Week of the family ------------------------------------------------------------------------------

@Composable
internal fun WeekCard(graph: AppGraph, state: FamilyUiState, onOpen: () -> Unit) {
    val today = java.time.LocalDate.now()
    val week = WeekNumbers.of(rememberWeekStats(graph).within(WeeklyDigest.thisWeek(today)))
    SoftCard(onClick = onOpen, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Illustration(Art3D.Calendar, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text("Неделя семьи", style = MaterialTheme.typography.titleMedium)
                Text(weekRangeLabel(today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Открыть итоги недели", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeekStat("${week.dangerous}", "опасных ${plural(week.dangerous, "момент", "момента", "моментов")}", Icons.Rounded.GppMaybe, Gradients.Sunrise)
            WeekStat("${week.scamCalls}", "${plural(week.scamCalls, "звонок", "звонка", "звонков")} мошенников", Icons.Rounded.PhoneDisabled, Gradients.Danger)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WeekStat("${week.checkIns}", "${plural(week.checkIns, "отметка", "отметки", "отметок")} «Я в порядке»", Icons.Rounded.Favorite, Gradients.Coral)
            WeekStat("${week.tasksDone}", plural(week.tasksDone, "задание", "задания", "заданий"), Icons.Rounded.TaskAlt, Gradients.Emerald)
        }
    }
}

@Composable
private fun RowScope.WeekStat(value: String, label: String, icon: ImageVector, gradient: Brush) {
    Row(
        Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, gradient, size = 36.dp)
        Column {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}
