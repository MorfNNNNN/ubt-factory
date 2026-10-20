package ru.ubt.familyshield.shared.detect

import ru.ubt.familyshield.shared.model.AppCategory

/** Knowledge about popular Android packages relevant to family safety in Russia. */
object AppCatalog {
    val banks: Set<String> = BANK_PACKAGES

    /** Apps that are not banks but give access to money or identity. */
    val sensitiveNonBank: Set<String> = setOf(
        "ru.rostel", // Госуслуги
        "ru.gosuslugi.goskey", // Госключ
        "ru.ozon.app.android",
        "com.wildberries.ru",
        "com.avito.android",
        "ru.yandex.money",
        "ru.nspk.mirpay", // Mir Pay
        "ru.nalog.www", // Налоги ФЛ
        "ru.fns.lkfl",
    )

    fun isMoneyApp(pkg: String): Boolean = pkg in banks || pkg in sensitiveNonBank

    /** Remote access / screen sharing tools scammers ask victims to install. */
    val remoteAccess: Map<String, String> = mapOf(
        "com.anydesk.anydeskandroid" to "AnyDesk",
        "com.anydesk.adcontrol.ad1" to "AnyDesk Control",
        "com.teamviewer.quicksupport.market" to "TeamViewer QuickSupport",
        "com.teamviewer.teamviewer.market.mobile" to "TeamViewer",
        "com.teamviewer.host.market" to "TeamViewer Host",
        "com.carriez.flutter_hbb" to "RustDesk",
        "com.sand.airdroid" to "AirDroid",
        "com.sand.airsupport" to "AirDroid Remote Support",
        "com.sand.aircast" to "AirDroid Cast",
        "com.sand.airmirror" to "AirMirror",
        "com.google.chromeremotedesktop" to "Chrome Remote Desktop",
        "com.splashtop.remote.pad.v2" to "Splashtop",
        "com.splashtop.streamer.addon.knox" to "Splashtop Streamer",
        "com.realvnc.viewer.android" to "VNC Viewer",
        "com.microsoft.rdc.androidx" to "Remote Desktop",
        "com.awesun.control" to "AweSun",
        "com.oray.sunlogin" to "SunLogin",
        "com.zoho.assist.agent" to "Zoho Assist",
        "com.rsupport.mobizen.sec" to "Mobizen",
        "com.hoptodesk.hoptodesk" to "HopToDesk",
    )

    fun remoteAccessName(pkg: String): String? = remoteAccess[pkg]
        ?: if (REMOTE_HINTS.any { pkg.contains(it) }) "Программа удалённого доступа" else null

    private val REMOTE_HINTS = listOf("anydesk", "teamviewer", "rustdesk", "rudesktop", "remotedesktop", "remote.support", "screenshare", "quicksupport")

    val messengers: Map<String, String> = mapOf(
        "org.telegram.messenger" to "Telegram",
        "org.telegram.messenger.web" to "Telegram",
        "org.thunderdog.challegram" to "Telegram X",
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.viber.voip" to "Viber",
        "com.vkontakte.android" to "ВКонтакте",
        "com.vk.im" to "VK Мессенджер",
        "ru.oneme.app" to "MAX",
        "ru.ok.android" to "Одноклассники",
        "ru.ok.messages" to "ТамТам",
        "com.discord" to "Discord",
        "org.thoughtcrime.securesms" to "Signal",
        "com.skype.raider" to "Skype",
        "com.icq.mobile.client" to "ICQ",
        "com.snapchat.android" to "Snapchat",
        "com.instagram.android" to "Instagram",
        "com.facebook.orca" to "Messenger",
        "com.zhiliaoapp.musically" to "TikTok",
        "com.ss.android.ugc.trill" to "TikTok",
        "video.like" to "Likee",
        "jp.naver.line.android" to "LINE",
        "com.imo.android.imoim" to "imo",
        "ru.mail.mailapp" to "Почта Mail.ru",
        "ru.yandex.mail" to "Яндекс Почта",
        "com.google.android.gm" to "Gmail",
    )

    val smsApps: Set<String> = setOf(
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.android.messaging",
        "com.huawei.message",
        "com.oneplus.mms",
        "com.miui.smsextra",
        "com.coloros.mms",
        "com.motorola.messaging",
        "com.sonyericsson.conversations",
        "com.textra",
        "xyz.klinker.messenger",
    )

    fun isMessaging(pkg: String): Boolean = pkg in messengers || pkg in smsApps

    val dialers: Set<String> = setOf(
        "com.android.dialer", "com.google.android.dialer", "com.samsung.android.dialer", "com.samsung.android.incallui",
        "com.android.phone", "com.android.server.telecom", "com.android.incallui", "com.huawei.contacts", "com.android.contacts",
        "com.samsung.android.contacts", "com.google.android.contacts", "com.miui.contacts", "com.coloros.phonemanager",
        "com.android.emergency", "com.google.android.apps.safetyhub", "com.oneplus.dialer",
    )

    val browsers: Map<String, String> = mapOf(
        "com.android.chrome" to "Chrome",
        "com.yandex.browser" to "Яндекс Браузер",
        "com.yandex.browser.lite" to "Яндекс Браузер Лайт",
        "ru.yandex.searchplugin" to "Яндекс",
        "org.mozilla.firefox" to "Firefox",
        "org.mozilla.focus" to "Firefox Focus",
        "com.opera.browser" to "Opera",
        "com.opera.mini.native" to "Opera Mini",
        "com.opera.gx" to "Opera GX",
        "com.microsoft.emmx" to "Edge",
        "com.brave.browser" to "Brave",
        "com.duckduckgo.mobile.android" to "DuckDuckGo",
        "com.sec.android.app.sbrowser" to "Samsung Internet",
        "com.mi.globalbrowser" to "Mi Browser",
        "com.huawei.browser" to "Huawei Browser",
        "com.kiwibrowser.browser" to "Kiwi",
        "com.vivaldi.browser" to "Vivaldi",
        "ru.atom.browser" to "Атом",
        "org.torproject.torbrowser" to "Tor Browser",
    )

    /** VPN, proxy and anonymity tools a child may use to bypass filters. */
    val bypassTools: Map<String, String> = mapOf(
        "org.torproject.torbrowser" to "Tor Browser",
        "org.torproject.android" to "Orbot (Tor)",
        "com.psiphon3" to "Psiphon",
        "com.psiphon3.subscription" to "Psiphon Pro",
        "ch.protonvpn.android" to "Proton VPN",
        "com.nordvpn.android" to "NordVPN",
        "com.expressvpn.vpn" to "ExpressVPN",
        "com.surfshark.vpnclient.android" to "Surfshark",
        "free.vpn.unblock.proxy.turbovpn" to "Turbo VPN",
        "hotspotshield.android.vpn" to "Hotspot Shield",
        "com.tunnelbear.android" to "TunnelBear",
        "com.cloudflare.onedotonedotonedotone" to "1.1.1.1 WARP",
        "com.v2ray.ang" to "v2rayNG",
        "com.github.shadowsocks" to "Shadowsocks",
        "app.hiddify.com" to "Hiddify",
        "com.windscribe.vpn" to "Windscribe",
        "com.adguard.vpn" to "AdGuard VPN",
        "de.blinkt.openvpn" to "OpenVPN",
        "net.openvpn.openvpn" to "OpenVPN Connect",
        "com.wireguard.android" to "WireGuard",
        "com.zaneschepke.wireguardautotunnel" to "WG Tunnel",
        "io.nekohasekai.sfa" to "sing-box",
        "com.outline.android.client" to "Outline",
        "org.outline.android.client" to "Outline",
        "com.amnezia.vpn" to "AmneziaVPN",
        "com.veepn.vpn" to "VeePN",
        "com.kaspersky.secure.connection" to "Kaspersky VPN",
    )

    fun isBypassTool(pkg: String): Boolean = pkg in bypassTools ||
        (pkg.contains("vpn", ignoreCase = true) && !pkg.startsWith("ru.ubt.familyshield"))

    val appStores: Map<String, String> = mapOf(
        "ru.vk.store" to "RuStore",
        "com.android.vending" to "Google Play",
        "com.huawei.appmarket" to "AppGallery",
        "com.sec.android.app.samsungapps" to "Galaxy Store",
        "com.xiaomi.market" to "GetApps",
        "com.xiaomi.mipicks" to "GetApps",
        "com.heytap.market" to "App Market",
        "com.oppo.market" to "App Market",
        "com.bbk.appstore" to "V-Appstore",
        "com.amazon.venezia" to "Amazon Appstore",
        "com.nashstore" to "NashStore",
        "com.apkpure.aegon" to "APKPure",
        "com.uptodown" to "Uptodown",
        "com.aurora.store" to "Aurora Store",
        "org.fdroid.fdroid" to "F-Droid",
    )

    /** Installers we consider trustworthy sources (official stores and system setup). */
    val trustedInstallers: Set<String> = setOf(
        "ru.vk.store", "com.android.vending", "com.huawei.appmarket", "com.sec.android.app.samsungapps", "com.xiaomi.market",
        "com.xiaomi.mipicks", "com.heytap.market", "com.oppo.market", "com.bbk.appstore", "com.amazon.venezia",
        "com.google.android.feedback", "com.android.managedprovisioning", "com.samsung.android.app.omcagent",
    )

    val social: Set<String> = setOf(
        "com.vkontakte.android", "ru.ok.android", "com.instagram.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "video.like", "com.snapchat.android", "com.pinterest", "com.twitter.android", "com.facebook.katana", "ru.zen.android",
        "com.reddit.frontpage", "com.tumblr", "ru.yappy.app",
    )

    val video: Set<String> = setOf(
        "com.google.android.youtube", "ru.rutube.app", "com.vk.vkvideo", "tv.twitch.android.app", "ru.kinopoisk", "ru.more.play",
        "ru.ivi.client", "ru.start.androidmobile", "ru.rt.video.app.mobile", "gpm.tnt_premier", "com.netflix.mediaclient",
        "com.google.android.apps.youtube.kids", "com.amediateka.amediatekaapp",
    )

    val games: Set<String> = setOf(
        "com.roblox.client", "com.mojang.minecraftpe", "com.supercell.brawlstars", "com.supercell.clashofclans",
        "com.supercell.clashroyale", "com.supercell.squad", "com.axlebolt.standoff2", "com.tencent.ig", "com.pubg.imobile",
        "com.miHoYo.GenshinImpact", "com.HoYoverse.hkrpgoversea", "com.kiloo.subwaysurf", "com.innersloth.spacemafia",
        "com.dts.freefireth", "com.dts.freefiremax", "com.activision.callofduty.shooter", "com.king.candycrushsaga",
        "com.outfit7.mytalkingtom2", "com.outfit7.mytalkingangelafree", "com.miniclip.eightballpool", "com.ea.gp.fifamobile",
        "com.garena.game.codm", "com.mobile.legends", "com.YoStarEN.Arknights", "com.nianticlabs.pokemongo",
        "com.imangi.templerun2", "com.playrix.homescapes", "com.playrix.gardenscapes", "com.playrix.township",
        "com.fingersoft.hillclimb", "com.ketchapp.stack", "com.sybogames.subway.surfers.game",
    )

    val music: Set<String> = setOf(
        "ru.yandex.music", "com.vk.music", "com.zvooq.openplay", "com.spotify.music", "com.google.android.apps.youtube.music",
        "com.apple.android.music", "ru.mts.music.android",
    )

    val education: Set<String> = setOf(
        "com.duolingo", "ru.dnevnik.app", "ru.mos.mes", "com.google.android.apps.classroom", "org.khanacademy.android",
        "com.photomath.photomath", "ru.uchi.uchi", "ru.yandex.uchebnik", "ru.skysmart.app", "com.quizlet.quizletandroid",
        "org.wikipedia", "ru.foxford.app", "ru.sferum.app",
    )

    val shopping: Set<String> = setOf("ru.ozon.app.android", "com.wildberries.ru", "com.avito.android", "ru.yandex.market", "com.alibaba.aliexpresshd", "ru.megamarket.marketplace", "com.lamoda.lite")

    val navigation: Set<String> = setOf(
        "ru.yandex.yandexmaps", "ru.dublgis.dgismobile", "com.google.android.apps.maps", "ru.yandex.yandexnavi", "ru.yandex.taxi",
        "ru.mosgortrans.app", "ru.yandex.metro",
    )

    /** Android ApplicationInfo.CATEGORY_* constants (kept here to stay platform independent). */
    object AndroidCategory {
        const val GAME = 0
        const val AUDIO = 1
        const val VIDEO = 2
        const val IMAGE = 3
        const val SOCIAL = 4
        const val NEWS = 5
        const val MAPS = 6
        const val PRODUCTIVITY = 7
        const val ACCESSIBILITY = 8
    }

    fun categoryOf(pkg: String, androidCategory: Int? = null, isGameFlag: Boolean = false): AppCategory = when {
        pkg in games -> AppCategory.GAMES
        isMessaging(pkg) -> AppCategory.MESSAGING
        pkg in social -> AppCategory.SOCIAL
        pkg in video -> AppCategory.VIDEO
        pkg in browsers -> AppCategory.BROWSER
        pkg in education -> AppCategory.EDUCATION
        pkg in music -> AppCategory.MUSIC
        pkg in shopping -> AppCategory.SHOPPING
        isMoneyApp(pkg) -> AppCategory.FINANCE
        pkg in navigation -> AppCategory.NAVIGATION
        isGameFlag || androidCategory == AndroidCategory.GAME -> AppCategory.GAMES
        androidCategory == AndroidCategory.SOCIAL -> AppCategory.SOCIAL
        androidCategory == AndroidCategory.VIDEO -> AppCategory.VIDEO
        androidCategory == AndroidCategory.AUDIO -> AppCategory.MUSIC
        androidCategory == AndroidCategory.MAPS -> AppCategory.NAVIGATION
        androidCategory == AndroidCategory.IMAGE -> AppCategory.PHOTO
        androidCategory == AndroidCategory.PRODUCTIVITY -> AppCategory.PRODUCTIVITY
        else -> AppCategory.OTHER
    }

    /** Apps a child can always use regardless of limits (calls, emergency). */
    fun isEssential(pkg: String): Boolean = pkg in dialers || pkg in smsApps || pkg.startsWith("ru.ubt.familyshield") ||
        pkg == "com.android.systemui" || pkg == "android"
}
