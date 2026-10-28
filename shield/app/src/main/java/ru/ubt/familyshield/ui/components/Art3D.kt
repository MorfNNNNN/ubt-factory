package ru.ubt.familyshield.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import ru.ubt.familyshield.R

/** 3D object art (3dicons, CC0) from res/drawable-nodpi/ic3d_*. */
object Art3D {
    val Shield = R.drawable.ic3d_shield
    val Lock = R.drawable.ic3d_lock
    val Locker = R.drawable.ic3d_locker
    val Key = R.drawable.ic3d_key
    val Trophy = R.drawable.ic3d_trophy
    val Medal = R.drawable.ic3d_medal
    val GiftBox = R.drawable.ic3d_gift_box
    val Gift = R.drawable.ic3d_gift
    val Star = R.drawable.ic3d_star
    val Crown = R.drawable.ic3d_crown
    val Wallet = R.drawable.ic3d_wallet
    val CreditCard = R.drawable.ic3d_credit_card
    val Coin = R.drawable.ic3d_coin
    val Bell = R.drawable.ic3d_bell
    val BellFestive = R.drawable.ic3d_bell_festive
    val NotifyHeart = R.drawable.ic3d_notify_heart
    val Calendar = R.drawable.ic3d_calendar
    val Clock = R.drawable.ic3d_clock
    val Stopwatch = R.drawable.ic3d_stopwatch
    val MapPin = R.drawable.ic3d_map_pin
    val CallRinging = R.drawable.ic3d_call_ringing
    val ChatBubble = R.drawable.ic3d_chat_bubble
    val ChatText = R.drawable.ic3d_chat_text
    val Mail = R.drawable.ic3d_mail
    val Tick = R.drawable.ic3d_tick
    val Heart = R.drawable.ic3d_heart
    val Target = R.drawable.ic3d_target
    val Rocket = R.drawable.ic3d_rocket
    val Fire = R.drawable.ic3d_fire
    val Notebook = R.drawable.ic3d_notebook
    val Pencil = R.drawable.ic3d_pencil
    val FileText = R.drawable.ic3d_file_text
    val Mobile = R.drawable.ic3d_mobile
    val Computer = R.drawable.ic3d_computer
    val Tools = R.drawable.ic3d_tools
    val Bulb = R.drawable.ic3d_bulb
    val Megaphone = R.drawable.ic3d_megaphone
    val Mic = R.drawable.ic3d_mic
    val Chart = R.drawable.ic3d_chart
    val Link = R.drawable.ic3d_link
    val Bag = R.drawable.ic3d_bag
    val Moon = R.drawable.ic3d_moon
    val Sun = R.drawable.ic3d_sun
    val Umbrella = R.drawable.ic3d_umbrella
    val Travel = R.drawable.ic3d_travel
}

/** A 3D object on a soft rounded backdrop, for list rows and cards. */
@Composable
fun ArtTile(@DrawableRes res: Int, modifier: Modifier = Modifier, size: Dp = 56.dp, backdrop: Brush? = null) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f))
            .background(backdrop ?: SolidColor(MaterialTheme.colorScheme.surfaceVariant)),
        contentAlignment = Alignment.Center,
    ) { Illustration(res, size = size * 0.8f) }
}
