package ru.ubt.familyshield.shared.protocol

import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.ubt.familyshield.shared.ShieldJson
import ru.ubt.familyshield.shared.model.Alert
import ru.ubt.familyshield.shared.model.AlertType
import ru.ubt.familyshield.shared.model.ChildPolicy
import ru.ubt.familyshield.shared.model.Command
import ru.ubt.familyshield.shared.model.ElderPolicy

class SerializationTest {
    @Test
    fun payloadsRoundTripPolymorphically() {
        val payloads = listOf(
            FamilyPayload.AlertRaised(Alert("a", AlertType.BANK_APP_DURING_CALL, title = "t", body = "b", createdAt = 1)),
            FamilyPayload.ChildPolicyUpdate("d1", ChildPolicy(version = 5)),
            FamilyPayload.ElderPolicyUpdate("d2", ElderPolicy(version = 2)),
            FamilyPayload.CommandIssued("d1", Command.GrantBonus("c", 15, "Сделал уроки")),
            FamilyPayload.KeyRotation(2, mapOf("d1" to "x")),
        )
        for (p in payloads) {
            val json = ShieldJson.encodeToString(InnerMessage(1, p))
            assertEquals(p, ShieldJson.decodeFromString(InnerMessage.serializer(), json).payload)
        }
        assertTrue(FamilyPayload.CommandIssued("d", Command.Unlock("u")).requiresGuardianSignature)
        assertTrue(!FamilyPayload.AlertRaised(Alert("a", AlertType.SOS, title = "", body = "", createdAt = 0)).requiresGuardianSignature)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val json = """{"deviceId":"d","deviceToken":"t","newField":1}"""
        assertEquals("d", ShieldJson.decodeFromString(RegisterResponse.serializer(), json).deviceId)
    }
}
