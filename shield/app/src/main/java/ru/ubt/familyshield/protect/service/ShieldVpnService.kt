package ru.ubt.familyshield.protect.service

import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import ru.ubt.familyshield.ShieldApp
import ru.ubt.familyshield.core.notify.Notifier
import ru.ubt.familyshield.shared.billing.Feature
import ru.ubt.familyshield.shared.dns.DnsBypass
import ru.ubt.familyshield.shared.dns.DnsDecision
import ru.ubt.familyshield.shared.dns.DnsMessage
import ru.ubt.familyshield.shared.dns.DnsPolicy
import ru.ubt.familyshield.shared.dns.IpPackets
import ru.ubt.familyshield.shared.dns.UdpPacket
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.MemberRole
import ru.ubt.familyshield.shared.model.WebCategory
import ru.ubt.familyshield.shared.model.WebFilterSettings
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Local DNS filter. Only DNS traffic is routed into the tunnel (plus well-known public resolvers so
 * hard-coded DNS cannot bypass it); everything else goes directly to the network. Nothing is sent
 * to our servers: queries are answered locally or forwarded to the chosen family DNS resolver.
 */
class ShieldVpnService : VpnService() {
    companion object {
        private const val TAG = "ShieldVpn"
        const val ACTION_START = "start"
        const val ACTION_STOP = "stop"
        const val ACTION_RELOAD = "reload"
        private const val VPN_ADDRESS = "10.111.222.2"
        private const val DNS_ADDRESS = "10.111.222.1"
        private val REPORTED = setOf(WebCategory.ADULT, WebCategory.GAMBLING, WebCategory.DRUGS, WebCategory.PROXY_VPN, WebCategory.DATING, WebCategory.VIOLENCE)

        @Volatile var running = false
            private set
        @Volatile var blockedToday = 0
            private set

        fun start(context: Context) = send(context, ACTION_START)
        fun stop(context: Context) {
            if (running) send(context, ACTION_STOP)
        }
        fun reload(context: Context) {
            if (running) send(context, ACTION_RELOAD)
        }

        private fun send(context: Context, action: String) {
            runCatching { context.startService(Intent(context, ShieldVpnService::class.java).setAction(action)) }
        }
    }

    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private var output: FileOutputStream? = null
    private val pool = Executors.newFixedThreadPool(4)
    @Volatile private var policy: DnsPolicy? = null
    @Volatile private var settings: WebFilterSettings = WebFilterSettings()
    private val cache = object : LinkedHashMap<String, Pair<ByteArray, Long>>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<ByteArray, Long>>?) = size > 2000
    }
    private val reportedDomains = HashSet<String>()
    private var reportedDate = LocalDate.now()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RELOAD -> loadPolicy()
            else -> if (tun == null) establish() else loadPolicy()
        }
        return START_STICKY
    }

    private fun loadPolicy() {
        val graph = (application as ShieldApp).graph
        val s = graph.settings.current
        settings = when (s.role) {
            MemberRole.CHILD -> s.childPolicy.webFilter
            MemberRole.ELDER -> s.elderPolicy.webFilter
            else -> WebFilterSettings(enabled = false)
        }.let { w ->
            // Safe search and categories are premium; phishing protection uses the free threat feed.
            if (!graph.entitlements.has(Feature.WEB_FILTER) && !graph.entitlements.has(Feature.LINK_SCAN)) {
                w.copy(categories = w.categories.intersect(setOf(WebCategory.PHISHING, WebCategory.SCAM, WebCategory.MALWARE)), safeSearch = false)
            } else {
                w
            }
        }
        val serverHost = runCatching { java.net.URI(s.serverUrl).host }.getOrNull()
        policy = DnsPolicy(settings, graph.threats.categoryLists(settings.categories), setOfNotNull(serverHost))
        synchronized(cache) { cache.clear() }
    }

    private fun establish() {
        loadPolicy()
        val builder = Builder()
            .setSession("Семейный щит: веб-фильтр")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(DNS_ADDRESS)
            .addRoute(DNS_ADDRESS, 32)
            .setMtu(1500)
            .setBlocking(true)
        DnsBypass.publicResolversV4.forEach { runCatching { builder.addRoute(it, 32) } }
        runCatching { builder.addDisallowedApplication(packageName) }
        val notifier = (application as ShieldApp).graph.notifier
        builder.setConfigureIntent(notifier.openAppIntent("protection", 77))
        tun = runCatching { builder.establish() }.onFailure { Log.w(TAG, "establish failed", it) }.getOrNull()
        val fd = tun ?: run {
            stopSelf()
            return
        }
        running = true
        output = FileOutputStream(fd.fileDescriptor)
        reader = Thread({ readLoop(FileInputStream(fd.fileDescriptor)) }, "shield-vpn").apply { start() }
    }

    private fun readLoop(input: FileInputStream) {
        val buf = ByteArray(32_767)
        while (!Thread.currentThread().isInterrupted) {
            val n = try {
                input.read(buf)
            } catch (e: Exception) {
                break
            }
            if (n <= 0) continue
            val packet = IpPackets.parseUdp(buf, n) ?: continue
            if (packet.destinationPort != 53) continue
            runCatching { handle(packet) }.onFailure { Log.w(TAG, "packet error: ${it.message}") }
        }
    }

    private fun handle(packet: UdpPacket) {
        val q = DnsMessage.parseQuestion(packet.payload) ?: return
        when (val d = policy?.decide(q.name) ?: DnsDecision.Allow) {
            is DnsDecision.Block -> {
                write(IpPackets.buildUdpResponse(packet, DnsMessage.blocked(packet.payload, q)))
                onBlocked(q.name, d)
            }
            is DnsDecision.Rewrite -> pool.execute {
                val addresses = resolve(d.target, q.type)
                val response = if (addresses.isNullOrEmpty()) forward(packet.payload) else DnsMessage.answer(packet.payload, q, addresses, 300)
                response?.let { write(IpPackets.buildUdpResponse(packet, it)) }
            }
            DnsDecision.Allow -> {
                val key = "${q.name}/${q.type}"
                val cached = synchronized(cache) { cache[key] }
                if (cached != null && cached.second > System.currentTimeMillis()) {
                    write(IpPackets.buildUdpResponse(packet, DnsMessage.withId(cached.first, q.id)))
                    return
                }
                pool.execute {
                    val response = forward(packet.payload) ?: DnsMessage.servfail(packet.payload, q)
                    write(IpPackets.buildUdpResponse(packet, response))
                    DnsMessage.readAddresses(response)?.let { (_, ttl) ->
                        val ttlMs = ttl.coerceIn(30, 300) * 1000L
                        synchronized(cache) { cache[key] = response to System.currentTimeMillis() + ttlMs }
                    }
                }
            }
        }
    }

    private fun resolve(name: String, type: Int): List<ByteArray>? {
        if (type != DnsMessage.TYPE_A && type != DnsMessage.TYPE_AAAA) return null
        val query = DnsMessage.buildQuery((System.nanoTime() and 0xFFFF).toInt(), name, type)
        val response = forward(query) ?: return null
        return DnsMessage.readAddresses(response)?.first
    }

    private fun forward(query: ByteArray): ByteArray? {
        for (server in listOf(settings.upstream.primary, settings.upstream.secondary)) {
            try {
                DatagramSocket().use { socket ->
                    protect(socket)
                    socket.soTimeout = 2_500
                    val address = InetAddress.getByName(server)
                    socket.send(DatagramPacket(query, query.size, address, 53))
                    val buf = ByteArray(4096)
                    val reply = DatagramPacket(buf, buf.size)
                    socket.receive(reply)
                    return buf.copyOf(reply.length)
                }
            } catch (_: SocketTimeoutException) {
            } catch (e: Exception) {
                Log.w(TAG, "upstream $server failed: ${e.message}")
            }
        }
        return null
    }

    private fun write(bytes: ByteArray) {
        val out = output ?: return
        synchronized(out) { runCatching { out.write(bytes) } }
    }

    private fun onBlocked(name: String, d: DnsDecision.Block) {
        blockedToday++
        val graph = (application as ShieldApp).graph
        val category = d.category ?: return
        if (graph.settings.current.role != MemberRole.CHILD || category !in REPORTED) return
        val today = LocalDate.now()
        synchronized(reportedDomains) {
            if (today != reportedDate) {
                reportedDomains.clear()
                reportedDate = today
                blockedToday = 0
            }
            val domain = ru.ubt.familyshield.shared.detect.UrlAnalyzer.registrableDomain(name)
            if (!reportedDomains.add(domain)) return
            graph.protection.alerts.raise(
                if (category == WebCategory.PROXY_VPN) AlertType.FILTER_BYPASS else AlertType.BLOCKED_SITE,
                "Заблокирован сайт", "$domain — категория «${categoryTitle(category)}»",
                dedupeKey = "site-$domain", cooldownMs = 24 * 3_600_000L,
            )
        }
    }

    private fun categoryTitle(c: WebCategory) = when (c) {
        WebCategory.ADULT -> "18+"
        WebCategory.GAMBLING -> "азартные игры"
        WebCategory.DRUGS -> "наркотики"
        WebCategory.PROXY_VPN -> "обход блокировок"
        WebCategory.DATING -> "знакомства"
        WebCategory.VIOLENCE -> "насилие"
        else -> c.name.lowercase()
    }

    override fun onRevoke() {
        val graph = (application as ShieldApp).graph
        if (graph.settings.current.role == MemberRole.CHILD) {
            graph.protection.alerts.raise(AlertType.FILTER_BYPASS, "Веб-фильтр отключён", "На телефоне включили другой VPN или отключили фильтр", dedupeKey = "vpn-revoked", cooldownMs = 30 * 60_000L)
        }
        graph.notifier.info(Notifier.ID_FOREGROUND + 5, "Веб-фильтр выключен", "Откройте приложение, чтобы снова включить защиту", "protection")
        shutdown()
        super.onRevoke()
    }

    private fun shutdown() {
        running = false
        reader?.interrupt()
        reader = null
        runCatching { tun?.close() }
        tun = null
        output = null
    }

    override fun onDestroy() {
        shutdown()
        pool.shutdownNow()
        runCatching { pool.awaitTermination(1, TimeUnit.SECONDS) }
        super.onDestroy()
    }
}
