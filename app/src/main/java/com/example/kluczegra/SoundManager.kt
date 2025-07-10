package com.example.kluczegra

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log

class SoundManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    companion object {
        private const val TAG = "SoundManager"
    }

    /**
     * Sprawdza czy urządzenie nie jest w trybie cichym
     */
    private fun canPlaySound(): Boolean {
        return when (audioManager.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> false
            AudioManager.RINGER_MODE_VIBRATE -> false
            AudioManager.RINGER_MODE_NORMAL -> true
            else -> true
        }
    }

    /**
     * Odtwarza dźwięk ostrzeżenia przy wyjściu z domu
     */
    fun playExitAlert() {
        if (!canPlaySound()) {
            Log.d(TAG, "Urządzenie w trybie cichym - pomijam dźwięk ostrzeżenia")
            return
        }

        playSound(R.raw.alert_exit, "exit alert")
    }

    /**
     * Odtwarza łagodny dźwięk przypomnienia przy powrocie do domu
     */
    fun playSoftReminder() {
        if (!canPlaySound()) {
            Log.d(TAG, "Urządzenie w trybie cichym - pomijam dźwięk przypomnienia")
            return
        }

        playSound(R.raw.soft_reminder, "soft reminder")
    }

    /**
     * Odtwarza określony dźwięk z zasobów
     */
    private fun playSound(resourceId: Int, soundName: String) {
        try {
            // Zatrzymaj poprzedni dźwięk jeśli jest odtwarzany
            stopSound()

            mediaPlayer = MediaPlayer.create(context, resourceId)
            mediaPlayer?.let { player ->
                player.setOnCompletionListener {
                    Log.d(TAG, "Zakończono odtwarzanie: $soundName")
                    stopSound()
                }

                player.setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "Błąd odtwarzania $soundName: what=$what, extra=$extra")
                    stopSound()
                    false
                }

                player.start()
                Log.d(TAG, "Rozpoczęto odtwarzanie: $soundName")
            } ?: run {
                Log.e(TAG, "Nie udało się utworzyć MediaPlayer dla: $soundName")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas odtwarzania $soundName", e)
            stopSound()
        }
    }

    /**
     * Zatrzymuje aktualnie odtwarzany dźwięk
     */
    fun stopSound() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Błąd podczas zatrzymywania dźwięku", e)
        } finally {
            mediaPlayer = null
        }
    }

    /**
     * Zwalnia zasoby - wywołaj w onDestroy
     */
    fun release() {
        stopSound()
    }
}
