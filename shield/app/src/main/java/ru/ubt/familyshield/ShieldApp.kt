package ru.ubt.familyshield

import android.app.Application
import kotlinx.coroutines.launch
import ru.ubt.familyshield.core.push.ShieldPushService

open class ShieldApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = createGraph()
        graph.notifier.createChannels()
        startBackground()
    }

    /** Store-screenshot tests replace these to run without Android Keystore, push and WorkManager. */
    protected open fun createGraph(): AppGraph = AppGraph(this)

    protected open fun startBackground() {
        ShieldPushService.init(this)
        graph.scheduleWork()
        graph.appScope.launch {
            graph.settings.incrementLaunches()
            graph.protection.apply()
            ShieldPushService.fetchToken { token -> graph.appScope.launch { graph.registerPushToken(token) } }
        }
    }
}
