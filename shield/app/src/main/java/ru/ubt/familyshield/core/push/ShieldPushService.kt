package ru.ubt.familyshield.core.push

import android.app.Application
import android.util.Log
import kotlinx.coroutines.launch
import ru.rustore.sdk.pushclient.RuStorePushClient
import ru.rustore.sdk.pushclient.messaging.exception.RuStorePushClientException
import ru.rustore.sdk.pushclient.messaging.model.RemoteMessage
import ru.rustore.sdk.pushclient.messaging.service.RuStoreMessagingService
import ru.ubt.familyshield.BuildConfig
import ru.ubt.familyshield.ShieldApp
import ru.ubt.familyshield.core.sync.SyncWorker

/**
 * RuStore push: the server only sends content-free "sync" wake-ups. Real data is fetched over the
 * encrypted channel, so push providers never see family information.
 */
class ShieldPushService : RuStoreMessagingService() {
    override fun onNewToken(token: String) {
        val graph = (application as ShieldApp).graph
        graph.appScope.launch { graph.registerPushToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] == "sync") SyncWorker.runNow(applicationContext)
    }

    override fun onDeletedMessages() {
        SyncWorker.runNow(applicationContext)
    }

    override fun onError(errors: List<RuStorePushClientException>) {
        errors.forEach { Log.w("ShieldPush", "push error: ${it.message}") }
    }

    companion object {
        fun init(app: Application) {
            val projectId = BuildConfig.RUSTORE_PUSH_PROJECT_ID
            if (projectId.isBlank()) return
            runCatching { RuStorePushClient.init(app, projectId) }.onFailure { Log.w("ShieldPush", "init failed", it) }
        }

        fun fetchToken(onToken: (String) -> Unit) {
            if (!RuStorePushClient.isInitialized) return
            RuStorePushClient.getToken().addOnSuccessListener { onToken(it) }.addOnFailureListener { Log.w("ShieldPush", "token failed", it) }
        }
    }
}
