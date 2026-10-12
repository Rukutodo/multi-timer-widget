package com.example.multitimer.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
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

                val newTimer = TimerModel(
                    id = UUID.randomUUID().toString(),
                    name = finalName,
                    totalDurationMs = totalMs,
                    remainingMs = totalMs,
                    soundUri = chosenUri?.toString(),
                    soundName = chosenName
                )

                repository.saveTimer(newTimer)
                refreshList()
                Toast.makeText(this, "Created '$finalName' with sound '$chosenName'", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun refreshList() {
        adapter.updateData(repository.getAllTimers())
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
