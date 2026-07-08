# FRIDAY AI Firebase backend mode

Default cloud backend mode is now `firebase`.

`friday_api` remains as legacy/reference mode for the minimal Node API in `../api` and future backend-only surfaces. Android code must access cloud auth/profile through an interface, not direct hardcoded `/v1/*` calls.

## Backend modes

| Mode | Purpose | Status |
|---|---|---|
| `firebase` | Firebase Auth + Firestore + FCM + Remote Config for cloud metadata | default target |
| `friday_api` | Legacy/minimal Node API: `/health`, `POST /v1/auth/google`, `GET /v1/me` | reference/fallback only |

## Firebase stores

Allowed Firebase data:

- Firebase Auth UID
- email / display name / avatar URL from Google sign-in
- plan / entitlement metadata
- device registry metadata
- FCM token
- push routing metadata
- Remote Config flags
- non-sensitive app policy/settings

Firebase must not store by default:

- prompt
- assistant answer
- chat transcript
- session summary
- durable memory
- ContextEngine package
- gateway API key/token
- gateway base URL
- selected provider/model
- provider health result
- audio stream

## Required files from Long

Place Firebase config here after project creation:

```text
Sources/app/app/google-services.json
```

Do not commit real production credentials unless project policy says public client config is allowed. For this repo, treat it as local/dev config first.

## Required Firebase console setup

1. Create Firebase project.
2. Add Android app:
   - package name: `com.friday.ai`
   - app nickname: `FRIDAY AI Android`
3. Download `google-services.json` into `Sources/app/app/google-services.json`.
4. Enable Authentication provider: Google.
5. Add SHA-1/SHA-256 fingerprints for debug/release signing keys.
6. Create Firestore database.
7. Create Remote Config namespace/params for feature flags.
8. Enable Cloud Messaging.

## Proposed Firestore shape

```text
/users/{uid}
  displayName
  email
  avatarUrl
  plan
  createdAt
  updatedAt

/users/{uid}/devices/{deviceId}
  platform
  appVersion
  fcmToken
  lastSeenAt
  policyVersion
```

No conversations, messages, memories, gateway configs, provider secrets, selected model, or health results in Firestore.

## Implementation status (FRI-571)

Firebase-first backend mode is wired end-to-end. All Firebase touches are gated on `BuildConfig.FIREBASE_CONFIGURED`, which is `true` only when `app/google-services.json` exists at build time (the `com.google.gms.google-services` plugin is applied conditionally in `app/build.gradle.kts`). Without the file the app compiles, installs, and runs; any Firebase call surfaces `FirebaseCloud.SETUP_MESSAGE` instead of crashing.

Code map (`app/src/main/java/com/dark/tool_neuron/data/firebase/`):

- `FirebaseCloud` — single readiness gate (`ready(context)`), setup-error message, `default_web_client_id` resolver.
- `FirebaseGoogleAuth` — Google → Firebase Auth via `androidx.credentials` + `googleid` (no `play-services-auth` AAR). Returns a `FirebaseUser`.
- `FirebaseProfileStore` — Firestore `/users/{uid}` upsert + `/users/{uid}/devices/{deviceId}` registry with FCM token.
- `FirebasePolicy` — Remote Config feature flags + `policy_version` (defaults on missing config, never crashes).
- `FridayMessagingService` — `FirebaseMessagingService` (Hilt `@AndroidEntryPoint`); `onNewToken` pushes into the device registry.
- `CloudAccountGateway.FirebaseAccountGateway` — orchestrates sign-in → profile → device → policy, rides the Firebase ID token in `FridaySession.jwt` so `AccountRepository` / `ScaffoldViewModel` gates stay backend-agnostic.

Backend mode is chosen by `AppPreferences.backendMode` (default `FIREBASE`) via `CloudAccountGatewayProvider.current()`. `friday_api` remains the legacy fallback.

### Device id

The Firestore device document id is a random per-install UUID sealed in the encrypted `app_prefs` vault (`AppPreferences.deviceRegistryId()`), **not** `Settings.Secure.ANDROID_ID` — the repo forbids OS-attested/global ids for any persisted identity.

### Local setup

1. Follow the console steps above and drop the real file at `Sources/app/app/google-services.json` (gitignored).
2. `app/google-services.json.template` is a committed placeholder documenting the required shape — do not rename it to the real path.
3. Rebuild. `FIREBASE_CONFIGURED` flips to `true` automatically; no code change needed.

### Data boundary (enforced)

Firestore writes are limited to: `displayName`, `email`, `avatarUrl`, `plan`, `createdAt`, `updatedAt` on `/users/{uid}`; and `platform`, `appVersion`, `sdkInt`, `model`, `fcmToken`, `lastSeenAt`, `policyVersion` on `/users/{uid}/devices/{deviceId}`. No prompt, transcript, assistant response, memory, context package, gateway config/key/token/base URL, selected model, provider health, or audio is ever uploaded. No `conversations` / `messages` / `memories` / `gateways` collections are created.
