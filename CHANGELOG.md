# Changelog

All notable changes are documented here. This project follows [Semantic Versioning](https://semver.org/).

## [2.0.2] - 2026-10-27

### Changed

- Accepted finalized post-stop Live transcripts after a 250 ms settle window instead of waiting up to 3 seconds for a missing `turnComplete` signal.
- Kept the existing 3-second protection and batch fallback when no finalized transcript arrives.
- Continued to keep interim/live transcript text out of the UI.

## [2.0.1] - 2026-10-27

### Changed

- Prepared the Gemini Live WebSocket while the voice keyboard is visible.
- Reduced audio chunks from 100 ms to 50 ms for lower streaming latency.
- Reduced the post-commit keyboard-switch delay from 450 ms to 100 ms.
- Added debug-only timing metrics without logging audio, transcripts, or credentials.
- Prevented a failed in-flight preconnect from opening a duplicate Live connection.
- Guarded delayed microphone callbacks with an editor and recording-session token.

Live interim transcription remains intentionally disabled.

## [2.0.0] - 2026-10-27

### Changed

- Replaced the floating overlay and Accessibility service with a standard Android input method.
- Inserted transcripts directly through `InputConnection.commitText`.
- Returned to the previous keyboard after successful transcription.
- Reduced declared app permissions to internet and microphone.
- Updated setup UI and documentation for the keyboard-based flow.

### Removed

- Display-over-other-apps permission and floating overlay service.
- Accessibility permission and paste service.
- Foreground microphone service, notification permission, and clipboard fallback.

### Security

- Blocked voice recording and transcript insertion in password fields.
- Removed screen-control and overlay capabilities that sensitive apps can treat as app-access risks.

## [1.0.4] - 2026-10-26

### Added

- Drag-to-close target for stopping the floating overlay service.
- Unit coverage for dismiss-target geometry.

### Changed

- Improved floating-orb drag behavior and close-target feedback.

## [1.0.3] - 2026-10-26

### Fixed

- Prevented the app from remaining indefinitely in the “preparing text” state.
- Accepted a finalized live transcript when the service omits `turnComplete`.
- Added bounded live connection, finalization, batch-call, and UI processing timeouts.

### Changed

- Started microphone capture and the Gemini Live connection in parallel.
- Queued early audio and sent it immediately after live setup completes.
- Retained batch transcription as a fallback for live-route failure.

## [1.0.0] - 2026-10-25

### Added

- Floating system-wide voice typing control.
- Azerbaijani Gemini transcription.
- Android Keystore-backed API key storage.
- Safe accessibility paste with password-field exclusion.
