package com.example.multitimer.data

import org.json.JSONObject

enum class TimerState {
    IDLE,
    RUNNING,
    PAUSED,
    FINISHED
}

enum class BatteryStrategy {
    NATIVE_CHRONOMETER, // 0% extra battery: SystemUI renders countdown directly in RemoteViews
    HIGH_FIDELITY_LOOP  // Dynamic updates: Real-time progress bar & exact formatted countdown string
}

data class TimerModel(
    val id: String,
    var name: String,
    val totalDurationMs: Long,
    var remainingMs: Long = totalDurationMs,
    var endRealtimeMs: Long = 0L, // Used with SystemClock.elapsedRealtime()
    var state: TimerState = TimerState.IDLE,
    var soundUri: String? = null, // Custom audio file or ringtone URI
    var soundName: String = "Default Alarm" // Human-readable track/ringtone name
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("id", id)
        json.put("name", name)
        json.put("totalDurationMs", totalDurationMs)
        json.put("remainingMs", remainingMs)
        json.put("endRealtimeMs", endRealtimeMs)
        json.put("state", state.name)
        json.put("soundUri", soundUri ?: "")
        json.put("soundName", soundName)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): TimerModel {
            val uriStr = json.optString("soundUri", "")
            return TimerModel(
                id = json.getString("id"),
                name = json.getString("name"),
                totalDurationMs = json.getLong("totalDurationMs"),
                remainingMs = json.optLong("remainingMs", json.getLong("totalDurationMs")),
                endRealtimeMs = json.optLong("endRealtimeMs", 0L),
                state = try {
                    TimerState.valueOf(json.optString("state", TimerState.IDLE.name))
                } catch (e: Exception) {
                    TimerState.IDLE
                },
                soundUri = if (uriStr.isNotEmpty()) uriStr else null,
                soundName = json.optString("soundName", "Default Alarm")
            )
        }
    }
}
