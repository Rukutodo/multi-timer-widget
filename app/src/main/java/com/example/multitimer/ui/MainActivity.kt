package com.example.multitimer.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.multitimer.R
import com.example.multitimer.data.BatteryStrategy
import com.example.multitimer.data.TimerModel
import com.example.multitimer.data.TimerRepository
import com.example.multitimer.data.TimerState
import com.example.multitimer.databinding.ActivityMainBinding
import com.example.multitimer.databinding.DialogAddTimerBinding
import com.example.multitimer.util.WidgetDiagnostics
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: TimerRepository
    private lateinit var adapter: TimerAdapter

    private var pendingSoundCallback: ((Uri?, String) -> Unit)? = null

    private val soundPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK && result.data != null) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            } ?: result.data?.data

            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Non-persistable content URIs are fine
                }

                val ringtone = RingtoneManager.getRingtone(this, uri)
                val title = ringtone?.getTitle(this) ?: "Custom Music"
                pendingSoundCallback?.invoke(uri, title)
            } else {
                pendingSoundCallback?.invoke(null, "Default Alarm")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TimerRepository.getInstance(this)
        requestPermissionsIfNeeded()
        setupStrategyControls()
        setupRecyclerView()
        setupFab()
        setupDiagnostics()
        startUiUpdateLoop()
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun setupRecyclerView() {
        adapter = TimerAdapter(
            timers = repository.getAllTimers(),
            onPlayPause = { timer ->
                if (timer.state == TimerState.RUNNING) {
                    repository.pauseTimer(timer.id)
                } else {
                    repository.startTimer(timer.id)
                }
                refreshList()
            },
            onReset = { timer ->
                repository.resetTimer(timer.id)
                refreshList()
            },
            onDelete = { timer ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Timer")
                    .setMessage("Are you sure you want to delete '${timer.name}'?")
                    .setPositiveButton("Delete") { _, _ ->
                        repository.deleteTimer(timer.id)
                        refreshList()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        binding.recyclerTimers.layoutManager = LinearLayoutManager(this)
        binding.recyclerTimers.adapter = adapter
    }

    private fun setupStrategyControls() {
        val current = repository.getStrategy()
        updateStrategyUi(current)

        binding.toggleGroupStrategy.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val selected = if (checkedId == R.id.btn_mode_chronometer) {
                    BatteryStrategy.NATIVE_CHRONOMETER
                } else {
                    BatteryStrategy.HIGH_FIDELITY_LOOP
                }
                repository.setStrategy(selected)
                updateStrategyUi(selected)
            }
        }
    }

    private fun updateStrategyUi(strategy: BatteryStrategy) {
        if (strategy == BatteryStrategy.NATIVE_CHRONOMETER) {
            binding.toggleGroupStrategy.check(R.id.btn_mode_chronometer)
            binding.strategyDescription.text =
                "⚡ Native Chronometer: Uses Android SystemUI to render the ticking countdown at 0% extra battery drain."
        } else {
            binding.toggleGroupStrategy.check(R.id.btn_mode_loop)
            binding.strategyDescription.text =
                "✨ High-Fi Loop: Active foreground loop updating home screen with progress bar percentages and dynamic text."
        }
    }

    private fun setupFab() {
        binding.fabAddTimer.setOnClickListener {
            showAddTimerDialog()
        }
    }

    private fun setupDiagnostics() {
        binding.btnDiagnostics.setOnClickListener {
            showDiagnosticsDialog()
        }
    }

    private fun showDiagnosticsDialog() {
        val report = WidgetDiagnostics.generateFormattedReport(this)
        MaterialAlertDialogBuilder(this)
            .setTitle("🛠️ Widget Diagnostics & Status")
            .setMessage(report)
            .setPositiveButton("📋 Copy Report") { _, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Widget Diagnostics", report)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied diagnostics report to clipboard!", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("🔄 Force Refresh") { _, _ ->
                repository.notifyWidgets()
                Toast.makeText(this, "Broadcasted reload to all home screen widgets!", Toast.LENGTH_SHORT).show()
                showDiagnosticsDialog()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showAddTimerDialog() {
        val dialogBinding = DialogAddTimerBinding.inflate(LayoutInflater.from(this))

        var chosenUri: Uri? = null
        var chosenName = "Default Alarm"

        dialogBinding.chip1m.setOnClickListener {
            dialogBinding.editHours.setText("0")
            dialogBinding.editMinutes.setText("1")
            dialogBinding.editSeconds.setText("0")
            if (dialogBinding.editTimerName.text.isNullOrEmpty()) dialogBinding.editTimerName.setText("Quick 1m")
        }
        dialogBinding.chip5m.setOnClickListener {
            dialogBinding.editHours.setText("0")
            dialogBinding.editMinutes.setText("5")
            dialogBinding.editSeconds.setText("0")
            if (dialogBinding.editTimerName.text.isNullOrEmpty()) dialogBinding.editTimerName.setText("5 Min Break")
        }
        dialogBinding.chip15m.setOnClickListener {
            dialogBinding.editHours.setText("0")
            dialogBinding.editMinutes.setText("15")
            dialogBinding.editSeconds.setText("0")
            if (dialogBinding.editTimerName.text.isNullOrEmpty()) dialogBinding.editTimerName.setText("Short Sprint")
        }
        dialogBinding.chip25m.setOnClickListener {
            dialogBinding.editHours.setText("0")
            dialogBinding.editMinutes.setText("25")
            dialogBinding.editSeconds.setText("0")
            if (dialogBinding.editTimerName.text.isNullOrEmpty()) dialogBinding.editTimerName.setText("Focus Session")
        }
        dialogBinding.chip45m.setOnClickListener {
            dialogBinding.editHours.setText("0")
            dialogBinding.editMinutes.setText("45")
            dialogBinding.editSeconds.setText("0")
            if (dialogBinding.editTimerName.text.isNullOrEmpty()) dialogBinding.editTimerName.setText("Deep Work")
        }
        dialogBinding.chip60m.setOnClickListener {
            dialogBinding.editHours.setText("1")
            dialogBinding.editMinutes.setText("0")
            dialogBinding.editSeconds.setText("0")
            if (dialogBinding.editTimerName.text.isNullOrEmpty()) dialogBinding.editTimerName.setText("1 Hour Block")
        }

        // Audio Source Switch: System Sound vs YouTube Music
        dialogBinding.chipTypeSystem.setOnClickListener {
            dialogBinding.chipTypeSystem.isChecked = true
            dialogBinding.chipTypeYoutube.isChecked = false
            dialogBinding.cardSelectSound.visibility = View.VISIBLE
            dialogBinding.layoutYoutubeMusic.visibility = View.GONE
        }
        dialogBinding.chipTypeYoutube.setOnClickListener {
            dialogBinding.chipTypeYoutube.isChecked = true
            dialogBinding.chipTypeSystem.isChecked = false
            dialogBinding.cardSelectSound.visibility = View.GONE
            dialogBinding.layoutYoutubeMusic.visibility = View.VISIBLE
        }

        // YouTube Preset Chips
        dialogBinding.chipYtLofi.setOnClickListener {
            dialogBinding.editYoutubeQuery.setText("Lo-Fi Beats to Relax/Study to")
        }
        dialogBinding.chipYtHype.setOnClickListener {
            dialogBinding.editYoutubeQuery.setText("Workout Hype Music Mix")
        }
        dialogBinding.chipYtAmbient.setOnClickListener {
            dialogBinding.editYoutubeQuery.setText("Morning Ambient Acoustic Chill")
        }
        dialogBinding.chipYtSynth.setOnClickListener {
            dialogBinding.editYoutubeQuery.setText("Synthwave Focus Cyberpunk")
        }

        // Custom Sound Picker action
        val openSoundPicker = {
            pendingSoundCallback = { uri, name ->
                chosenUri = uri
                chosenName = name
                dialogBinding.textSelectedSound.text = name
            }
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Timer Sound or Music")
            }
            soundPickerLauncher.launch(intent)
        }

        dialogBinding.btnChooseSound.setOnClickListener { openSoundPicker() }
        dialogBinding.cardSelectSound.setOnClickListener { openSoundPicker() }

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Create") { _, _ ->
                val name = dialogBinding.editTimerName.text?.toString()?.trim()
                val finalName = if (!name.isNullOrEmpty()) name else "Custom Timer"

                val hours = dialogBinding.editHours.text?.toString()?.toLongOrNull() ?: 0L
                val minutes = dialogBinding.editMinutes.text?.toString()?.toLongOrNull() ?: 0L
                val seconds = dialogBinding.editSeconds.text?.toString()?.toLongOrNull() ?: 0L

                val totalMs = (hours * 3600 + minutes * 60 + seconds) * 1000L

                if (totalMs <= 0) {
                    Toast.makeText(this, "Timer duration must be greater than 0", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                val isYouTube = dialogBinding.chipTypeYoutube.isChecked
                val ytQuery = if (isYouTube) {
                    val raw = dialogBinding.editYoutubeQuery.text?.toString()?.trim()
                    if (raw.isNullOrEmpty()) "Lo-Fi Beats" else raw
                } else null

                val finalSoundName = if (isYouTube) {
                    ytQuery ?: "YouTube Music"
                } else {
                    chosenName
                }

                val newTimer = TimerModel(
                    id = UUID.randomUUID().toString(),
                    name = finalName,
                    totalDurationMs = totalMs,
                    remainingMs = totalMs,
                    soundUri = if (!isYouTube) chosenUri?.toString() else null,
                    soundName = finalSoundName,
                    isYouTubeMusic = isYouTube,
                    youTubeQuery = ytQuery
                )

                repository.saveTimer(newTimer)
                refreshList()
                val soundMsg = if (isYouTube) "▶️ YouTube Music: $ytQuery" else "🎵 $chosenName"
                Toast.makeText(this, "Created '$finalName' ($soundMsg)", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshList() {
        val allTimers = repository.getAllTimers()
        adapter.updateData(allTimers)

        if (allTimers.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerTimers.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerTimers.visibility = View.VISIBLE
        }

        val activeCount = allTimers.size
        val runningCount = allTimers.count { it.state == TimerState.RUNNING }
        binding.textStatActive.text = "⚡ $activeCount Active"
        binding.textStatRunning.text = "● $runningCount Running"
        val strat = repository.getStrategy()
        binding.textStatEngine.text = if (strat == BatteryStrategy.NATIVE_CHRONOMETER) "🔋 Native Chrono" else "✨ High-Fi Loop"
    }

    private fun startUiUpdateLoop() {
        lifecycleScope.launch {
            while (isActive) {
                val hasRunning = repository.getAllTimers().any { it.state == TimerState.RUNNING }
                if (hasRunning) {
                    adapter.notifyDataSetChanged()
                }
                delay(500L)
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }
    }
}
