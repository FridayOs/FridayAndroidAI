# FRIDAY AI backend scope

Status: legacy/minimal Node API scaffold created during `FRI-534`.

## Purpose

`Sources/api` currently exists as a small reference backend for early Android auth/profile smoke tests. It is not the owner of FRIDAY assistant data.

The long-term product direction is **Firebase-first for cloud metadata** and **Android-local for assistant/private data**.

## Implemented now

Current Node API implementation:

- `GET /health`
- `POST /v1/auth/google`
- `GET /v1/me`
- local SQLite dev storage at `data/*.db`
- Vitest coverage for auth/profile flow

Current SQLite table:

| Table | Fields | Purpose |
|---|---|---|
| `users` | `id`, `display_name`, `email`, `avatar_url`, `plan`, `created_at` | Dev auth/profile metadata only |

`POST /v1/auth/google` is still an M1 stub. It derives a deterministic dev user from a non-empty ID-token string. Real Google verification belongs to a future backend path only if we keep this Node API alive.

## Data this backend may store

Allowed cloud/backend metadata:

- auth account id
- display name
- email
- avatar URL
- plan / entitlement
- device registration metadata
- push token routing metadata
- policy metadata
- non-sensitive feature flags / app settings
- operational health/status metadata

## Data this backend must not store by default

Forbidden by default:

- chat transcript
- user prompt
- assistant response
- context package
- session summary
- durable memory
- local conversation history
- gateway config
- provider API key / token
- provider base URL
- selected provider/model
- provider health result
- audio stream / raw voice content

These stay in Android HXS/local storage unless a later explicit user-controlled export/sync feature is approved.

## Firebase migration direction

The planned cloud path is Firebase:

| Need | Firebase service |
|---|---|
| Google login | Firebase Authentication |
| User profile metadata | Firestore `/users/{uid}` |
| Device registry | Firestore `/users/{uid}/devices/{deviceId}` |
| Push routing | Firebase Cloud Messaging |
| Feature flags / app policy | Firebase Remote Config |
| Server-only future logic | Cloud Functions, only when needed |

After Firebase auth/profile/device/push are implemented, this Node API should be treated as optional legacy/reference code or future Event Gateway/admin integration space, not as the main mobile auth path.

## When to develop `Sources/api` further

Develop this folder only for backend-only needs that Firebase client SDKs cannot cover cleanly:

- external webhook/Event Gateway ingestion
- OpenClaw/Hermes bridge endpoints
- server-side entitlement verification
- admin actions
- billing webhook validation
- future FRIDAY-managed provider path

Do not add user-owned AI provider proxy routes here for M1/M2. User-owned Gemini/OpenAI/Anthropic/DeepSeek/custom gateways connect directly from Android with local encrypted credentials.
