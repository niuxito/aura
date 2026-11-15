package com.aura.astronaut.mission

import com.aura.astronaut.R
import com.aura.astronaut.i18n.str
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

/** Resultado de comprobar el checksum. No verificarlo no impide usar el paquete. */
enum class ChecksumStatus { VERIFIED, MISMATCH, MISSING }

data class LoadedPackage(val pkg: MissionPackage, val checksum: ChecksumStatus)

/** El paquete no se puede usar; [message] se muestra al astronauta tal cual. */
class PackageException(message: String, cause: Throwable? = null) : Exception(message, cause)

object MissionPackageParser {

    /** Campos que el Manager añada en el futuro no rompen la lectura; los que faltan, sí. */
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Lee y comprueba un paquete. Lanza [PackageException] si no es un paquete v5 utilizable;
     * un checksum que no coincide se informa en [LoadedPackage.checksum] pero no bloquea.
     */
    fun parse(text: String): LoadedPackage {
        val tree = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: Exception) {
            throw PackageException(str(R.string.pkg_not_json), e)
        }
        val schema = (tree["schema"] as? JsonPrimitive)?.content
        if (schema != SUPPORTED_SCHEMA) {
            throw PackageException(str(R.string.pkg_schema, schema ?: str(R.string.pkg_no_schema), SUPPORTED_SCHEMA))
        }
        val pkg = try {
            json.decodeFromJsonElement(MissionPackage.serializer(), tree)
        } catch (e: SerializationException) {
            throw PackageException(str(R.string.pkg_contract, schema, e.message.orEmpty()), e)
        } catch (e: IllegalArgumentException) {
            throw PackageException(str(R.string.pkg_contract, schema, e.message.orEmpty()), e)
        }
        val problems = integrityProblems(pkg)
        if (problems.isNotEmpty()) {
            throw PackageException(str(R.string.pkg_incoherent, problems.joinToString("; ")))
        }
        return LoadedPackage(pkg, checksumStatus(tree, pkg.checksum))
    }

    /** Comprobaciones de coherencia interna que el resto de la app da por hechas. */
    fun integrityProblems(pkg: MissionPackage): List<String> = buildList {
        if (pkg.crew.isEmpty()) add(str(R.string.pkg_no_crew))
        if (pkg.days.isEmpty()) add(str(R.string.pkg_no_days))
        pkg.days.forEach { d -> if (d.endMet <= d.startMet) add(str(R.string.pkg_day_order, d.label)) }
        pkg.days.zipWithNext().forEach { (a, b) ->
            if (b.startMet < a.endMet) add(str(R.string.pkg_days_overlap, a.label, b.label))
        }
        val dayNumbers = pkg.days.map { it.day }.toSet()
        val catalogIds = pkg.catalog.map { it.id }.toSet()
        pkg.crew.forEach { member ->
            val plan = pkg.plans[member.id]
            if (plan == null) {
                add(str(R.string.pkg_plan_missing, member.id))
                return@forEach
            }
            plan.forEach { dayPlan ->
                if (dayPlan.day !in dayNumbers) add(str(R.string.pkg_plan_day, member.id, dayPlan.day))
                dayPlan.activities.forEach { a ->
                    if (a.itemId !in catalogIds) add(str(R.string.pkg_plan_item, a.entryId, a.itemId))
                }
                if (dayPlan.activities.zipWithNext().any { (x, y) -> y.met < x.met }) {
                    add(str(R.string.pkg_plan_order, member.id, dayPlan.day))
                }
            }
        }

        // Autoevaluación: todas las referencias cruzadas deben existir.
        val q = pkg.questionnaire
        if (pkg.capacities.isEmpty()) add(str(R.string.pkg_no_capacities))
        if (pkg.defaultLanguage !in pkg.languages) add(str(R.string.pkg_default_language, pkg.defaultLanguage))
        val capacityIds = pkg.capacities.map { it.id }.toSet()
        val signalIds = pkg.signals.map { it.id }.toSet()
        val questionIds = q.items.map { it.id }.toSet()
        if (q.catalogItemId !in catalogIds) add(str(R.string.pkg_q_item, q.catalogItemId))
        pkg.signals.forEach { s ->
            if (s.capacityId !in capacityIds) add(str(R.string.pkg_signal_capacity, s.id, s.capacityId))
        }
        q.items.forEach { item ->
            if (item.capacityId !in capacityIds) add(str(R.string.pkg_question_capacity, item.id, item.capacityId))
            if (item.variants.isEmpty()) add(str(R.string.pkg_question_variants, item.id))
            if (item.scale.max <= item.scale.min) add(str(R.string.pkg_question_scale, item.id))
            item.triggers.filterIsInstance<QuestionTrigger.SignalValue>().forEach { t ->
                if (t.signalId !in signalIds) add(str(R.string.pkg_question_signal, item.id, t.signalId))
            }
        }
        q.consistencyChecks.forEach { c ->
            if (c.questionId !in questionIds) add(str(R.string.pkg_check_question, c.id, c.questionId))
            if (c.signalId !in signalIds) add(str(R.string.pkg_check_signal, c.id, c.signalId))
        }
    }

    private fun checksumStatus(tree: JsonObject, declared: String?): ChecksumStatus {
        if (declared.isNullOrBlank()) return ChecksumStatus.MISSING
        val body = JsonObject(tree - "checksum")
        val actual = "sha256:" + sha256Hex(CanonicalJson.write(body))
        return if (actual.equals(declared, ignoreCase = true)) ChecksumStatus.VERIFIED else ChecksumStatus.MISMATCH
    }

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/**
 * JSON canónico idéntico al `canonicalJson` del Manager (claves ordenadas, sin espacios),
 * con las cadenas escapadas como `JSON.stringify` de JavaScript.
 */
object CanonicalJson {
    fun write(element: JsonElement): String = StringBuilder().also { append(it, element) }.toString()

    private fun append(out: StringBuilder, e: JsonElement) {
        when (e) {
            is JsonObject -> {
                out.append('{')
                // Orden por unidades UTF-16, como Array.prototype.sort() en JavaScript.
                e.keys.sorted().forEachIndexed { i, key ->
                    if (i > 0) out.append(',')
                    quote(out, key)
                    out.append(':')
                    append(out, e.getValue(key))
                }
                out.append('}')
            }
            is JsonArray -> {
                out.append('[')
                e.forEachIndexed { i, item ->
                    if (i > 0) out.append(',')
                    append(out, item)
                }
                out.append(']')
            }
            JsonNull -> out.append("null")
            is JsonPrimitive -> if (e.isString) quote(out, e.content) else out.append(e.content)
        }
    }

    private fun quote(out: StringBuilder, s: String) {
        out.append('"')
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '"' -> out.append("\\\"")
                c == '\\' -> out.append("\\\\")
                c == '\b' -> out.append("\\b")
                c == '\u000C' -> out.append("\\f")
                c == '\n' -> out.append("\\n")
                c == '\r' -> out.append("\\r")
                c == '\t' -> out.append("\\t")
                c < ' ' -> out.append("\\u%04x".format(c.code))
                c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> {
                    out.append(c).append(s[i + 1])
                    i++
                }
                // JSON.stringify escapa los sustitutos sueltos.
                c.isSurrogate() -> out.append("\\u%04x".format(c.code))
                else -> out.append(c)
            }
            i++
        }
        out.append('"')
    }
}
