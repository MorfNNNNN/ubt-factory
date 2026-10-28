package ru.ubt.familyshield.ui.trust

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.ubt.familyshield.AppGraph
import ru.ubt.familyshield.core.db.MemberEntity
import ru.ubt.familyshield.core.family.childPolicy
import ru.ubt.familyshield.core.family.task
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.content.plural
import ru.ubt.familyshield.shared.model.AgeBand
import ru.ubt.familyshield.shared.model.MemberRequest
import ru.ubt.familyshield.shared.model.RequestKind
import ru.ubt.familyshield.shared.model.TrustLevel
import ru.ubt.familyshield.ui.FamilyUiState
import ru.ubt.familyshield.ui.components.Art3D
import ru.ubt.familyshield.ui.components.ArtTile
import ru.ubt.familyshield.ui.components.GlyphLine
import ru.ubt.familyshield.ui.components.HeroCard
import ru.ubt.familyshield.ui.components.Illustration
import ru.ubt.familyshield.ui.components.LabelChip
import ru.ubt.familyshield.ui.components.NoticeCard
import ru.ubt.familyshield.ui.components.PrimaryButton
import ru.ubt.familyshield.ui.components.SoftCard
import ru.ubt.familyshield.ui.guardian.DetailScaffold
import ru.ubt.familyshield.ui.guardian.HeroChip
import ru.ubt.familyshield.ui.guardian.PillButton
import ru.ubt.familyshield.ui.guardian.tintedFill
import ru.ubt.familyshield.ui.theme.Brand
import ru.ubt.familyshield.ui.theme.Gradients

/*
 * «Лестница доверия»: the child sees what each level allows and how many points from tasks it takes;
 * the parent raises or lowers the level. Perks sit on top of the parents' rules, which never change.
 */

@DrawableRes
fun levelArt(level: TrustLevel): Int = when (level) {
    TrustLevel.START -> Art3D.Target
    TrustLevel.STEADY -> Art3D.Medal
    TrustLevel.INDEPENDENT -> Art3D.Key
    TrustLevel.ADULT -> Art3D.Crown
}

private fun points(n: Int) = "$n ${plural(n, "балл", "балла", "баллов")}"

/** Points from accepted tasks, removed tasks included. */
@Composable
internal fun rememberTaskPoints(graph: AppGraph, deviceId: String): Int {
    val points by remember(deviceId) {
        graph.db.tasks().observeHistoryFor(deviceId).map { rows -> rows.sumOf { runCatching { it.task().earnedPoints }.getOrDefault(0) } }
    }.collectAsState(0)
    return points
}

/** The level this phone has asked for and not yet heard back about. */
@Composable
private fun rememberPendingLevel(graph: AppGraph): TrustLevel? {
    val pending by remember {
        graph.db.requests().observePending().map { rows ->
            rows.filter { it.kind == RequestKind.TRUST_LEVEL.name }
                .mapNotNull { runCatching { ShieldJson.decodeFromString(MemberRequest.serializer(), it.json).level }.getOrNull() }
                .maxOrNull()
        }
    }.collectAsState(null)
    return pending
}

/** Level names stay in the nominative: "уровень «Надёжный»" reads right, "до «Надёжный»" would not. */
private fun nextLine(next: TrustLevel?, points: Int, guardian: Boolean): String = when {
    next == null -> if (guardian) "Самый высокий уровень" else "Самый высокий уровень — родители тебе доверяют"
    points >= next.minPoints -> if (guardian) "Баллов хватает на уровень «${next.title}»" else "Уровень «${next.title}» уже можно попросить"
    else -> "Следующий уровень «${next.title}»: ещё ${points(next.minPoints - points)}"
}

private fun progressTo(next: TrustLevel?, current: TrustLevel, points: Int): Float {
    next ?: return 1f
    val span = (next.minPoints - current.minPoints).coerceAtLeast(1)
    return ((points - current.minPoints).toFloat() / span).coerceIn(0f, 1f)
}

@Composable
private fun LadderHero(level: TrustLevel, points: Int, subtitle: String) {
    HeroCard(
        title = "Уровень «${level.title}»",
        subtitle = subtitle,
        gradient = Gradients.Violet,
        art = { Illustration(levelArt(level), size = 112.dp) },
        footer = {
            HeroChip(points(points), Icons.Rounded.Stars)
            Spacer(Modifier.height(10.dp))
            val fraction = progressTo(level.next, level, points)
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(CircleShape).background(Color.White))
            }
        },
    )
}

/** One step of the ladder; [action] is the guardian's button, null on the child's phone. */
@Composable
private fun LevelCard(level: TrustLevel, current: TrustLevel, points: Int, age: AgeBand, guardian: Boolean, action: (@Composable () -> Unit)? = null) {
    val reached = level <= current
    val ageLocked = age.maxAge < level.minAge
    val isCurrent = level == current
    SoftCard(
        Modifier.let { if (isCurrent) it.border(2.dp, MaterialTheme.colorScheme.primary, MaterialTheme.shapes.large) else it },
        padding = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ArtTile(levelArt(level), size = 52.dp, backdrop = if (reached) Gradients.Mint else null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(level.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        level == TrustLevel.START -> "Начало пути"
                        level.minAge >= 13 -> "${points(level.minPoints)} · с ${level.minAge} лет"
                        else -> points(level.minPoints)
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                isCurrent -> LabelChip("Сейчас", Brand.GreenLight, Brand.GreenDark, icon = Icons.Rounded.Verified)
                reached -> LabelChip("Пройден", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Rounded.Check)
                ageLocked -> LabelChip("С ${level.minAge} лет", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, icon = Icons.Rounded.Lock)
                points >= level.minPoints -> LabelChip(if (guardian) "Баллы набраны" else "Можно просить", Brand.WarningLight, Brand.Warning)
                else -> Unit
            }
        }
        level.perks.forEach { perk ->
            GlyphLine(
                perk, if (reached) Icons.Rounded.Check else Icons.Rounded.Lock, if (reached) Gradients.Emerald else Gradients.Slate,
                tile = 26.dp, style = MaterialTheme.typography.bodyMedium,
            )
        }
        action?.invoke()
    }
}

// ---- Child -------------------------------------------------------------------------------------------

@Composable
fun ChildTrustScreen(graph: AppGraph, state: FamilyUiState, onBack: () -> Unit, onTasks: () -> Unit) {
    val scope = rememberCoroutineScope()
    val policy = state.settings.childPolicy
    val level = policy.trustLevel
    val age = policy.ageBand
    val myPoints = rememberTaskPoints(graph, state.settings.deviceId.orEmpty())
    val pending = rememberPendingLevel(graph)?.takeIf { it > level }
    val next = level.next

    DetailScaffold(title = "Лестница доверия", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LadderHero(level, myPoints, nextLine(next, myPoints, guardian = false))
            Text(
                "Выполняй задания и получай баллы. Набрал нужное — попроси родителей о новом уровне: правила станут мягче.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            when {
                next == null -> Unit
                pending != null -> NoticeCard(
                    "Запрос на уровень «${pending.title}» отправлен. Родители ответят в приложении.",
                    Icons.Rounded.HourglassTop, Brand.WarningLight, Brand.Warning,
                )
                next.reachable(myPoints, age) -> PrimaryButton("Попросить уровень «${next.title}»") { scope.launch { graph.actions.askTrustLevel(next) } }
                age.maxAge < next.minAge -> NoticeCard(
                    "Уровень «${next.title}» откроется с ${next.minAge} лет. А пока копи баллы за задания.",
                    Icons.Rounded.Lock, MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> PillButton("К заданиям", modifier = Modifier.fillMaxWidth(), icon = Icons.Rounded.TaskAlt, onClick = onTasks)
            }
            TrustLevel.entries.forEach { LevelCard(it, level, myPoints, age, guardian = false) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Child home: current level and the way to the next one. */
@Composable
fun TrustCard(graph: AppGraph, state: FamilyUiState, onOpen: () -> Unit) {
    val policy = state.settings.childPolicy
    if (!TrustLevel.availableFor(policy.ageBand)) return
    val level = policy.trustLevel
    val next = level.next
    val myPoints = rememberTaskPoints(graph, state.settings.deviceId.orEmpty())
    SoftCard(onClick = onOpen, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ArtTile(levelArt(level), size = 52.dp, backdrop = Gradients.Mint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Лестница доверия: «${level.title}»", style = MaterialTheme.typography.titleMedium)
                Text(nextLine(next, myPoints, guardian = false), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (next != null) {
            Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                Box(Modifier.fillMaxWidth(progressTo(next, level, myPoints)).height(8.dp).clip(CircleShape).background(Gradients.Emerald))
            }
        }
    }
}

// ---- Guardian ----------------------------------------------------------------------------------------

@Composable
fun GuardianTrustScreen(graph: AppGraph, member: MemberEntity, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val policy = member.childPolicy()
    val level = policy.trustLevel
    val age = policy.ageBand
    val childPoints = rememberTaskPoints(graph, member.deviceId)
    val name = member.displayName.ifBlank { "Ребёнок" }

    DetailScaffold(title = "Лестница доверия", onBack = onBack) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LadderHero(level, childPoints, nextLine(level.next, childPoints, guardian = true))
            Text(
                "Правила мягчают постепенно. $name видит лестницу у себя и может попросить следующий уровень, когда наберёт баллы. " +
                    "Ваши настройки не меняются: если вернуть уровень ниже, всё станет как было.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            TrustLevel.entries.forEach { l ->
                val ageLocked = age.maxAge < l.minAge
                LevelCard(l, level, childPoints, age, guardian = true, action = if (l == level || ageLocked) null else {
                    {
                        val raise = l > level
                        PillButton(
                            if (raise) "Включить уровень «${l.title}»" else "Вернуть уровень «${l.title}»",
                            modifier = Modifier.fillMaxWidth(),
                            fill = if (raise) Gradients.Emerald else tintedFill(),
                            contentColor = if (raise) Color.White else MaterialTheme.colorScheme.onSurface,
                            elevated = raise,
                        ) { scope.launch { graph.guardian.setTrustLevel(member.deviceId, l) } }
                    }
                })
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
