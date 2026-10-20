package ru.ubt.familyshield.ui

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import android.widget.RemoteViews
import com.journeyapps.barcodescanner.CaptureActivity
import ru.ubt.familyshield.R
import ru.ubt.familyshield.protect.ui.SosActivity

/** Portrait QR scanner for pairing (ZXing embedded, Apache 2.0). */
class QrScanActivity : CaptureActivity()

/** Quick Settings tile: SOS in one swipe. */
class SosTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, SosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

/** Big red SOS button on the home screen. */
class SosWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pi = PendingIntent.getActivity(
            context, 0, Intent(context, SosActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_sos).apply { setOnClickPendingIntent(R.id.widget_root, pi) }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
