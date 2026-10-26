# Privacy

Last updated: 26 October 2026

VoiceTyping is a client-side Android application. It does not operate a separate application backend, create user accounts, show advertising, or include analytics SDKs.

## Data handled by the app

- **Microphone audio:** Captured only after the user taps the floating orb. The app sends audio to Google's Gemini API for transcription.
- **Transcript:** Received from Gemini, copied to the Android clipboard, and optionally pasted into the focused editable field.
- **Gemini API key:** Supplied by the user. The app encrypts it with AES-GCM using a non-exportable key stored in Android Keystore.
- **Permission state:** Android provides the current microphone, overlay, notification, and accessibility permission state.

## Storage and retention

- The app does not write microphone audio or transcript history to files or a database.
- Audio is kept in memory only for the active transcription session. This allows audio validation and a batch fallback if the live route fails.
- Android manages clipboard retention. Other apps or the operating system may access clipboard content according to Android's security model.
- The encrypted API key remains on the device until the user clears it or uninstalls the app.

## Network services

Audio, the API key, and request metadata are transmitted directly from the device to Google's Gemini API over TLS. Google's terms and privacy practices apply to that processing. Users should not dictate information they are not authorized to send to the service.

## Accessibility service

The accessibility service is used only to find the currently focused editable field and invoke Android's standard paste action after VoiceTyping produces a transcript. It does not subscribe to accessibility events, log field contents, or paste into fields Android marks as passwords.

## Backups

Android backup is disabled for the application. The project also declares data-extraction rules that exclude application data from cloud backup and device transfer.

## User controls

The user can stop recording with the orb, stop the foreground service, revoke any permission in Android settings, clear the API key in the app, or uninstall the app.

## Public project notice

This document describes the behavior of the open-source code in this repository. Modified builds may behave differently. Review the source and build provenance before installing third-party APKs.
