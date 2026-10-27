# Privacy

Last updated: 27 October 2026

VoiceTyping is a client-side Android application. It has no separate application backend, user accounts, advertising, or analytics SDKs.

## Data handled by the app

- **Microphone audio:** Captured only after the user taps the voice button. The app sends audio to Google's Gemini API for transcription.
- **Transcript:** Received from Gemini and inserted into the active editor through Android's standard `InputConnection` API.
- **Gemini API key:** Supplied by the user and encrypted with AES-GCM using a non-exportable Android Keystore key.
- **Permission state:** Android provides the current microphone and input-method state.

VoiceTyping does not request Accessibility, display-over-other-apps, notification-listener, contacts, storage, or clipboard access. It does not read the current editor contents. Recording and transcript insertion are blocked in password fields.

When the VoiceTyping keyboard appears in a normal text field, the app may prepare an authenticated Gemini Live connection to reduce delay. No microphone audio is captured or transmitted until the user taps the voice button. Debug builds may write timing values such as connection and response duration to Logcat; these diagnostics contain no audio, transcript text, API key, or editor content.

## Storage and retention

- The app does not write microphone audio or transcript history to files or a database.
- Audio remains in memory only for the active transcription session. This supports audio validation and a batch fallback if the live route fails.
- The encrypted API key remains on the device until the user clears it or uninstalls the app.

## Network services

Audio, the API key, and request metadata are transmitted directly from the device to Google's Gemini API over TLS. Google's terms and privacy practices apply. Users should not dictate information they are not authorized to send to the service.

## Input-method access

Android gives an active keyboard an `InputConnection` for the focused editor. VoiceTyping uses that connection only to insert the completed transcript. It does not call APIs that retrieve existing text. Android may hide or restrict the input method in protected fields, and VoiceTyping independently blocks fields marked as passwords.

## Backups

Android backup is disabled. Data-extraction rules also exclude application data from cloud backup and device transfer.

## User controls

The user can stop recording, switch back to another keyboard, revoke microphone permission, disable the input method, clear the API key, or uninstall the app.

## Public project notice

This document describes the open-source code in this repository. Modified builds may behave differently. Review source and build provenance before installing third-party APKs.
