# Çizgi service gateway

The Android app calls this HTTP Cloud Function. Press.org.tr, API-Football and WeatherAPI credentials are kept in Google Secret Manager and are never included in the APK or shown to readers. News summaries are generated locally from publisher text, so the app does not call a paid AI summary service.

## Configure service secrets

Create these Secret Manager entries in the Google Cloud project that owns the app:

- `PRESS_API_KEY`
- `FOOTBALL_API_KEY`
- `WEATHER_API_KEY`

Grant the deployed function's runtime service account `Secret Manager Secret Accessor` access to these three secrets. Deploy `cizgiApi` in `us-central1` with the three secrets mounted as environment variables of the same names. Do not put their values in source control, build arguments, or the Android app.

The volleyball proxy reads `VOLLEYBALL_API_KEY` when configured and otherwise falls back to `FOOTBALL_API_KEY`. Use the key enabled for volleyball in the API-Sports dashboard; if it is separate, create a `VOLLEYBALL_API_KEY` Secret Manager entry and mount it as an environment variable. API-Sports must also enable volleyball coverage for the account.

## Deploy

From the repository root, deploy `backend/` as a second-generation Node.js 22 HTTP Cloud Function named `cizgiApi` in `us-central1`. The mobile URL is `https://us-central1-gen-lang-client-0441524192.cloudfunctions.net/cizgiApi`.

The function exposes `/health`, `/v1/news`, `/v1/football/{endpoint}`, `/v1/volleyball/{endpoint}`, and `/v1/weather`. It limits provider endpoints, bounds response sizes, rate limits callers, caches provider responses, and only fetches article pages from the configured news domains.

Volleyball fixture responses are cached for five minutes; league and standings responses are cached for six hours. The Android client refreshes only the selected competition and uses a slower interval for volleyball to preserve API-Sports' free daily request quota.

PHA currently exposes its archive through page-based requests. The function forwards pages up to 100 items, and the Android archive stores and loads up to 100,000 articles. A larger total is only available when the upstream provider actually has more records.

