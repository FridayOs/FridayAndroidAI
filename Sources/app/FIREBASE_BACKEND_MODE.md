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
