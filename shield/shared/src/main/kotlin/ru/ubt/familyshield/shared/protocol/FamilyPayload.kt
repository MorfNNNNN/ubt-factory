package ru.ubt.familyshield.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.ubt.familyshield.shared.model.Alert
import ru.ubt.familyshield.shared.model.AppInventory
import ru.ubt.familyshield.shared.model.CheckIn
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.DeviceStatus
import ru.ubt.familyshield.shared.model.ElderPolicy
import ru.ubt.familyshield.shared.model.FamilyNote
import ru.ubt.familyshield.shared.model.LocationFix
import ru.ubt.familyshield.shared.model.MemberProfile
import ru.ubt.familyshield.shared.model.MemberRequest
import ru.ubt.familyshield.shared.model.RequestDecision
import ru.ubt.familyshield.shared.model.SecurityReport
import ru.ubt.familyshield.shared.model.UsageReport

/**
 * Everything family devices tell each other. Payloads are serialized, encrypted with the family key
 * and only then handed to the relay server, which never sees their content.
 */
@Serializable
sealed class FamilyPayload {
    /** Payloads that change a device's behaviour must be signed by a guardian key. */
    open val requiresGuardianSignature: Boolean get() = false

    @Serializable
    @SerialName("status")
    data class Status(val status: DeviceStatus) : FamilyPayload()

    @Serializable
    @SerialName("location")
    data class Location(val fix: LocationFix) : FamilyPayload()

    @Serializable
    @SerialName("alert")
    data class AlertRaised(val alert: Alert) : FamilyPayload()

    @Serializable
    @SerialName("usage")
    data class Usage(val report: UsageReport) : FamilyPayload()

    @Serializable
    @SerialName("apps")
    data class Apps(val inventory: AppInventory) : FamilyPayload()

    @Serializable
    @SerialName("security")
    data class Security(val report: SecurityReport) : FamilyPayload()

    @Serializable
    @SerialName("profile")
    data class Profile(val profile: MemberProfile) : FamilyPayload()

    @Serializable
    @SerialName("child_policy")
    data class ChildPolicyUpdate(val targetDeviceId: String, val policy: ChildPolicy) : FamilyPayload() {
        override val requiresGuardianSignature: Boolean get() = true
    }

    @Serializable
    @SerialName("elder_policy")
    data class ElderPolicyUpdate(val targetDeviceId: String, val policy: ElderPolicy) : FamilyPayload() {
        override val requiresGuardianSignature: Boolean get() = true
    }

    @Serializable
    @SerialName("command")
    data class CommandIssued(val targetDeviceId: String, val command: Command) : FamilyPayload() {
        override val requiresGuardianSignature: Boolean get() = true
    }

    @Serializable
    @SerialName("request")
    data class Request(val request: MemberRequest) : FamilyPayload()

    @Serializable
    @SerialName("decision")
    data class Decision(val targetDeviceId: String, val decision: RequestDecision) : FamilyPayload() {
        override val requiresGuardianSignature: Boolean get() = true
    }

    @Serializable
    @SerialName("note")
    data class Note(val note: FamilyNote) : FamilyPayload()

    @Serializable
    @SerialName("checkin")
    data class CheckInDone(val checkIn: CheckIn) : FamilyPayload()

    /** A guardian announces its signing public key (X.509, base64url). */
    /**
     * Introduces a guardian signing key. Protected devices only accept it when signed by a guardian
     * they already trust (the one who paired them), so a child cannot promote itself.
     */
    @Serializable
    @SerialName("guardian_key")
    data class GuardianKey(val deviceId: String, val publicKey: String) : FamilyPayload() {
        override val requiresGuardianSignature: Boolean get() = true
    }

    /**
     * A device shares its private per-device key with guardians, so that a future family key can be
     * delivered to it without the removed members being able to read it.
     */
    @Serializable
    @SerialName("device_key")
    data class DeviceKey(val deviceId: String, val key: String) : FamilyPayload()

    /** New family key for [epoch], wrapped separately for every remaining device with its device key. */
    @Serializable
    @SerialName("key_rotation")
    data class KeyRotation(val epoch: Int, val wrappedKeys: Map<String, String>) : FamilyPayload() {
        override val requiresGuardianSignature: Boolean get() = true
    }

    /** Signed entitlement token issued by the server, re-shared so offline devices can verify the plan. */
    @Serializable
    @SerialName("entitlement")
    data class EntitlementShare(val token: String) : FamilyPayload()
}

@Serializable
data class InnerMessage(
    val sentAt: Long,
    val payload: FamilyPayload,
)
