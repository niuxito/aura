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

/** Voice state, so the HUD can explain it. */
enum class VoiceStatus {
    STARTING,
    READY,
    /** The engine has no offline voice for the active language: the app continues with the HUD only. */
    NO_OFFLINE_VOICE,
    /** There is no text-to-speech engine on the device. */
    UNAVAILABLE,
}

/**
 * Voice notices with the system `TextToSpeech`, in the interface's active language and offline.
 * - Each announcement is spoken only once per session (by its id).
 * - An announcement only interrupts another of equal or lower priority.
 * - When muted, announcements are marked as said: re-enabling the voice does not repeat old ones.
 */
class Speaker(context: Context, initialLanguage: String) : TextToSpeech.OnInitListener {

    /** Voice language: the interface's. */
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

    /** Notices already said (or muted). Stored so they are not repeated when reopening the app. */
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
            Log.w(TAG, "Voice engine not available ($result)")
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

    /** Changes the voice language (when the interface language changes). */
    fun setLanguage(lang: String) {
        if (lang == language) return
        language = lang
        if (engineReady) {
            tts.stop()
            chooseVoice()
        }
    }

    /** Picks an installed voice for the active language that needs no network; without one, the app continues with the HUD only. */
    private fun chooseVoice(): Boolean {
        val voice = offlineVoice(language)
        if (voice == null) {
            val same = runCatching { tts.voices }.getOrNull().orEmpty().filter { it.locale.language == language }
            Log.w(
                TAG,
                "No offline voice for \"$language\". Voices for that language: " + same.joinToString { v ->
                    "${v.name}(network=${v.isNetworkConnectionRequired}, " +
                        "installed=${TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in v.features})"
                }.ifEmpty { "none" },
            )
            _status.value = VoiceStatus.NO_OFFLINE_VOICE
            return false
        }
        tts.voice = voice
        Log.i(TAG, "Voz lista: ${voice.name} (${voice.locale})")
        _status.value = VoiceStatus.READY
        return true
    }

    /** Announces whatever is new in [announcements]; what was already said is ignored. */
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
        // Only the latest ones: enough to avoid repeats and it does not grow without limit.
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
        // A message is only interrupted by one of equal or lower priority; otherwise the new one waits its turn.
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

    /** Installed voice for the language that needs no network. Prefers the usual variant (Spain, US) and then any. */
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
