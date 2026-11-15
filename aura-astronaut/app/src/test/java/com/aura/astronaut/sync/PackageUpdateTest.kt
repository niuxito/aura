package com.aura.astronaut.sync

import com.aura.astronaut.mission.CanonicalJson
import com.aura.astronaut.mission.MissionPackageParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class PackageUpdateTest {

    init {
        com.aura.astronaut.i18n.TestStrings.install("es")
    }

    private val text = File("../contract/mission-package.example.json").readText()
    private val current = MissionPackageParser.parse(text).pkg

    /** Another valid package: changes the version and recomputes the checksum like the Manager. */
    private fun republished(version: Int): String {
        val tree = Json.parseToJsonElement(text).jsonObject.toMutableMap()
        tree["packageVersion"] = JsonPrimitive(version)
        tree.remove("checksum")
        val sha = MessageDigest.getInstance("SHA-256").digest(CanonicalJson.write(JsonObject(tree)).toByteArray(Charsets.UTF_8))
        tree["checksum"] = JsonPrimitive("sha256:" + sha.joinToString("") { "%02x".format(it) })
        return JsonObject(tree).toString()
    }

    @Test
    fun `the same package is not reloaded`() {
        assertEquals(PackageDecision.Keep(current.packageVersion), PackageUpdate.decide(current, text))
    }

    @Test
    fun `a different valid package is loaded`() {
        val d = PackageUpdate.decide(current, republished(7))
        assertTrue(d is PackageDecision.Replace)
        assertEquals(7, (d as PackageDecision.Replace).loaded.pkg.packageVersion)
    }

    @Test
    fun `with no package loaded the downloaded one is accepted`() {
        assertTrue(PackageUpdate.decide(null, text) is PackageDecision.Replace)
    }

    @Test
    fun `an old schema package is discarded`() {
        val v4 = File("../contract/mission-package.v4.example.json").readText()
        val d = PackageUpdate.decide(current, v4)
        assertTrue(d is PackageDecision.Reject)
        assertTrue((d as PackageDecision.Reject).reason.contains("v4"))
    }

    @Test
    fun `a package with an altered checksum is discarded`() {
        val tampered = text.replaceFirst("\"packageVersion\": 1", "\"packageVersion\": 9")
        check(tampered != text)
        assertTrue(PackageUpdate.decide(current, tampered) is PackageDecision.Reject)
    }

    @Test
    fun `a response that is not JSON is discarded`() {
        assertTrue(PackageUpdate.decide(current, "<html>error</html>") is PackageDecision.Reject)
    }
}
