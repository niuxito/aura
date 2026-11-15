package com.aura.astronaut.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import androidx.core.content.edit
import com.aura.astronaut.hud.Announcement
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Estado de la voz, para que el HUD pueda explicarlo. */
enum class VoiceStatus {
    STARTING,
    READY,
    /** El motor no tiene una voz del idioma activo que funcione sin conexión: la app sigue solo con el HUD. */
    NO_OFFLINE_VOICE,
    /** No hay motor de texto a voz en el dispositivo. */
    UNAVAILABLE,
}

/**
 * Avisos por voz con el `TextToSpeech` del sistema, en el idioma activo de la interfaz y sin conexión.
 * - Cada anuncio se dice una sola vez por sesión (por su id).
 * - Un anuncio solo interrumpe a otro de prioridad igual o menor.
 * - Silenciado, los anuncios se dan por dichos: al reactivar la voz no se repiten los antiguos.
 */
class Speaker(context: Context, initialLanguage: String) : TextToSpeech.OnInitListener {

    /** Idioma de la voz: el de la interfaz. */
    @Volatile private var language = initialLanguage
    @Volatile private var engineReady = false

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("voice", Context.MODE_PRIVATE)
    private val audio = appContext.getSystemService(AudioManager::class.java)
    private val tts = TextToSpeech(appContext, this)

    private val _status = MutableStateFlow(VoiceStatus.STARTING)
    val status: StateFlow<VoiceStatus> = _status.asStateFlow()

    private val _muted = MutableStateFlow(prefs.getBoolean(KEY_MUTED, false))
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    /** Avisos ya dichos (o silenciados). Se guardan para no repetirlos al reabrir la app. */
    private val spoken = LinkedHashSet(prefs.getString(KEY_SPOKEN, null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty())
    private val pending = mutableListOf<Announcement>()
    @Volatile private var speakingPriority: Int? = null

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .build()

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            Log.w(TAG, "Motor de voz no disponible ($result)")
            _status.value = VoiceStatus.UNAVAILABLE
            return
        }
        engineReady = true
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) {
                Log.i(TAG, "Diciendo: $utteranceId")
            }

            override fun onDone(utteranceId: String) {
                Log.i(TAG, "Dicho: $utteranceId")
                finished()
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finished()
            override fun onError(utteranceId: String, errorCode: Int) = finished()
        })
        if (!chooseVoice()) return
        synchronized(pending) {
            pending.sortedByDescending { it.priority }.forEach { speakNow(it) }
            pending.clear()
        }
    }

    /** Cambia el idioma de la voz (al cambiar el de la interfaz). */
    fun setLanguage(lang: String) {
        if (lang == language) return
        language = lang
        if (engineReady) {
            tts.stop()
            chooseVoice()
        }
    }

    /** Elige una voz instalada del idioma activo que no necesite red; sin ella, la app sigue solo con el HUD. */
    private fun chooseVoice(): Boolean {
        val voice = offlineVoice(language)
        if (voice == null) {
            val same = runCatching { tts.voices }.getOrNull().orEmpty().filter { it.locale.language == language }
            Log.w(
                TAG,
                "Sin voz sin conexión para «$language». Voces de ese idioma: " + same.joinToString { v ->
                    "${v.name}(red=${v.isNetworkConnectionRequired}, " +
                        "instalada=${TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features})"
                }.ifEmpty { "ninguna" },
            )
            _status.value = VoiceStatus.NO_OFFLINE_VOICE
            return false
        }
        tts.voice = voice
        Log.i(TAG, "Voz lista: ${voice.name} (${voice.locale})")
        _status.value = VoiceStatus.READY
        return true
    }

    /** Anuncia lo que haya de nuevo en [announcements]; lo ya dicho se ignora. */
    fun announce(announcements: List<Announcement>) {
        announcements.sortedByDescending { it.priority }.forEach { a ->
            if (!spoken.add(a.id)) return@forEach
            rememberSpoken()
            if (_muted.value) return@forEach
            when (_status.value) {
                VoiceStatus.READY -> speakNow(a)
                VoiceStatus.STARTING -> synchronized(pending) { pending += a }
                else -> Unit
            }
        }
    }

    private fun rememberSpoken() {
        // Solo los últimos: basta para no repetir y no crece sin límite.
        while (spoken.size > MAX_REMEMBERED) spoken.remove(spoken.first())
        prefs.edit { putString(KEY_SPOKEN, spoken.joinToString("\n")) }
    }

    fun forgetSpoken() {
        spoken.clear()
        prefs.edit { remove(KEY_SPOKEN) }
    }

    fun setMuted(muted: Boolean) {
        _muted.value = muted
        prefs.edit { putBoolean(KEY_MUTED, muted) }
        if (muted) {
            tts.stop()
            finished()
        }
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
        audio.abandonAudioFocusRequest(focusRequest)
    }

    private fun speakNow(a: Announcement) {
        val current = speakingPriority
        // Solo se interrumpe un mensaje de prioridad igual o menor; si no, el nuevo espera su turno.
        val mode = if (current == null || a.priority >= current) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        if (mode == TextToSpeech.QUEUE_FLUSH) speakingPriority = a.priority
        audio.requestAudioFocus(focusRequest)
        tts.speak(a.text, mode, null, a.id)
    }

    private fun finished() {
        if (!tts.isSpeaking) {
            speakingPriority = null
            audio.abandonAudioFocusRequest(focusRequest)
        }
    }

    /** Voz instalada del idioma que no necesita red. Prefiere la variante habitual (España, EE. UU.) y luego cualquiera. */
    private fun offlineVoice(lang: String): Voice? {
        val voices = runCatching { tts.voices }.getOrNull().orEmpty().filter { v ->
            v.locale.language == lang &&
                !v.isNetworkConnectionRequired &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features
        }
        return voices.firstOrNull { it.locale.country == PREFERRED_COUNTRY[lang] } ?: voices.firstOrNull()
    }

    private companion object {
        const val TAG = "AuraVoice"
        const val KEY_MUTED = "muted"
        const val KEY_SPOKEN = "spoken_ids"
        const val MAX_REMEMBERED = 300
        val PREFERRED_COUNTRY = mapOf("es" to "ES", "en" to "US")
    }
}
