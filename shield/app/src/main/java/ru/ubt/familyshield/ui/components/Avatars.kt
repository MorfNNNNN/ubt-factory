package ru.ubt.familyshield.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.ubt.familyshield.R
import ru.ubt.familyshield.ui.theme.Brand

/**
 * Illustrated family avatars. Stored values are "avatar:<id>"; values saved by earlier versions
 * (emoji) are mapped to the closest portrait so existing families keep a sensible picture.
 */
enum class AvatarArt(val id: String, @DrawableRes val res: Int, val label: String) {
    MOM("mom", R.drawable.avatar_mom, "Мама"),
    DAD("dad", R.drawable.avatar_dad, "Папа"),
    GRANDMA("grandma", R.drawable.avatar_grandma, "Бабушка"),
    GRANDPA("grandpa", R.drawable.avatar_grandpa, "Дедушка"),
    GIRL("girl", R.drawable.avatar_girl, "Дочка"),
    BOY("boy", R.drawable.avatar_boy, "Сын"),
    TEEN_GIRL("teen_girl", R.drawable.avatar_teen_girl, "Подросток"),
    TEEN_BOY("teen_boy", R.drawable.avatar_teen_boy, "Подросток"),
    TODDLER("toddler", R.drawable.avatar_toddler, "Малыш"),
    GRANDMA2("grandma2", R.drawable.avatar_grandma2, "Бабуля"),
    GRANDPA2("grandpa2", R.drawable.avatar_grandpa2, "Дедуля"),
    WOMAN("woman", R.drawable.avatar_woman, "Тётя"),
    MAN("man", R.drawable.avatar_man, "Дядя");

    val value: String get() = PREFIX + id

    companion object {
        const val PREFIX = "avatar:"

        private val legacy = mapOf(
            "👵" to GRANDMA, "🧓" to GRANDPA, "👴" to GRANDPA, "👩" to MOM, "👨" to DAD, "👧" to GIRL,
            "👦" to BOY, "🧒" to BOY, "👶" to TODDLER, "🧑" to MAN, "👱" to WOMAN,
        )

        fun default(role: String?): AvatarArt = when (role) {
            "CHILD" -> GIRL
            "ELDER" -> GRANDMA
            else -> MOM
        }

        fun of(value: String?, role: String? = null): AvatarArt {
            if (value != null && value.startsWith(PREFIX)) entries.firstOrNull { it.id == value.removePrefix(PREFIX) }?.let { return it }
            value?.let { v -> legacy.entries.firstOrNull { v.startsWith(it.key) }?.let { return it.value } }
            return default(role)
        }

        /** Portraits offered first for a role in pickers. */
        fun suggested(role: String?): List<AvatarArt> = when (role) {
            "CHILD" -> listOf(GIRL, BOY, TEEN_GIRL, TEEN_BOY, TODDLER)
            "ELDER" -> listOf(GRANDMA, GRANDPA, GRANDMA2, GRANDPA2)
            else -> listOf(MOM, DAD, WOMAN, MAN)
        }.let { first -> first + entries.filter { it !in first } }
    }
}

/** Round portrait with a soft mint backdrop and an optional status ring. */
@Composable
fun AvatarImage(value: String?, role: String? = null, size: Dp = 48.dp, ring: Color? = null, modifier: Modifier = Modifier) {
    val art = AvatarArt.of(value, role)
    Box(
        modifier.size(size)
            .shadow(if (size >= 56.dp) 6.dp else 2.dp, CircleShape, ambientColor = Brand.Green.copy(alpha = 0.2f), spotColor = Brand.Green.copy(alpha = 0.2f))
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0xFFECFDF5), Color(0xFFD1FAE5))))
            .let { if (ring != null) it.border(size * 0.06f, ring, CircleShape) else it },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Image(
            painterResource(art.res), contentDescription = art.label, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size * 0.92f).offset(y = size * 0.06f),
        )
    }
}

/** Overlapping portraits of the whole family. */
@Composable
fun AvatarStack(values: List<Pair<String?, String?>>, size: Dp = 40.dp, max: Int = 5) {
    Box {
        values.take(max).forEachIndexed { i, (avatar, role) ->
            AvatarImage(avatar, role, size, ring = Color.White, modifier = Modifier.offset(x = size * 0.62f * i))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AvatarPicker(selected: String?, role: String?, onPick: (String) -> Unit) {
    val current = AvatarArt.of(selected, role)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AvatarArt.suggested(role).forEach { art ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable { onPick(art.value) }.padding(4.dp)) {
                AvatarImage(art.value, role, 60.dp, ring = if (art == current) MaterialTheme.colorScheme.primary else null)
                Text(art.label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
