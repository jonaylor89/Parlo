# Parlo

A hands-free, real-time voice language tutor for Android. Put your phone in your pocket, put earbuds in, go for a walk, and talk with Gemini in the language you're learning.

Native Kotlin + Jetpack Compose (Material 3), Gemini Live API over WebSockets. Single user, no accounts, no backend.

## Features

- **Live voice conversation** with the Gemini Live API (bidirectional audio streaming over WebSocket)
- **Hands-free walking mode**: foreground service with microphone type, notification controls (mute / end), MediaSession headset button support, audio focus, earcons for state changes
- **Bluetooth / wired earbud routing** with mid-session route changes
- **Barge-in**: interrupt the tutor at any time; playback flushes instantly
- **Language / dialect / level / scenario / correction style** pickers, plus quick-switch chips of recent combos
- **Voice-driven switching**: say "let's switch to Portuguese" and the tutor calls `switch_language` mid-session
- **Vocabulary**: say "save that word" and the tutor calls `save_vocab`; review in a list or flashcard mode
- **Session history** with full transcript and spoken end-of-session recap (long-press End to skip the recap)
- **Automatic model discovery**: lists models advertising `bidiGenerateContent`, prefers the newest native-audio Live model, and lets you override with free text
- **Resilience**: session resumption handles, exponential-backoff reconnect, connectivity monitoring, fresh-session fallback with recent-transcript context
- **Encrypted API key storage** via `EncryptedSharedPreferences` (Android Keystore)

## Requirements

- Android 8.0 (API 26) or newer; targets API 35
- JDK 17
- Android SDK Platform 35 and Build-Tools 35.0.0
- A Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey)

## Build & install

```bash
# point Gradle at your SDK (or set ANDROID_HOME)
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio (Ladybug or newer) and press Run.

Lint: `./gradlew :app:lintDebug`

## First run

1. Open Parlo and tap the gear icon.
2. Paste your Gemini API key and tap **Save**. It is stored encrypted on-device and never leaves the phone except in requests to `generativelanguage.googleapis.com`.
3. Tap **Refresh** under *Model* to discover Live-capable models. The best native-audio model is picked automatically; type a model name to override.
4. Pick a voice (Puck, Aoede, Charon, Kore, Fenrir, Leda, Orus, Zephyr).
5. Back on the main screen choose language, dialect, level, scenario, and correction style, then tap **Start**.

Parlo will ask for **microphone**, **notification** (Android 13+), and **Bluetooth** (Android 12+) permissions the first time you start a session.

## Talking to the tutor

The tutor speaks first and keeps turns short. Things you can say at any time (in either language):

- "Repeat that slowly"
- "What does ___ mean?" / "How do I say ___?"
- "Correct me more" / "Stop correcting me"
- "Save that word" → stored to Vocabulary
- "Let's switch to Italian" / "Make it easier" → switches language / level live

Changing language, dialect, level, scenario, or correction style in the UI during a session sends a text turn to the tutor. Changing voice or model reconnects with session resumption so context is kept.

## Project layout

```
app/src/main/java/com/parlo/app/
  ParloApp.kt              Application + manual DI container
  MainActivity.kt
  model/                   SessionConfig, LiveSessionState, enums
  data/                    Room DB (sessions, turns, vocab), Settings (encrypted + DataStore), model discovery
  gemini/                  GeminiApi (URLs/version), Messages (wire types), GeminiLiveClient (WebSocket),
                           PromptBuilder (system prompt + tools), ToolHandler
  audio/                   MicrophoneStreamer, AudioPlayer, AudioRouteManager, Earcons
  service/                 LiveSessionService (foreground service, MediaSession, reconnect, recap)
  ui/                      Compose screens: main, settings, vocab, history; MainViewModel; theme; nav
```

## Gemini Live details

- Endpoint: `wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=…`
  (API version is centralised in `GeminiApi.API_VERSION`)
- Input: 16 kHz mono 16-bit PCM, ~30 ms chunks, base64 in `realtimeInput.audio`
- Output: 24 kHz mono 16-bit PCM played via `AudioTrack` with a jitter buffer
- Setup enables `AUDIO` response modality, input/output transcription, session resumption, sliding-window context compression, and the `save_vocab` / `switch_language` tools.

## Privacy

Everything (transcripts, vocab, settings) is stored locally in the app's private storage. Backups are disabled. The only network traffic is to Google's Gemini API using your own key.
