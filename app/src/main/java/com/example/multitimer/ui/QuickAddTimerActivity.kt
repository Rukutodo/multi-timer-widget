package com.example.multitimer.ui

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.multitimer.R
import com.example.multitimer.data.TimerModel
import com.example.multitimer.data.TimerRepository
import com.example.multitimer.databinding.ActivityQuickAddTimerBinding
import java.util.UUID

/**
 * Lightweight dialog activity launched directly when tapping [+] on the Home Screen Widget.
 * Allows the user to enter ANY custom duration (or presets) and pick YouTube Music / Default
 * right from the home screen without navigating into the full app.
 */
class QuickAddTimerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQuickAddTimerBinding
    private lateinit var repository: TimerRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQuickAddTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TimerRepository.getInstance(this)

        setupDurationPresets()
        setupAudioChoice()
        setupActionButtons()
        checkClipboardForYouTubeLink()
    }

    override fun onResume() {
        super.onResume()
        checkClipboardForYouTubeLink()
    }

    private fun setupDurationPresets() {
        binding.chipQ1m.setOnClickListener { setDuration(0, 1, 0) }
        binding.chipQ5m.setOnClickListener { setDuration(0, 5, 0) }
        binding.chipQ10m.setOnClickListener { setDuration(0, 10, 0) }
        binding.chipQ15m.setOnClickListener { setDuration(0, 15, 0) }
        binding.chipQ25m.setOnClickListener { setDuration(0, 25, 0) }
        binding.chipQ30m.setOnClickListener { setDuration(0, 30, 0) }
        binding.chipQ45m.setOnClickListener { setDuration(0, 45, 0) }
        binding.chipQ60m.setOnClickListener { setDuration(1, 0, 0) }
    }

    private fun setDuration(h: Long, m: Long, s: Long) {
        binding.quickHours.setText(h.toString())
        binding.quickMinutes.setText(m.toString())
        binding.quickSeconds.setText(s.toString())
    }

    private fun setupAudioChoice() {
        binding.quickAudioDefault.setOnClickListener {
            binding.quickAudioDefault.isChecked = true
            binding.quickAudioYt.isChecked = false
            binding.quickYtContainer.visibility = View.GONE
        }

        binding.quickAudioYt.setOnClickListener {
            binding.quickAudioYt.isChecked = true
            binding.quickAudioDefault.isChecked = false
            binding.quickYtContainer.visibility = View.VISIBLE
        }

        binding.quickBtnOpenYt.setOnClickListener {
            openYouTubeMusicPlaylists()
        }
    }

    private fun openYouTubeMusicPlaylists() {
        Toast.makeText(
            this,
            "Opening YouTube Music! Tap 'Share' -> 'Copy link' on any song/playlist, then return here.",
            Toast.LENGTH_LONG
        ).show()

        val ytIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("https://music.youtube.com/library/playlists")
            setPackage("com.google.android.apps.youtube.music")
        }

        try {
            startActivity(ytIntent)
        } catch (e: Exception) {
            // Fallback to browser or regular YouTube
            val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://music.youtube.com/library/playlists"))
            startActivity(fallback)
        }
    }

    private fun checkClipboardForYouTubeLink() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount > 0) {
            val text = clip.getItemAt(0)?.text?.toString()?.trim() ?: return
            if (text.contains("music.youtube.com") || text.contains("youtu.be") || text.contains("youtube.com")) {
                if (binding.quickAudioYt.isChecked) {
                    binding.quickYtQuery.setText(text)
                    Toast.makeText(this, "📋 Detected copied YouTube Music link!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupActionButtons() {
        binding.btnCloseDialog.setOnClickListener { finish() }
        binding.quickBtnCancel.setOnClickListener { finish() }

        binding.quickBtnCreate.setOnClickListener {
            val hours = binding.quickHours.text?.toString()?.toLongOrNull() ?: 0L
            val minutes = binding.quickMinutes.text?.toString()?.toLongOrNull() ?: 0L
            val seconds = binding.quickSeconds.text?.toString()?.toLongOrNull() ?: 0L

            val totalMs = (hours * 3600 + minutes * 60 + seconds) * 1000L

            if (totalMs <= 0) {
                Toast.makeText(this, "Duration must be greater than 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val rawName = binding.quickTimerName.text?.toString()?.trim()
            val finalName = if (!rawName.isNullOrEmpty()) {
                rawName
            } else {
                val formatted = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
                "Timer $formatted"
            }

            val isYouTube = binding.quickAudioYt.isChecked
            val ytQuery = if (isYouTube) {
                val query = binding.quickYtQuery.text?.toString()?.trim()
                if (query.isNullOrEmpty()) "Lo-Fi Beats" else query
            } else null

            val finalSoundName = if (isYouTube) {
                ytQuery ?: "YouTube Music"
            } else {
                "Default Alarm"
            }

            val newTimer = TimerModel(
                id = UUID.randomUUID().toString(),
                name = finalName,
                totalDurationMs = totalMs,
                remainingMs = totalMs,
                isYouTubeMusic = isYouTube,
                youTubeQuery = ytQuery,
                soundName = finalSoundName
            )

            repository.saveTimer(newTimer)
            repository.notifyWidgets()

            val soundMsg = if (isYouTube) "▶️ YT Music" else "🔔 Default"
            Toast.makeText(this, "Added '$finalName' ($soundMsg) to widget!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
