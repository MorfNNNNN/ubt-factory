package ru.ubt.familyshield.protect.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import ru.ubt.familyshield.ShieldApp
import ru.ubt.familyshield.protect.ChildEnforcer
import ru.ubt.familyshield.protect.ProtectionCenter
import ru.ubt.familyshield.protect.ShieldRuntime

/**
 * Detects which app is in the foreground (no screen content is read or stored). Used to enforce a
 * child's rules instantly, to warn an elder when a bank app is opened during a suspicious call, and
 * to protect the app's own settings from being switched off by a child.
 */
class ShieldAccessibilityService : AccessibilityService() {
    private lateinit var p: ProtectionCenter
    private var lastPackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        p = (application as ShieldApp).graph.protection
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!::p.isInitialized) return
        val pkg = event.packageName?.toString() ?: return
        ShieldRuntime.lastInteractionAt = System.currentTimeMillis()
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return
        if (pkg in ChildEnforcer.TAMPER_PACKAGES && p.child.tamperGuardActive() && p.child.isTamperScreen(pkg, windowTexts(event))) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            p.child.onTamper(pkg)
            return
        }
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || pkg == lastPackage) return
        // Ignore our own overlays and system UI flicker.
        if (pkg == packageName || pkg == "com.android.systemui") return
        lastPackage = pkg
        ShieldRuntime.lastForegroundPackage = pkg
        runCatching {
            p.child.onForeground(pkg)
            p.elder.onForeground(pkg)
        }
    }

    /** Visible texts of the current window, only used to recognise our own settings/uninstall screens. */
    private fun windowTexts(event: AccessibilityEvent): List<CharSequence> {
        val texts = ArrayList<CharSequence>(event.text)
        val root: AccessibilityNodeInfo = rootInActiveWindow ?: return texts
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || depth > 12 || texts.size > 200) return
            node.text?.let { texts += it }
            node.contentDescription?.let { texts += it }
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        return texts
    }

    override fun onInterrupt() = Unit
}
