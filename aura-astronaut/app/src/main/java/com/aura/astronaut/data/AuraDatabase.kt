package com.aura.astronaut.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/*
 * Datos recogidos a bordo. Se quedan en el dispositivo: solo las adaptaciones (paso 5)
 * se envían a control de misión, con su evidencia.
 */

/** Origen de una observación, con los valores literales del contrato. */
enum class Origin(val wire: String) {
    MANUAL("manual"),
    SENSOR("sensor"),
    SIMULATED("simulated"),
}

enum class ConfirmationStatus { DONE, OMITTED }

/** Confirmación de una actividad del plan: hecha u omitida (siempre con motivo). */
@Entity(tableName = "activity_confirmations", primaryKeys = ["crewId", "entryId"])
data class ActivityConfirmation(
    val crewId: String,
    /** `plans[].activities[].entryId` del paquete. */
    val entryId: String,
    val itemId: String,
    val packageVersion: Int,
    val status: ConfirmationStatus,
    /** Obligatorio si se omite. */
    val reason: String?,
    val confirmedAtMet: Long,
    val recordedAtUtcMs: Long,
    val origin: Origin = Origin.MANUAL,
)

/**
 * Una medida: respuesta de cuestionario, señal, resultado de prueba…
 * Guarda valor, unidad, fecha, origen y calidad, como pide la especificación.
 */
@Entity(tableName = "observations")
data class Observation(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val crewId: String,
    /** Tipo de observación, p. ej. [KIND_QUESTIONNAIRE]. */
    val kind: String,
    /** Qué se mide: id de la pregunta o de la señal. */
    val indicator: String,
    /** Valor usado por las reglas. En el cuestionario, el normalizado (alto = peor). */
    val value: Double,
    /** Valor tal como se introdujo (respuesta original), si difiere. */
    val rawValue: Double?,
    val unit: String,
    val variantId: String?,
    /** Actividad del plan que la originó (el cuestionario `F` de ese día). */
    val entryId: String?,
    val capacityId: String?,
    val flightDay: Int?,
    val met: Long,
    val recordedAtUtcMs: Long,
    val origin: Origin,
    /** Calidad o naturaleza del dato, p. ej. «autoinforme». */
    val quality: String,
    val packageVersion: Int,
) {
    companion object {
        const val KIND_QUESTIONNAIRE = "cuestionario"
        const val KIND_SIGNAL = "senal"
    }
}

enum class SendStatus { PENDING, SENT }

/**
 * Adaptación decidida por la interfaz. Se aplica a bordo al guardarse y se envía a control
 * de misión en segundo plano; [id] se genera aquí, así que reenviarla no la duplica.
 */
@Entity(tableName = "adaptations")
data class LocalAdaptation(
    @PrimaryKey val id: String,
    val crewId: String,
    val entryId: String,
    val originalItemId: String,
    val packageVersion: Int,
    /** `sustitucion`, `reprogramacion` u `omision`. */
    val type: String,
    val replacementItemId: String?,
    val newMet: Long?,
    val decidedAtMet: Long,
    val reason: String,
    /** `evidence[]` tal como se envía, en JSON. */
    val evidenceJson: String,
    val rule: String,
    val createdAtUtcMs: Long,
    val sendStatus: SendStatus = SendStatus.PENDING,
    val sentAtUtcMs: Long? = null,
    /** Comprobación del servidor contra su política; null hasta recibirla. */
    val withinPolicy: Boolean? = null,
    val violations: String? = null,
    /** `pendiente`, `validada` u `objetada`. */
    val reviewStatus: String = ReviewStatus.PENDING,
    val reviewNote: String? = null,
)

/**
 * Alerta para control de misión: lo que la interfaz no resuelve adaptando (capacidad en rojo,
 * medida de preparación perdida, incoherencia autoinforme/sensor). Su [id] es un UUID derivado
 * de tripulante + [dedupeKey]: la misma situación nunca genera dos alertas, ni al reenviarla.
 */
@Entity(tableName = "alerts")
data class LocalAlert(
    @PrimaryKey val id: String,
    val crewId: String,
    val dedupeKey: String,
    /** `capacidad-roja`, `preparacion-perdida` o `incoherencia`. */
    val kind: String,
    /** `rojo` o `aviso`. */
    val level: String,
    val capacityId: String?,
    val entryId: String?,
    val met: Long,
    val flightDay: Int?,
    /** Lo que vio el astronauta. */
    val message: String,
    val evidenceJson: String,
    val packageVersion: Int,
    val createdAtUtcMs: Long,
    val sendStatus: SendStatus = SendStatus.PENDING,
    /** `nueva`, `vista` o `atendida`, según control de misión. */
    val ackStatus: String = AckStatus.NEW,
    val ackNote: String? = null,
)

class Converters {
    @TypeConverter fun originToWire(o: Origin): String = o.wire
    // "simulado" es el código v4, por si quedara alguna fila sin migrar.
    @TypeConverter fun wireToOrigin(s: String): Origin = if (s == "simulado") Origin.SIMULATED else Origin.entries.first { it.wire == s }
    @TypeConverter fun statusToText(s: ConfirmationStatus): String = s.name
    @TypeConverter fun textToStatus(s: String): ConfirmationStatus = ConfirmationStatus.valueOf(s)
    @TypeConverter fun sendToText(s: SendStatus): String = s.name
    @TypeConverter fun textToSend(s: String): SendStatus = SendStatus.valueOf(s)
}

@Dao
interface AuraDao {
    @Query("SELECT * FROM activity_confirmations WHERE crewId = :crewId")
    fun confirmations(crewId: String): Flow<List<ActivityConfirmation>>

    @Upsert
    suspend fun upsertConfirmation(c: ActivityConfirmation)

    @Query("DELETE FROM activity_confirmations WHERE crewId = :crewId AND entryId = :entryId")
    suspend fun deleteConfirmation(crewId: String, entryId: String)

    @Insert
    suspend fun insertObservations(list: List<Observation>)

    @Query("SELECT * FROM observations WHERE crewId = :crewId AND kind = :kind ORDER BY met, id")
    fun observations(crewId: String, kind: String): Flow<List<Observation>>

    @Query("SELECT * FROM observations WHERE crewId = :crewId ORDER BY met, id")
    fun allObservations(crewId: String): Flow<List<Observation>>

    @Query("DELETE FROM observations WHERE crewId = :crewId AND kind = :kind AND flightDay = :flightDay AND origin = 'simulated'")
    suspend fun deleteSimulated(crewId: String, kind: String, flightDay: Int)

    @Query("SELECT * FROM adaptations WHERE crewId = :crewId ORDER BY createdAtUtcMs")
    fun adaptations(crewId: String): Flow<List<LocalAdaptation>>

    @Query("SELECT * FROM adaptations WHERE crewId = :crewId")
    suspend fun adaptationsNow(crewId: String): List<LocalAdaptation>

    @Query("SELECT * FROM adaptations WHERE sendStatus = 'PENDING' ORDER BY createdAtUtcMs")
    suspend fun pendingAdaptations(): List<LocalAdaptation>

    @Query("SELECT * FROM adaptations WHERE crewId = :crewId AND entryId = :entryId LIMIT 1")
    suspend fun adaptationFor(crewId: String, entryId: String): LocalAdaptation?

    @Insert
    suspend fun insertAdaptation(a: LocalAdaptation)

    @Query("UPDATE adaptations SET sendStatus = 'SENT', sentAtUtcMs = :at, withinPolicy = :within, violations = :violations WHERE id = :id")
    suspend fun markSent(id: String, at: Long, within: Boolean?, violations: String?)

    @Query("UPDATE adaptations SET reviewStatus = :status, reviewNote = :note, withinPolicy = :within, violations = :violations WHERE id = :id")
    suspend fun updateReview(id: String, status: String, note: String?, within: Boolean?, violations: String?)

    /** Si ya existe (mismo id), no hace nada: una situación, una alerta. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertAlert(a: LocalAlert)

    @Query("SELECT dedupeKey FROM alerts WHERE crewId = :crewId")
    suspend fun alertKeys(crewId: String): List<String>

    @Query("SELECT * FROM alerts WHERE crewId = :crewId ORDER BY createdAtUtcMs")
    fun alerts(crewId: String): Flow<List<LocalAlert>>

    @Query("SELECT * FROM alerts WHERE sendStatus = 'PENDING' ORDER BY createdAtUtcMs")
    suspend fun pendingAlerts(): List<LocalAlert>

    @Query("UPDATE alerts SET sendStatus = 'SENT' WHERE id = :id")
    suspend fun markAlertSent(id: String)

    @Query("UPDATE alerts SET ackStatus = :status, ackNote = :note WHERE id = :id")
    suspend fun updateAck(id: String, status: String, note: String?)
}

@Database(
    entities = [ActivityConfirmation::class, Observation::class, LocalAdaptation::class, LocalAlert::class],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun dao(): AuraDao

    companion object {
        @Volatile private var instance: AuraDatabase? = null

        /** v2 añade las adaptaciones (paso 5) sin tocar confirmaciones ni observaciones. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `adaptations` (`id` TEXT NOT NULL, `crewId` TEXT NOT NULL, " +
                        "`entryId` TEXT NOT NULL, `originalItemId` TEXT NOT NULL, `packageVersion` INTEGER NOT NULL, " +
                        "`type` TEXT NOT NULL, `replacementItemId` TEXT, `newMet` INTEGER, `decidedAtMet` INTEGER NOT NULL, " +
                        "`reason` TEXT NOT NULL, `evidenceJson` TEXT NOT NULL, `rule` TEXT NOT NULL, " +
                        "`createdAtUtcMs` INTEGER NOT NULL, `sendStatus` TEXT NOT NULL, `sentAtUtcMs` INTEGER, " +
                        "`withinPolicy` INTEGER, `violations` TEXT, `reviewStatus` TEXT NOT NULL, `reviewNote` TEXT, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }

        /** v3 añade las alertas para control de misión. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `alerts` (`id` TEXT NOT NULL, `crewId` TEXT NOT NULL, " +
                        "`dedupeKey` TEXT NOT NULL, `kind` TEXT NOT NULL, `level` TEXT NOT NULL, `capacityId` TEXT, " +
                        "`entryId` TEXT, `met` INTEGER NOT NULL, `flightDay` INTEGER, `message` TEXT NOT NULL, " +
                        "`evidenceJson` TEXT NOT NULL, `packageVersion` INTEGER NOT NULL, `createdAtUtcMs` INTEGER NOT NULL, " +
                        "`sendStatus` TEXT NOT NULL, `ackStatus` TEXT NOT NULL, `ackNote` TEXT, PRIMARY KEY(`id`))",
                )
            }
        }

        /** Equivalencias v4 → v5 de lo que la app guarda (contract/v4-to-v5.md). */
        private val V5_CODES = mapOf(
            "type" to mapOf("sustitucion" to "substitution", "reprogramacion" to "reschedule", "omision" to "omission"),
            "reviewStatus" to mapOf("pendiente" to "pending", "validada" to "validated", "objetada" to "objected"),
            "kind" to mapOf("capacidad-roja" to "capacity-red", "preparacion-perdida" to "preparation-lost", "incoherencia" to "inconsistency"),
            "level" to mapOf("rojo" to "red", "aviso" to "warning"),
            "ackStatus" to mapOf("nueva" to "new", "vista" to "seen", "atendida" to "handled"),
            "origin" to mapOf("simulado" to "simulated"),
        )
        private val V5_IDS = mapOf(
            "fatiga" to "fatigue", "musculoesqueletico" to "musculoskeletal", "coordinacion" to "coordination",
            "fc_reposo_delta" to "resting_hr_delta", "recuperacion_fc_lenta" to "slow_hr_recovery",
            "hrv_nocturna_delta" to "night_hrv_delta", "fr_delta" to "resp_rate_delta", "arritmia_ecg" to "ecg_arrhythmia",
            "fatiga_emg" to "emg_fatigue", "coordinacion_delta" to "coordination_delta", "Q1_FATIGA" to "Q1_FATIGUE",
        )

        /** v4: códigos e identificadores del contrato v5 (en inglés). No cambia el esquema de tablas. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                fun recode(table: String, column: String, map: Map<String, String>) =
                    map.forEach { (old, new) -> db.execSQL("UPDATE `$table` SET `$column` = ? WHERE `$column` = ?", arrayOf(new, old)) }
                recode("adaptations", "type", V5_CODES.getValue("type"))
                recode("adaptations", "reviewStatus", V5_CODES.getValue("reviewStatus"))
                recode("alerts", "kind", V5_CODES.getValue("kind"))
                recode("alerts", "level", V5_CODES.getValue("level"))
                recode("alerts", "ackStatus", V5_CODES.getValue("ackStatus"))
                recode("observations", "origin", V5_CODES.getValue("origin"))
                recode("activity_confirmations", "origin", V5_CODES.getValue("origin"))
                recode("observations", "indicator", V5_IDS)
                recode("observations", "capacityId", V5_IDS)
                recode("alerts", "capacityId", V5_IDS)
                for (table in listOf("adaptations", "alerts")) {
                    db.execSQL("UPDATE `$table` SET evidenceJson = REPLACE(evidenceJson, '\"simulado\"', '\"simulated\"')")
                }
                // La clave de deduplicación de las alertas cita la capacidad: «rojo:9:fatiga» → «rojo:9:fatigue».
                V5_IDS.forEach { (old, new) ->
                    db.execSQL("UPDATE `alerts` SET dedupeKey = REPLACE(dedupeKey, ?, ?)", arrayOf(":$old", ":$new"))
                }
            }
        }

        fun get(context: Context): AuraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AuraDatabase::class.java, "aura.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
                .also { instance = it }
        }
    }
}
