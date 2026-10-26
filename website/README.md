# VoiceTyping website

The public landing page is a buildless static site. It has no runtime secrets, analytics, forms, microphone access, or server-side code.

## Local preview

```bash
cd website
python3 -m http.server 4173
```

Open `http://127.0.0.1:4173`.

## Production deployment

The `website` directory is the Vercel project root. Vercel configuration and security headers live in `vercel.json`. Local `.vercel/` metadata is ignored by Git.

```bash
npx vercel@latest link --yes --project voicetyping-android
npx vercel@latest deploy --prod
```

The GitHub repository is the canonical source. Never add API keys or deployment tokens to this directory.
