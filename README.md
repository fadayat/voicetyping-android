# VoiceTyping for Android

[![Android](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![Android CI](https://github.com/fadayat/voicetyping-android/actions/workflows/android.yml/badge.svg)](https://github.com/fadayat/voicetyping-android/actions/workflows/android.yml)
[![License](https://img.shields.io/badge/License-Apache--2.0-blue.svg)](LICENSE)

VoiceTyping is an Azerbaijani voice input method for Android. Select it temporarily as the active keyboard, dictate text, and return to Gboard after the transcript is inserted.

[Azərbaycan dilində quraşdırma təlimatı](docs/README.az.md)

## Why an input method

Version 2 replaces the floating overlay and Accessibility service with Android's standard input-method API. VoiceTyping no longer requests permission to draw over other apps or control the screen. It sends the result directly to the active editor through `InputConnection.commitText` and never reads existing editor content.

Gboard remains installed and enabled. VoiceTyping switches back to the previous keyboard after a successful transcription. If Android cannot identify the previous keyboard, it opens the system keyboard picker.

## Highlights

- Uses the standard Android `InputMethodService` contract.
- Requests only internet and microphone permissions.
- Streams 16 kHz mono PCM audio to Gemini Live while recording.
- Prepares the Live connection while the keyboard is visible and sends 50 ms audio chunks.
- Queues audio captured during connection setup so the beginning is not lost.
- Falls back to the Gemini batch transcription API when the live route fails.
- Inserts the final transcript directly through `InputConnection`.
- Blocks recording and insertion in password fields.
- Stores the user's API key with AES-GCM backed by Android Keystore.
- Does not save audio, transcripts, or editor contents.
- Uses bounded connection, finalization, network, and processing timeouts.
- Records timing-only diagnostics in debug builds without logging audio or transcript content.

## How it works

```text
Select VoiceTyping keyboard
   |
   +-- tap Speak
   +-- capture microphone audio and open Gemini Live
              |
          tap Stop
              |
              +-- finalize live transcript
              +-- use batch fallback if needed
              |
        InputConnection.commitText
              |
        return to previous keyboard
```

Audio remains in memory only for the active session. The in-memory copy supports validation and the batch fallback; it is discarded when the session ends.

## Install and use

1. Install the APK and open VoiceTyping.
2. Enter your own Gemini API key.
3. Grant microphone permission.
4. Open Android's keyboard settings and enable **VoiceTyping səsli klaviatura**. Keep Gboard enabled.
5. Focus a normal text field and select VoiceTyping from Android's keyboard picker.
6. Tap **Danış**, speak, then tap **Dayandır**.
7. VoiceTyping inserts the transcript and returns to the previous keyboard.

VoiceTyping is disabled in password fields.

> [!IMPORTANT]
> VoiceTyping sends recorded speech to Google's Gemini API for transcription. You supply the API key and are responsible for the associated account, quota, terms, and charges.

The current evaluation APK uses Android's debug signing key and is sideloaded. Source changes remove overlay and Accessibility risk signals, but no APK can guarantee acceptance by every bank app or a clean Google Play Protect verdict. Production trust requires a stable release key and an appropriate verified distribution channel. Do not disable Play Protect to install this app.

## Build from source

Requirements:

- Android Studio or Android SDK 34
- JDK 17 or 21
- Internet access for Gradle dependencies

```bash
git clone https://github.com/fadayat/voicetyping-android.git
cd voicetyping-android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. No Gemini API key is required for compilation or automated tests.

## Architecture

- **Setup UI:** Jetpack Compose and Material 3
- **Voice keyboard:** Android `InputMethodService`
- **Text delivery:** `InputConnection.commitText`
- **State and concurrency:** `StateFlow`, Kotlin Coroutines, and Flow
- **Dependency injection:** Hilt
- **Networking:** OkHttp WebSocket and HTTP
- **Audio:** Android `AudioRecord`, 16 kHz mono PCM
- **Credential storage:** Android Keystore with AES-GCM

The domain layer defines the transcription contract. The data layer owns Gemini Live, batch fallback, audio validation, and WAV encoding. The input-method service owns recording and editor insertion.

## Privacy and security

Read [PRIVACY.md](PRIVACY.md) before use. VoiceTyping does not use an Accessibility service, overlay, notification listener, or clipboard. It never requests or stores current field contents. Audio and API requests go directly from the device to Google's Gemini service.

For vulnerability reports, follow [SECURITY.md](SECURITY.md). Never include credentials or private recordings in a public issue.

## Current scope

- The interface and transcription configuration target Azerbaijani (`az-AZ`).
- The local APK is a debug build intended for evaluation.
- Production distribution still needs release signing and store/developer verification.
- Gemini transcription endpoints and model names may change.

## Contributing

Bug reports and focused pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) first.

## License

Licensed under the [Apache License 2.0](LICENSE).

Google, Android, Gemini, and Gboard are trademarks of their respective owners. This project is independent and is not endorsed by Google.
