# FRI-534 — Friday design vs implementation checklist

> **2026-07-08 Firebase pivot:** Cloud backend mode is now Firebase-first for auth/profile/device/push/policy metadata. `Sources/api` remains a legacy/minimal reference scaffold and possible future Event Gateway/admin integration area. Android must use a backend-mode interface so `firebase` and `friday_api` are swappable. User-owned AI gateways still run direct from Android; conversation, context, memory, gateway config/key/model/health remain Android-local HXS only.


Design source of truth: `Documents/Designs/android-ai-app-prototype/project/Friday Agent.dc.html`.

Each row traces one design element to the Compose surface that renders it. M1 covers the
shell + auth gate + primary surfaces. Items deferred to a later milestone are flagged
`deferred: <issue>`.

## Token — colour

| Design token            | Source                | Implementation                                            |
|-------------------------|-----------------------|-----------------------------------------------------------|
| `--yellow` `#FFE658`    | design root vars (L14)| `FridayPalette.Primary` + `ColorPalette.FRIDAY` primary   |
| `--bg` / `--surface`    | design root vars      | `MaterialTheme.colorScheme.background` / `surface`        |
| `--text` / `--muted`    | design root vars      | `MaterialTheme.colorScheme.onSurface` / `onSurfaceVariant`|
| Sign-out red `#E5484D`  | design drawer (L310)  | `FridayPalette.Danger`                                    |

## Splash (design L47-57)

| Design element                                | Implementation                                   |
|-----------------------------------------------|--------------------------------------------------|
| Fullscreen logo + "Friday Agent" wordmark     | `FridaySplashScreen.kt` (`FridayLogoAsset`)      |
| 3-dot pulse (`fdDot` keyframe)                | `FridaySplashScreen.kt` `dots()` row             |
| Tap-to-go-login surface                      | Tap handler resolves `if (hasJwt) FridayVoice else FridayLogin` in 2.4 s timer |

Note: wordmark text is the Compose-side `Friday` (`strings.xml friday_brand_name`); the
design's "Friday Agent" wordmark is treated as the brand line. The design's `cursor:pointer`
on the whole splash maps to the timer's auto-route — no manual tap is required.

## Login (design L60-107)

| Design element                                | Implementation                                    |
|-----------------------------------------------|---------------------------------------------------|
| Logo + "Friday" + theme toggle (top bar)      | `FridayLoginScreen.kt` header row                 |
| "Hello, login now" + helper copy              | `R.string.friday_login_heading` + `friday_or_use_account_help` |
| `Your name` field (visual only, disabled)     | `OutlinedTextField(enabled = false)`              |
| `Password` field (visual only, disabled)      | `OutlinedTextField(enabled = false)` + mask icon  |
| `Login Now` button (yellow CTA, no-op)        | Rendered as visual element; disabled behaviour    |
| `Register Now` button (no-op)                 | Not rendered — design intent "no fake login paths"  |
| Google icon CTA (active)                     | Round outlined button → `AccountViewModel.signInWithGoogle` |
| Facebook icon CTA                             | Not rendered — satisfies "no Facebook path"       |

`AccountViewModel.signInWithGoogle` calls `GoogleSignIn.signIn` (dev-token wrapper) →
`FridayApi.exchangeGoogle` (POST `/v1/auth/google`) → `AccountRepository.persist`. The
backend-derived Firebase ID token or legacy Cloud auth session metadata is stored in HXS as `KEY_FRIDAY_JWT`. After `persist`,
cloud profile refresh is exercised with the same JWT and the returned profile rehydrates `Authenticated`.

## Voice home (design L110-172)

| Design element                                | Implementation                                    |
|-----------------------------------------------|---------------------------------------------------|
| Top bar: hamburger + history icon             | `FridayVoiceScreen.kt` top row                    |
| Animated orb                                  | `FridayOrb` radial-gradient fallback              |
| 44-bar waveform                               | `FridayWaveform` `Canvas`                         |
| Hint copy under orb                           | `R.string.friday_voice_idle_hint`                 |
| Mic pill (84dp yellow CTA)                    | `FridayVoiceScreen.kt` mic bar                    |
| State copy (idle / listening / thinking / speaking / done) | `R.string.friday_voice_*_title`      |

Note: `orb.mp4` from design assets (19 MB) intentionally not copied to APK; the design
itself documents `orb/video/fallback` and the radial-gradient orb is the documented
fallback path.

## Chat (design L174-231)

| Design element                                | Implementation                                    |
|-----------------------------------------------|---------------------------------------------------|
| Top bar: menu + new-chat + voice             | `FridayChatScreen.kt` top row                     |
| User bubble (right, surface tint)             | `FridayBubble`                                    |
| AI bubble (left) + thinking dots              | `FridayBubble` + `FridayThinkingDots`             |
| Composer pill: input + mic + yellow send      | `FridayChatScreen.kt` composer row                |
| Send opacity tied to input                    | `enabled = input.isNotBlank()`                    |

## History (design L234-266)

| Design element                                | Implementation                                    |
|-----------------------------------------------|---------------------------------------------------|
| Top bar: back arrow + title                   | `FridayHistoryScreen.kt` top row                  |
| "Recent" section (3 quick actions)            | `FridayHistoryViewModel.quickItems`               |
| "Reminder" section (3-4 items)                | `FridayHistoryViewModel.reminders`                |
| Footer: avatar + name + plan + settings       | `FridayHistoryScreen.kt` footer row               |

## Drawer (design L270-313)

| Design element                                | Implementation                                    |
|-----------------------------------------------|---------------------------------------------------|
| Drawer body 300px, slide-in animation         | `FridayDrawerContent` swapped by `AppScaffold`    |
| Profile header (avatar + name + plan)         | `FridayDrawerContent.kt` header row               |
| Nav rows: Voice / Chat / History              | `NavRow(...)` x3                                  |
| Model section — FRIDAY disabled row first     | `FridayProviderRow` (alpha 0.6, yellow border)    |
| Model section — user-owned gateway rows       | `FridayDrawerViewModel.gatewayModels` (GGUF only) |
| Appearance: System / Light / Dark             | `AppearanceToggle` → `ThemeController.setMode`    |
| Sign-out (red, bordered)                      | `Box(... border 1.5dp ... Danger)`                 |
| Footer: `Friday · v1.0.0`                     | `R.string.friday_version_footer`                  |

## Settings (added surface)

The design does not include a dedicated Settings screen, but FRI-534 explicitly asks for
a `Settings surface tối thiểu để chứa gateway/settings sau này`. `FridaySettingsScreen`
is rendered as the gear-icon destination from History's footer. It exposes:

- `Account` card (email + plan) — surfaces what the backend signed into HXS.
- `Sign out` card (red) — same path as drawer sign-out.
- `Gateway` / `Voice` / `Theme` placeholder cards — reserved for M1-03/04.

## Acceptance criteria vs evidence

| Criterion                                                   | Evidence                                                          |
|-------------------------------------------------------------|-------------------------------------------------------------------|
| Fresh install → Splash → Login                              | `FridaySplashScreen` 2.4 s timer → `hasJwt ? Voice : Login`       |
| Google login cannot be bypassed                             | Name / password fields `enabled = false`; only Google CTA wired   |
| Post-login → Voice home                                     | `resolveStartDestination()` returns `FridayVoice` once JWT present|
| Drawer uses local Android model source                      | `FridayDrawerViewModel.gatewayModels` filters `ModelRepository`    |
| FRIDAY provider is a disabled placeholder                   | `FridayProviderRow` alpha 0.6 + yellow border + "Coming soon"     |
| Dark/Light contrast preserved                               | `ColorPalette.FRIDAY` provides separate `FridayLight`/`FridayDark` |
| No "Tool-Neuron" text on primary Friday surfaces            | `FridaySplashScreen` / `FridayLoginScreen` brand = "Friday"       |
| Backend `POST /v1/auth/google` + `GET /v1/me` end-to-end    | `FridayApi.kt` POST + Bearer GET; verified locally                |
| Screenshot evidence                                         | No AVD / emulator present in this workspace; manual capture deferred to a follow-up. Design-vs-code traceability is provided above so reviewers can verify each row. |