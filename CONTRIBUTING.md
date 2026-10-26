# Contributing

Thank you for helping improve VoiceTyping.

## Before opening an issue

- Search existing issues.
- Use the provided bug or feature template.
- Remove API keys, recordings, transcripts, device identifiers, and other private data.
- For security problems, follow `SECURITY.md` instead of creating a public issue.

## Development setup

1. Install JDK 17 or 21 and Android SDK 34.
2. Fork and clone the repository.
3. Create a focused branch from `main`.
4. Run the local verification command:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

No API key is needed for these checks. Real service testing must use your own key entered through the app UI. Never commit it.

## Pull requests

- Keep each pull request focused on one problem.
- Explain the behavior change and the reason for it.
- Add or update tests for changed logic.
- Include screenshots only when the UI changes, and verify they contain no private data.
- Preserve the privacy boundary: no audio or transcript history, no analytics, and no broader accessibility data collection without prior design discussion.
- Confirm that the verification command passes.

By submitting a contribution, you agree that it is licensed under Apache License 2.0.
