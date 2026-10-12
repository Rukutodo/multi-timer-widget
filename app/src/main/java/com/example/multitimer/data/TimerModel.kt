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
    var soundName: String = "Default Alarm", // Human-readable track/ringtone name
    var isYouTubeMusic: Boolean = false, // True if set to play a song/playlist from YouTube Music
    var youTubeQuery: String? = null // YouTube Music URL or track search term
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
        json.put("isYouTubeMusic", isYouTubeMusic)
        json.put("youTubeQuery", youTubeQuery ?: "")
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): TimerModel {
            val uriStr = json.optString("soundUri", "")
            val ytQueryStr = json.optString("youTubeQuery", "")
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
                soundName = json.optString("soundName", "Default Alarm"),
                isYouTubeMusic = json.optBoolean("isYouTubeMusic", false),
                youTubeQuery = if (ytQueryStr.isNotEmpty()) ytQueryStr else null
            )
        }
    }
}
