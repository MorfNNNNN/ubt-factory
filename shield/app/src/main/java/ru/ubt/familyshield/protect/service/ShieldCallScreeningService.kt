package ru.ubt.familyshield.protect.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import ru.ubt.familyshield.ShieldApp
import ru.ubt.familyshield.shared.detect.RiskLevel

/** Screens incoming calls: blocks known scam numbers and warns about risky unknown callers. */
@RequiresApi(Build.VERSION_CODES.N)
class ShieldCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        val incoming = Build.VERSION.SDK_INT < 29 || details.callDirection == Call.Details.DIRECTION_INCOMING
        if (!incoming) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val p = (application as ShieldApp).graph.protection
        val number = details.handle?.schemeSpecificPart
        // The system expects an answer within a few seconds.
        val decision = runBlocking { withTimeoutOrNull(3_000) { p.callGuard.screen(number) } }
        val response = CallResponse.Builder()
        if (decision != null && decision.block) {
            response.setDisallowCall(true).setRejectCall(true).setSkipNotification(false).setSkipCallLog(false)
            p.alerts.journal("CALL_BLOCKED", "Заблокирован звонок ${decision.info.display()}", decision.risk.reasons.joinToString("; "))
        } else if (decision != null && decision.silence && Build.VERSION.SDK_INT >= 29) {
            response.setSilenceCall(true)
        }
        respondToCall(details, response.build())
        if (decision != null && !decision.block && decision.warn) {
            p.alerts.journal("CALL_WARNING", "Предупреждение о звонке ${decision.info.display()}", decision.risk.reasons.joinToString("; "))
            CallOverlay.showIncomingWarning(this, decision.info.display(), decision.risk.reasons, decision.risk.level >= RiskLevel.HIGH)
        }
    }
}
