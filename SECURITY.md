# Security policy

## Supported version

Security fixes target the latest release and the `main` branch.

## Reporting a vulnerability

Do not open a public issue for a suspected vulnerability. Use GitHub's **Report a vulnerability** option in the repository's Security tab. Include the affected version, reproduction steps, impact, and a minimal proof of concept.

Do not include a real Gemini API key, private recording, transcript, or other personal data. Replace sensitive values with clearly marked test data.

## Credential safety

VoiceTyping never requires a Gemini API key during compilation or automated tests. A legitimate contribution must not add API keys to source code, Gradle files, fixtures, screenshots, logs, or CI configuration.

If a credential is accidentally exposed, revoke it at the provider first. Removing it from a later commit is not sufficient because Git history may retain the value.
