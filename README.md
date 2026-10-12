# Multi-Timer Home Screen Widget (Android)

An ultra-lightweight, battery-optimized Android application featuring a sleek, scrollable multi-timer widget with real-time controls directly on your home screen.

---

## ✨ Features

- **Multiple Named Timers**: Create and run independent timers simultaneously (e.g., *Deep Work 25m*, *Coffee Steep 4m*, *Workout Rest 90s*).
- **Interactive Home Screen Widget**:
  - **Scrollable List**: View all active and saved timers directly in a single widget container.
  - **Direct Controls**: One-tap **Play / Pause** and **Reset** without opening the full app.
  - **Quick Add**: Tap `+` right on the widget header to spawn instant presets.
  - **Header Stats**: Shows active running count (e.g., `2 running`).
- **Exact Alarms & Notifications**: Wakes the device with custom vibrations, sounds, and high-priority heads-up notifications when any timer finishes.

---

## ⚡ Dual Battery Strategy (Both Implemented!)

You can toggle between two distinct battery strategies directly from the widget header or the in-app settings card:

| Strategy | Engine | Pros & Behavior |
| :--- | :--- | :--- |
| **⚡ Battery Saver (Native Chronometer)** *(Default)* | Native `android.widget.Chronometer` in `RemoteViews` | **0% extra battery drain**. SystemUI's hardware compositor draws the seconds tick-by-tick natively. The application process sleeps completely while counting down. |
| **✨ High-Fi Loop (Dynamic Progress)** | Lightweight foreground loop (`TimerTickService`) | Renders **live percentage progress bars** (`[====>   ]`) and exact formatted countdown strings. Automatically shuts itself down when all timers pause or complete. |

---

## 📂 Project Architecture

```
multi-timer-widget/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/example/multitimer/
│   │   │   ├── data/
│   │   │   │   ├── TimerModel.kt         # Data model with JSON serialization & state enums
│   │   │   │   └── TimerRepository.kt    # SharedPreferences store, AlarmManager & widget sync
│   │   │   ├── service/
│   │   │   │   ├── TimerAlarmReceiver.kt # Exact alarm wake-up, ringtone & vibration
│   │   │   │   └── TimerTickService.kt   # High-Fi 1s ticker foreground service
│   │   │   ├── widget/
│   │   │   │   ├── MultiTimerWidgetProvider.kt # AppWidgetProvider lifecycle & header actions
│   │   │   │   ├── MultiTimerWidgetService.kt  # RemoteViewsFactory for the scrollable ListView
│   │   │   │   └── WidgetActionReceiver.kt     # Handles Play/Pause/Reset broadcasts from clicks
│   │   │   └── ui/
│   │   │       ├── MainActivity.kt       # App dashboard with Strategy toggle & FAB
│   │   │       └── TimerAdapter.kt       # Material 3 RecyclerView adapter
│   │   └── res/
│   │       ├── drawable/                 # Vectors (ic_play, ic_pause, ic_reset, ic_add, card bg)
│   │       ├── layout/
│   │       │   ├── activity_main.xml
│   │       │   ├── item_timer.xml
│   │       │   ├── widget_multi_timer.xml            # Widget container with ListView
│   │       │   ├── widget_timer_item_chronometer.xml # Battery-saver layout (0% CPU)
│   │       │   └── widget_timer_item_dynamic.xml     # High-Fi layout with progress bar
│   │       └── xml/
│   │           └── widget_multi_timer_info.xml       # AppWidget metadata
└── build.gradle.kts
```

---

## 🚀 How to Run in Android Studio

1. Open **Android Studio**.
2. Select **File -> Open...** and choose `D:\upskilling\agy\multi-timer-widget`.
3. Allow Gradle to sync dependencies.
4. Connect an Android device or launch an Emulator (Android 8.0+ / API 26+).
5. Click **Run (`Shift + F10`)**.
6. On your home screen:
   - Long-press empty space -> select **Widgets**.
   - Find **Multi-Timer** and drag the **Multi-Timer Widget** onto your screen.
   - Resize it vertically/horizontally as desired to view multiple timers at once!
