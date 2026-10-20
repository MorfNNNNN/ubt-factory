package ru.ubt.familyshield.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ru.ubt.familyshield.ShieldApp
import ru.ubt.familyshield.core.notify.Notifier

class MainActivity : ComponentActivity() {
    private val graph get() = (application as ShieldApp).graph
    private val routeRequests = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent { ShieldRoot(graph, this, routeRequests) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        graph.billing.handleIntent(intent)
        intent.getStringExtra(Notifier.EXTRA_ROUTE)?.let { routeRequests.value = it }
        intent.data?.takeIf { it.scheme == "familyshield" }?.let { routeRequests.value = "pair:" + it.toString() }
        if (intent.hasCategory(Intent.CATEGORY_HOME)) routeRequests.value = "elder"
    }
}
