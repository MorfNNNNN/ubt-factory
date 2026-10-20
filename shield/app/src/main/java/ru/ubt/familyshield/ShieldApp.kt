package ru.ubt.familyshield

import android.app.Application
import kotlinx.coroutines.launch
import ru.ubt.familyshield.core.push.ShieldPushService

class ShieldApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
        graph.notifier.createChannels()
        ShieldPushService.init(this)
        graph.scheduleWork()
        graph.appScope.launch {
            graph.settings.incrementLaunches()
            graph.protection.apply()
            ShieldPushService.fetchToken { token -> graph.appScope.launch { graph.registerPushToken(token) } }
        }
    }
}
