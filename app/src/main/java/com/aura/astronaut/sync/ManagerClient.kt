package com.aura.astronaut.sync

import com.aura.astronaut.data.LocalAdaptation
import com.aura.astronaut.data.SOURCE_INTERFACE
import com.aura.astronaut.data.LocalAlert
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Serializable
data class EvidenceDto(
    val indicator: String,
    val value: String,
    val unit: String? = null,
    /** `manual`, `sensor` o `simulado`. */
    val source: String,
    val met: Long? = null,
)

/** Lo que espera `POST /api/adaptations` (AdaptationInput del Manager). */
@Serializable
data class AdaptationDto(
    val id: String,
    val packageVersion: Int,
    val crewId: String,
    val entryId: String,
    val type: String,
    val replacementItemId: String? = null,
    val newMet: Long? = null,
    val decidedAtMet: Long,
    val reason: String,
    val evidence: List<EvidenceDto>,
    val source: String = SOURCE_INTERFACE,
)

@Serializable
data class PolicyDto(val withinPolicy: Boolean, val violations: List<String> = emptyList())

@Serializable
data class ReviewDto(val status: String, val note: String = "")

/** Adaptación registrada en control de misión (solo los campos que usa la interfaz). */
@Serializable
data class ServerAdaptation(val id: String, val policy: PolicyDto? = null, val review: ReviewDto? = null)

@Serializable
private data class AdaptationsResponse(val adaptations: List<ServerAdaptation> = emptyList())

/** Lo que espera `POST /api/alerts` (AlertInput del Manager). */
@Serializable
data class AlertDto(
    val id: String,
    val packageVersion: Int,
    val crewId: String,
    val kind: String,
    val level: String,
    val capacityId: String? = null,
    val entryId: String? = null,
    val met: Long,
    val message: String,
    val evidence: List<EvidenceDto>,
    val source: String = SOURCE_INTERFACE,
)

@Serializable
data class AckDto(val status: String, val note: String = "")

@Serializable
data class ServerAlert(val id: String, val ack: AckDto? = null)

@Serializable
private data class AlertsResponse(val alerts: List<ServerAlert> = emptyList())

/** Respuesta HTTP fuera de 2xx. */
class HttpException(val code: Int, body: String) : IOException("HTTP $code: $body")

/**
 * Cliente HTTP mínimo de AURA Manager, sin dependencias. Cualquier fallo de red lanza
 * [IOException]: quien lo llama deja la adaptación en cola y lo reintenta más tarde.
 */
class ManagerClient(private val baseUrl: String) {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun postAdaptations(list: List<AdaptationDto>): List<ServerAdaptation> {
        val body = json.encodeToString(ListSerializer(AdaptationDto.serializer()), list)
        return json.decodeFromString(AdaptationsResponse.serializer(), request("POST", "/api/adaptations", body)).adaptations
    }

    fun fetchAdaptations(crewId: String): List<ServerAdaptation> {
        val path = "/api/adaptations?crewId=" + URLEncoder.encode(crewId, "UTF-8")
        return json.decodeFromString(AdaptationsResponse.serializer(), request("GET", path, null)).adaptations
    }

    fun postAlerts(list: List<AlertDto>): List<ServerAlert> {
        val body = json.encodeToString(ListSerializer(AlertDto.serializer()), list)
        return json.decodeFromString(AlertsResponse.serializer(), request("POST", "/api/alerts", body)).alerts
    }

    fun fetchAlerts(crewId: String): List<ServerAlert> {
        val path = "/api/alerts?crewId=" + URLEncoder.encode(crewId, "UTF-8")
        return json.decodeFromString(AlertsResponse.serializer(), request("GET", path, null)).alerts
    }

    /**
     * Último paquete publicado, como texto (se guarda tal cual). Null si aún no hay ninguno (404).
     * Cualquier otro fallo lanza [IOException].
     */
    fun fetchPackage(version: Int? = null): String? = try {
        request("GET", "/api/package" + (version?.let { "?version=$it" } ?: ""), null)
    } catch (e: HttpException) {
        if (e.code == 404) null else throw e
    }

    fun toDto(a: LocalAlert) = AlertDto(
        id = a.id,
        packageVersion = a.packageVersion,
        crewId = a.crewId,
        kind = a.kind,
        level = a.level,
        capacityId = a.capacityId,
        entryId = a.entryId,
        met = a.met,
        message = a.message,
        evidence = evidenceFromJson(a.evidenceJson),
    )

    fun evidenceFromJson(text: String): List<EvidenceDto> =
        json.decodeFromString(ListSerializer(EvidenceDto.serializer()), text)

    fun evidenceToJson(list: List<EvidenceDto>): String =
        json.encodeToString(ListSerializer(EvidenceDto.serializer()), list)

    fun toDto(a: LocalAdaptation) = AdaptationDto(
        id = a.id,
        packageVersion = a.packageVersion,
        crewId = a.crewId,
        entryId = a.entryId,
        type = a.type,
        replacementItemId = a.replacementItemId,
        newMet = a.newMet,
        decidedAtMet = a.decidedAtMet,
        reason = a.reason,
        evidence = evidenceFromJson(a.evidenceJson),
    )

    private fun request(method: String, path: String, body: String?): String {
        val conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw HttpException(code, text.take(200))
            return text
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MS = 8_000
    }
}
