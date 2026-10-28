package ru.ubt.familyshield.protect.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Warning banner drawn over the incoming-call screen (needs "display over other apps"). Plain views
 * are used on purpose: they work from a service without an activity lifecycle.
 */
object CallOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var view: View? = null

    private fun dp(context: Context, v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics).toInt()

    fun showIncomingWarning(context: Context, number: String, reasons: List<String>, high: Boolean) {
        val title = if (high) "Осторожно: вероятно мошенники" else "Звонит незнакомый номер"
        val body = buildString {
            append(number)
            reasons.take(2).forEach { append("\n• ").append(it) }
            append("\n\nНе называйте коды из СМС и не переводите деньги. Если звонят «из банка» — положите трубку и перезвоните сами.")
        }
        show(context, title, body, if (high) Color.parseColor("#B91C1C") else Color.parseColor("#B45309"), 60_000)
    }

    fun showInCallReminder(context: Context) {
        show(context, "Разговор с незнакомцем", "Не называйте коды, пароли и данные карты. Банк и полиция никогда их не спрашивают.", Color.parseColor("#1E3A8A"), 20_000)
    }

    private fun show(source: Context, title: String, body: String, color: Int, autoHideMs: Long) {
        // The application context outlives the call screening service that triggered the banner.
        val context = source.applicationContext
        if (!Settings.canDrawOverlays(context)) return
        main.post {
            hideNow(context)
            val wm = context.getSystemService(WindowManager::class.java)
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(context, 20f), dp(context, 16f), dp(context, 20f), dp(context, 16f))
                background = GradientDrawable().apply {
                    cornerRadius = dp(context, 20f).toFloat()
                    setColor(color)
                }
                addView(TextView(context).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    setTypeface(typeface, Typeface.BOLD)
                })
                addView(TextView(context).apply {
                    text = body
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    setPadding(0, dp(context, 8f), 0, 0)
                })
                addView(TextView(context).apply {
                    text = "Нажмите, чтобы скрыть"
                    setTextColor(Color.argb(200, 255, 255, 255))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    setPadding(0, dp(context, 10f), 0, 0)
                })
                setOnClickListener { hide(context) }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP
                y = dp(context, 48f)
                horizontalMargin = 0.03f
            }
            runCatching { wm.addView(card, params) }.onSuccess { view = card }
            main.postDelayed({ hide(context) }, autoHideMs)
        }
    }

    fun hide(context: Context) = main.post { hideNow(context.applicationContext) }

    private fun hideNow(context: Context) {
        val v = view ?: return
        runCatching { context.getSystemService(WindowManager::class.java).removeView(v) }
        view = null
    }
}
