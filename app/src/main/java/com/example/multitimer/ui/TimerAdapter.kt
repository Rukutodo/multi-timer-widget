package com.example.multitimer.ui

import android.content.res.ColorStateList
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.multitimer.R
import com.example.multitimer.data.TimerModel
import com.example.multitimer.data.TimerState
import com.example.multitimer.databinding.ItemTimerBinding
import java.util.Locale

class TimerAdapter(
    private var timers: List<TimerModel>,
    private val onPlayPause: (TimerModel) -> Unit,
    private val onReset: (TimerModel) -> Unit,
    private val onDelete: (TimerModel) -> Unit
) : RecyclerView.Adapter<TimerAdapter.TimerViewHolder>() {

    fun updateData(newTimers: List<TimerModel>) {
        timers = newTimers
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimerViewHolder {
        val binding = ItemTimerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TimerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TimerViewHolder, position: Int) {
        holder.bind(timers[position])
    }

    override fun getItemCount(): Int = timers.size

    inner class TimerViewHolder(private val binding: ItemTimerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(timer: TimerModel) {
            val context = binding.root.context
            binding.textTimerName.text = timer.name
            if (timer.isYouTubeMusic) {
                binding.textSoundName.text = "▶️ YT Music: " + (timer.youTubeQuery ?: timer.soundName)
                binding.textSoundName.setTextColor(ContextCompat.getColor(context, R.color.yt_red_glow))
            } else {
                binding.textSoundName.text = "🎵 " + timer.soundName
                binding.textSoundName.setTextColor(ContextCompat.getColor(context, R.color.primary))
            }

            val remainingMs = when (timer.state) {
                TimerState.RUNNING -> (timer.endRealtimeMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                TimerState.PAUSED -> timer.remainingMs
                TimerState.FINISHED -> 0L
                TimerState.IDLE -> timer.totalDurationMs
            }

            binding.textCountdown.text = formatMillis(remainingMs)

            // Progress bar
            val progress = if (timer.totalDurationMs > 0) {
                val elapsed = (timer.totalDurationMs - remainingMs).coerceAtLeast(0L)
                ((elapsed.toDouble() / timer.totalDurationMs) * 100).toInt().coerceIn(0, 100)
            } else {
                0
            }
            binding.progressTimer.progress = progress

            // Status Badge and Buttons
            when (timer.state) {
                TimerState.RUNNING -> {
                    binding.badgeStatus.text = "RUNNING"
                    binding.badgeStatus.setTextColor(ContextCompat.getColor(context, R.color.accent))
                    binding.btnItemPlayPause.text = "Pause"
                    binding.btnItemPlayPause.backgroundTintList =
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_card_inner))
                    binding.btnItemPlayPause.setTextColor(ContextCompat.getColor(context, R.color.warning))
                }
                TimerState.PAUSED -> {
                    binding.badgeStatus.text = "PAUSED"
                    binding.badgeStatus.setTextColor(ContextCompat.getColor(context, R.color.warning))
                    binding.btnItemPlayPause.text = "Resume"
                    binding.btnItemPlayPause.backgroundTintList =
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_card_inner))
                    binding.btnItemPlayPause.setTextColor(ContextCompat.getColor(context, R.color.text_main))
                }
                TimerState.FINISHED -> {
                    binding.badgeStatus.text = "FINISHED"
                    binding.badgeStatus.setTextColor(ContextCompat.getColor(context, R.color.danger))
                    binding.btnItemPlayPause.text = "Restart"
                    binding.btnItemPlayPause.backgroundTintList =
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_card_inner))
                    binding.btnItemPlayPause.setTextColor(ContextCompat.getColor(context, R.color.text_main))
                }
                TimerState.IDLE -> {
                    binding.badgeStatus.text = "READY"
                    binding.badgeStatus.setTextColor(ContextCompat.getColor(context, R.color.text_sub))
                    binding.btnItemPlayPause.text = "Start"
                    binding.btnItemPlayPause.backgroundTintList =
                        ColorStateList.valueOf(ContextCompat.getColor(context, R.color.surface_card_inner))
                    binding.btnItemPlayPause.setTextColor(ContextCompat.getColor(context, R.color.text_main))
                }
            }
            binding.btnItemPlayPause.setOnClickListener { onPlayPause(timer) }
            binding.btnItemReset.setOnClickListener { onReset(timer) }
            binding.btnDelete.setOnClickListener { onDelete(timer) }
        }

        private fun formatMillis(millis: Long): String {
            val totalSeconds = (millis + 999) / 1000
            val hours = totalSeconds / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }
    }
}
