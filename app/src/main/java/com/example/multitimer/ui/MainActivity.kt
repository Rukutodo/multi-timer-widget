package com.example.multitimer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Toast
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
                    remainingMs = totalMs
                )

                repository.saveTimer(newTimer)
                refreshList()
                Toast.makeText(this, "Created '$finalName'", Toast.LENGTH_SHORT).show()
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
