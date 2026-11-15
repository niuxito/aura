package com.aura.astronaut.sync

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/** Real Manager v5 responses: policy violations are multilingual texts. */
class ManagerResponseTest {

    init {
        com.aura.astronaut.i18n.TestStrings.install("es")
    }

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reads multilingual and plain-text violations`() {
        val body = """{"id":"x","policy":{"withinPolicy":false,"violations":[{"en":"Package v6 does not exist.","es":"El paquete v6 no existe."},"legacy text"]},"review":{"status":"pending","note":""}}"""
        val a = json.decodeFromString(ServerAdaptation.serializer(), body)
        assertEquals(listOf("El paquete v6 no existe.", "legacy text"), a.policy!!.violationTexts)
    }
}
