# Changelog

All notable changes are documented here. This project follows [Semantic Versioning](https://semver.org/).

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
