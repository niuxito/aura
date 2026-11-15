package com.aura.astronaut.sync

import android.util.Log
import com.aura.astronaut.data.AckStatus
import com.aura.astronaut.data.AuraDao
import com.aura.astronaut.data.ReviewStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException

/** Último resultado de la conexión con control de misión, para explicarlo en el HUD. */
data class SyncStatus(
    val url: String,
    val lastOkUtcMs: Long? = null,
    val lastError: String? = null,
)

/**
 * Cola de envío a control de misión: adaptaciones y alertas pendientes, y lectura de su
 * revisión (adaptaciones) y acuse (alertas).
 * Nunca bloquea: la adaptación ya está aplicada a bordo. Sin conexión, falla en silencio y
 * se reintenta en la siguiente vuelta; reenviar el mismo id no duplica el registro.
 */
class AdaptationSync(private val dao: AuraDao, initialUrl: String) {

    private val _status = MutableStateFlow(SyncStatus(initialUrl))
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    fun setUrl(url: String) {
        _status.value = SyncStatus(url)
    }

    suspend fun runOnce(crewId: String?) = withContext(Dispatchers.IO) {
        val url = _status.value.url
        val client = ManagerClient(url)
        // Adaptaciones y alertas se sincronizan por separado: si un servidor aún no tiene
        // /api/alerts (despliegue antiguo), las adaptaciones siguen llegando.
        val adaptationsError = attempt(url) { syncAdaptations(client, url, crewId) }
        val alertsError = attempt(url) { syncAlerts(client, url, crewId) }
        _status.value = when {
            adaptationsError != null -> _status.value.copy(lastError = adaptationsError)
            alertsError != null -> SyncStatus(url, lastOkUtcMs = System.currentTimeMillis(), lastError = "alertas: $alertsError")
            else -> SyncStatus(url, lastOkUtcMs = System.currentTimeMillis())
        }
    }

    private suspend fun syncAdaptations(client: ManagerClient, url: String, crewId: String?) {
        val pending = dao.pendingAdaptations()
        if (pending.isNotEmpty()) {
            val registered = client.postAdaptations(pending.map(client::toDto)).associateBy { it.id }
            val now = System.currentTimeMillis()
            pending.forEach { a ->
                val server = registered[a.id]
                dao.markSent(a.id, now, server?.policy?.withinPolicy, server?.policy?.violations?.joinToString("\n")?.ifBlank { null })
            }
            Log.i(TAG, "Enviadas ${pending.size} adaptaciones a $url")
        }
        if (crewId != null) {
            client.fetchAdaptations(crewId).forEach { s ->
                dao.updateReview(
                    s.id,
                    ServerCodes.review(s.review?.status),
                    s.review?.note?.ifBlank { null },
                    s.policy?.withinPolicy,
                    s.policy?.violations?.joinToString("\n")?.ifBlank { null },
                )
            }
        }
    }

    private suspend fun syncAlerts(client: ManagerClient, url: String, crewId: String?) {
        val pending = dao.pendingAlerts()
        if (pending.isNotEmpty()) {
            client.postAlerts(pending.map(client::toDto))
            pending.forEach { dao.markAlertSent(it.id) }
            Log.i(TAG, "Enviadas ${pending.size} alertas a $url")
        }
        if (crewId != null) {
            client.fetchAlerts(crewId).forEach { s ->
                dao.updateAck(s.id, ServerCodes.ack(s.ack?.status), s.ack?.note?.ifBlank { null })
            }
        }
    }

    /** null si ha ido bien; si no, el motivo (sin conexión, respuesta inesperada…). */
    private suspend fun attempt(url: String, block: suspend () -> Unit): String? = try {
        block()
        null
    } catch (e: IOException) {
        e.message ?: "sin conexión"
    } catch (e: SerializationException) {
        Log.w(TAG, "Respuesta inesperada de $url", e)
        "respuesta inesperada del servidor"
    }

    private companion object {
        const val TAG = "AuraSync"
        // ServerCodes: ver más abajo.
    }
}

/** Estados que devuelve el Manager, en códigos v5. Un servidor aún en v4 responde en español: se aceptan ambos. */
object ServerCodes {
    fun review(status: String?): String = when (status) {
        ReviewStatus.VALIDATED, "validada" -> ReviewStatus.VALIDATED
        ReviewStatus.OBJECTED, "objetada" -> ReviewStatus.OBJECTED
        else -> ReviewStatus.PENDING
    }

    fun ack(status: String?): String = when (status) {
        AckStatus.SEEN, "vista" -> AckStatus.SEEN
        AckStatus.HANDLED, "atendida" -> AckStatus.HANDLED
        else -> AckStatus.NEW
    }
}

