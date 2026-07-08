# FRIDAY AI Android — Technical Feasibility Report

Date: 2026-07-05
Project: `friday-android`
Source root: `/home/leo/data/Projects/friday-android`
Design source of truth: `Documents/Designs/android-ai-app-prototype/project/Friday Agent.dc.html`
Existing source candidate: `Sources/` (`Tool-Neuron` open-source Android app)

## 1. Executive summary

Recommendation: adapt/fork the existing `Sources/` Android app, but treat it as a native Android engine/runtime foundation, not as UI source of truth.

Best path:

1. Keep Android native stack: Kotlin + Jetpack Compose + Hilt + Gradle.
2. Rebrand package/app to FRIDAY AI.
3. Replace/reshape top-level UX to match `Friday Agent.dc.html` design.
4. Keep existing AI capabilities where useful: on-device GGUF chat, sherpa-onnx STT/TTS, encrypted storage, model manager, RAG, inference service.
5. Add new FRIDAY backend integration for cloud agent, sync, account, model routing, assistant memory, and wake-word policy.
6. Build `Hey Friday` as a progressive feature: push-to-talk first, foreground hotword next, background hotword last because Android restrictions make always-on wake words expensive and permission-sensitive.

Do not rewrite from scratch unless product goal is a thin cloud-client app only. Existing source already contains hard Android work: inference service, STT/TTS, encrypted storage, Compose architecture, model downloads, native modules, plugin runtime. Rebuilding those would cost much more than replacing the UI.

## 2. What was inspected

### Design files

- `Documents/Designs/android-ai-app-prototype/README.md`
- `Documents/Designs/android-ai-app-prototype/project/Friday Agent.dc.html`
- `Documents/Designs/android-ai-app-prototype/project/Aura AI.dc.html`
- `Documents/Designs/android-ai-app-prototype/project/android-frame.jsx`
- assets:
  - `assets/logo.png`
  - `assets/avatar.jpg`
  - `assets/orb.mp4`
  - `.thumbnail`
  - uploaded Figma/video files

Primary handoff says to read `Friday Agent.dc.html` in full. That is the source of truth.

### Source files

Key inspected files:

- `Sources/README.md`
- `Sources/CLAUDE.md`
- `Sources/settings.gradle.kts`
- `Sources/app/build.gradle.kts`
- `Sources/app/src/main/AndroidManifest.xml`
- `Sources/app/src/main/res/values/strings.xml`
- `Sources/app/src/main/java/com/dark/tool_neuron/voice/SttRecorder.kt`
- `Sources/app/src/main/java/com/dark/tool_neuron/voice/TtsPlayer.kt`
- `Sources/app/src/main/java/com/dark/tool_neuron/viewmodel/HomeViewModel.kt`
- `Sources/app/src/main/java/com/dark/tool_neuron/ui/screens/home_screen/HomeScreenBody.kt`
- `Sources/app/src/main/java/com/dark/tool_neuron/ui/navigation/TNavigation.kt`
- `Sources/gradle/libs.versions.toml`

## 3. Design source of truth summary

Primary design: `Friday Agent.dc.html`.

### Brand and app identity

Current design label:

- Splash title: `Friday Agent`
- Top login mini-brand: `Friday`
- Drawer footer: `Friday Agent · v1.0.0`

Product target from user:

- APK app name: `FRIDAY AI`

Suggested product naming:

- Launcher/app name: `FRIDAY AI`
- In-app assistant name: `Friday`
- Internal/old design label `Friday Agent` should be renamed to `FRIDAY AI` where user-facing.

### Visual language

Primary style from `Friday Agent.dc.html`:

- Device size: 390 × 844 prototype, rounded black phone frame.
- Font: Poppins.
- Primary accent: yellow `#FFE658`; pressed yellow `#F2D63B`.
- Light tokens:
  - `bg`: `#FFFFFF`
  - `text`: `#1A1A1A`
  - `muted`: `#9A9AA2`
  - `border`: `#EFEFF1`
  - `surface`: `#F5F5F6`
  - `inputBorder`: `#E6E6E9`
- Dark tokens:
  - `bg`: `#141416`
  - `text`: `#F4F4F6`
  - `muted`: `#86868E`
  - `border`: `#26262B`
  - `surface`: `#1E1E23`
  - `inputBorder`: `#2E2E34`
- Orb visual: `assets/orb.mp4` in circular crop, scaled around 1.32×, glow changes by state.
- Animation primitives:
  - fade/pop enter animations
  - waveform bars around orb
  - orb pulse while active
  - drawer slide in
  - typing dots

### Screens in primary design

#### 1. Splash

- Center logo: `assets/logo.png`, width 128.
- Title: `Friday Agent` / should become `FRIDAY AI` or `Friday` based final product copy.
- Bottom loading dots.
- Auto advances to login after ~2400ms.
- Tapping also advances.

#### 2. Login

Elements:

- Top brand row: logo + `Friday`.
- Theme toggle button.
- Title: `Hello, login now`.
- Inputs:
  - `Your name`
  - `Password`
  - password visibility toggle
- Actions:
  - `Login Now`
  - `Register Now`
  - Google login button
  - Facebook login button
- Forgot password link.

Implementation note: current design login is placeholder quality. Product should decide if FRIDAY AI uses:

- FRIDAY account auth
- Google only
- local-only mode
- guest mode

#### 3. Voice home

Core product screen.

Elements:

- Top left hamburger opens drawer.
- Top right history/chat icon opens history.
- Center status title:
  - `Tap to send voice message`
  - `Listening…`
  - `Thinking…`
  - `Speaking…`
  - `Tap to ask again`
- Orb center with 44 radial waveform bars.
- Transcript area:
  - user speech transcript while listening
  - assistant answer while speaking/done
  - idle hint: `Tap the mic and ask me anything — I'll listen and reply out loud.`
- Controls:
  - left: switch to typed chat
  - center: large yellow mic button
  - right: reset/clear
- Voice state machine:
  - idle
  - listening
  - thinking
  - speaking
  - done

This should be first-class home, not a secondary feature.

#### 4. Chat

Typed assistant screen.

Elements:

- Hamburger menu.
- New chat action.
- Back to voice action.
- Scrollable chat messages.
- User bubble: right aligned, surface background.
- AI message: plain text with actions after done:
  - copy
  - thumbs up
  - regenerate
- Thinking dots.
- Input composer:
  - placeholder `Type message here`
  - mic icon
  - send circle button.

#### 5. History

Elements:

- Back to voice.
- Title: `Friday Agent`.
- Recent quick commands:
  - Quick command
  - Open Care
  - How to use
- Reminder/history list.
- Bottom profile row:
  - avatar
  - user name
  - plan label
  - settings icon

#### 6. Drawer

Elements:

- Profile header.
- Navigation:
  - Voice assistant
  - Chat
  - History
- Model selection:
  - Friday / Default
  - Gemini 2.5 / Google
  - Claude / Anthropic
  - Llama 3 / Meta
  - DeepSeek / DeepSeek
- Appearance toggle:
  - Light
  - Dark
- Sign out.
- App footer.

### Secondary design: `Aura AI.dc.html`

`Aura AI.dc.html` is not primary but useful reference. It has extra product ideas absent in primary:

- Onboarding carousel.
- Bottom nav with Chat / History / Models / Settings.
- Dedicated model screen.
- Dedicated settings screen.
- Private mode toggle: `Run open models on-device`.
- Model cards with blurbs.
- More polished multi-model chat UX.

Useful borrow list:

- onboarding structure
- model cards
- private mode toggle concept
- settings screen layout
- history search

Do not use Aura branding.

## 4. Existing source technical assessment

Existing source is `Tool-Neuron`, native Android.

### Stack

From inspected Gradle files:

- Kotlin
- Jetpack Compose
- Material 3
- Hilt DI
- Navigation Compose
- Kotlin Serialization
- Gradle Kotlin DSL
- AGP `9.2.1`
- Kotlin `2.3.21`
- Compose BOM `2026.05.00`
- minSdk `31`
- targetSdk `37`
- ABI filters: `arm64-v8a`, `x86_64`

### Existing capabilities relevant to FRIDAY AI

Good fit:

- Native Compose UI.
- Multi-turn chat.
- On-device GGUF model inference.
- Streaming generation.
- Voice stack:
  - `SttRecorder` uses `AudioRecord` with `VOICE_RECOGNITION`, 16 kHz mono float PCM.
  - `TtsPlayer` streams sentence-chunked TTS via `InferenceClient.synthesize` and `AudioTrack`.
  - `VoiceModelManager` is already wired into `HomeViewModel`.
- STT/TTS catalog includes:
  - Whisper Tiny English
  - Whisper Tiny multilingual
  - Piper/VITS voice models
- RAG/document ingestion.
- Model manager and Hugging Face explorer.
- Encrypted storage / secure session policy.
- Native background inference service.
- Embedded HTTP server.
- Plugin runtime.

### Existing app identity conflict

Current app identity:

- Root project name: `Tool-Neuron`
- Package/applicationId: `com.dark.tool_neuron`
- App name: `Tool-Neuron`
- Launcher icon: current ToolNeuron assets.

Required FRIDAY identity changes:

- app name: `FRIDAY AI`
- package id likely new: e.g. `com.friday.ai` or `com.fridayos.ai`
- launcher icon/logo from design asset
- package/class namespace migration if doing clean rebrand
- release signing/key setup

### Existing architecture complexity

Strength:

- Very capable AI app baseline.
- Voice and local inference are already hard problems solved.
- Security/privacy layer is deep.

Risk:

- Source is opinionated: privacy-first/offline-only, HXS-only storage, native security policy, plugin runtime, RAG, model store.
- UI is large and already has its own navigation/scaffold rules.
- The design wants simpler consumer assistant UX; current app is power-user/local-model app.
- Full package rename is non-trivial across many modules/native/manifest/proguard paths.
- Existing repo rules say no spec/plan docs in `Sources`; report should stay in `Documents`, not `Sources`.

## 5. Feasibility: applying design to existing source

Feasible: yes.

Recommended adaptation strategy:

- Keep modules and AI services.
- Add a new FRIDAY UX layer in `:app` rather than trying to skin every existing screen first.
- Make voice screen the start destination after auth/onboarding.
- Map existing `HomeViewModel`/voice manager into a new simplified FRIDAY view model or facade.
- Hide advanced ToolNeuron screens behind settings/dev menu if still needed.
- Phase out old visual system gradually.

### Mapping design to existing code

| Design feature | Existing source support | Work needed |
|---|---|---|
| Splash | Intro/setup screens exist | Replace with FRIDAY splash |
| Login/register/social | No clear cloud auth flow found in inspected files | Need FRIDAY backend auth or local account decision |
| Voice home | STT/TTS/inference already exists | Build new Compose screen and state machine |
| Orb animation | Design has `orb.mp4` | Add asset to Android raw/assets; render via ExoPlayer/Media3 or animated drawable/Lottie-like substitute |
| Waveform bars | `RecordingEqualizer.kt` exists | Reuse/adapt amplitude visualization into radial waveform |
| Chat | Existing chat screens/messages exist | Re-skin to design and simplify controls |
| History | Existing chat repository exists | Build new history screen mapped to chats |
| Model picker | Existing model repository/manager exists | Build drawer cards / model selector |
| Theme toggle | Existing theme controller exists | Add FRIDAY tokens and dark/light toggle |
| Settings/profile | Existing settings exist | Create design-aligned simplified settings |
| TTS playback | Existing `TtsPlayer` | Reuse |
| STT | Existing `SttRecorder` and sherpa/Whisper support | Reuse |
| Wake word | Not found as implemented | New capability required |

### Main incompatibilities

1. UI style mismatch: current app is Material/power-user; design is bespoke Poppins/yellow/orb assistant.
2. Backend/auth assumptions missing: design has login/social but source is local/offline-first.
3. Multi-model drawer in design includes cloud providers; current source centers local GGUF/provider types.
4. Wake word is absent.
5. If FRIDAY AI must be cloud-agent-first, current local-only assumptions may fight product direction.

## 6. Rebuild from scratch comparison

### Option A — Adapt existing source

Best when:

- Need real APK fast.
- Need voice, local model, local privacy/offline mode.
- Need RAG/model manager later.
- Want native performance.

Pros:

- Existing STT/TTS stack.
- Existing inference service.
- Existing secure storage.
- Existing Compose architecture.
- Existing model downloads/catalog.
- Android-native from day 1.

Cons:

- Big codebase with unrelated features.
- Rebrand/package migration takes care.
- UI replacement touches navigation, scaffold, theme, home flow.
- Existing offline/local-first product may not match FRIDAY cloud-agent-first requirement.

Estimated implementation risk: medium.

### Option B — Build new native Android app from scratch

Recommended stack if rebuild:

- Kotlin + Jetpack Compose.
- Hilt or Koin DI.
- Kotlin Coroutines + Flow.
- Retrofit/Ktor client + OkHttp WebSocket/SSE.
- Room or SQLDelight for local cache, unless FRIDAY mandates encrypted HXS-style storage.
- AndroidX Credentials / Google Sign-In if account auth needed.
- Android SpeechRecognizer or sherpa-onnx/Whisper for STT.
- Android TextToSpeech or cloud TTS or sherpa/Piper for TTS.
- WorkManager/ForegroundService for background voice/wake workflows.

Pros:

- Clean product architecture.
- Easier FRIDAY branding and backend-first flow.
- Less baggage.
- Faster UI implementation if cloud-first only.

Cons:

- Rebuilding voice stack, chat persistence, streaming, permissions, audio pipeline.
- Wake word still hard.
- Offline model support becomes a large separate project.
- More unknowns around APK quality/performance.

Estimated implementation risk: medium-high if voice/offline needed; low-medium if thin cloud-client only.

### Option C — React Native / Flutter rebuild

Not recommended for this product as primary if voice/wake/offline inference matter.

Reason:

- Design can be implemented quickly in RN/Flutter.
- But hotword/background voice, audio session, local inference, native model runtimes, Android foreground services, and low-latency streaming are native-heavy.
- You will end up writing native modules anyway.

Use RN/Flutter only if:

- App is cloud-only.
- Wake word is not required beyond foreground push-to-talk.
- Local inference is out of scope.

## 7. Recommended technology path

Recommendation: native Android with Kotlin + Jetpack Compose, based on existing `Sources/` fork.

Technical target:

- APK name: `FRIDAY AI`
- Native shell: Kotlin/Compose
- Primary UX: voice-first FRIDAY assistant
- Runtime modes:
  - Cloud FRIDAY agent mode as default
  - Optional local/private mode using existing ToolNeuron local models if product wants it
- Backend protocol:
  - HTTPS REST for auth/profile/settings/history
  - WebSocket or SSE for live agent streaming/events
  - Binary/multipart upload for audio if backend STT is used
  - Optional on-device STT/TTS for low latency/private mode

## 8. Voice and `Hey Friday` feasibility

### Push-to-talk voice

Feasible now.

Existing source has:

- `RECORD_AUDIO` permission.
- `SttRecorder` for microphone capture.
- STT via sherpa-onnx/Whisper models.
- TTS playback via `TtsPlayer`.
- Voice state already exposed in `HomeViewModel`.

Implementation:

- Build design voice screen.
- Mic button starts/stops recording.
- STT transcript becomes user message.
- Send transcript to model/backend.
- Stream answer to UI.
- TTS reads answer.

### Foreground `Hey Friday`

Feasible with moderate work.

Approach:

- While FRIDAY AI is open, run a small keyword spotter in foreground.
- Options:
  - Porcupine/Picovoice custom wake word `Hey Friday` (commercial/licensing likely).
  - sherpa-onnx keyword spotting model if compatible.
  - Vosk/Kaldi KWS.
  - lightweight on-device TFLite keyword model.
- When detected, trigger the same voice flow.

Pros:

- Good UX while app open.
- Lower Android restriction friction.

### Background always-on `Hey Friday`

Possible but high-risk/high-policy.

Android realities:

- Continuous mic in background needs foreground service + persistent notification.
- Battery optimization will affect reliability.
- OEMs may kill background service.
- Google Play review/privacy expectations are strict.
- Android 12+ foreground service/mic indicators expose recording.
- Hotword cannot behave exactly like system-level `Ok Google` unless privileged/system app.

Recommendation:

- Phase 1: push-to-talk.
- Phase 2: foreground hotword while app active.
- Phase 3: optional background hotword with foreground notification and clear privacy controls.
- Do not promise `Ok Google` parity. Use wording: “wake FRIDAY while app is open”, then “optional always-listening mode”.

## 9. Backend requirements derived from design

Design is not just UI; it implies backend/product capabilities.

### Account/auth

Needed if login is real:

- Register/login endpoint.
- Password auth or Google OAuth.
- Token refresh.
- Forgot password.
- Profile endpoint.
- Plan/subscription endpoint.

Suggested endpoints:

- `POST /auth/login`
- `POST /auth/register`
- `POST /auth/google`
- `POST /auth/refresh`
- `POST /auth/logout`
- `GET /me`
- `PATCH /me`
- `GET /billing/plan`

### Agent conversation

Needed for chat/voice:

- Create conversation.
- Send message.
- Stream assistant response.
- Cancel response.
- Regenerate.
- Copy/feedback actions.
- Persist history.
- Search history.

Suggested endpoints:

- `GET /conversations`
- `POST /conversations`
- `GET /conversations/{id}`
- `POST /conversations/{id}/messages`
- `POST /conversations/{id}/regenerate`
- `POST /conversations/{id}/cancel`
- `POST /messages/{id}/feedback`
- `DELETE /conversations/{id}`

Streaming:

- Prefer WebSocket for bidirectional voice/agent events.
- SSE acceptable for text-only streaming.

Event model:

- `user_transcript.partial`
- `user_transcript.final`
- `assistant.delta`
- `assistant.done`
- `assistant.audio.chunk` if backend TTS streaming
- `tool.started`
- `tool.result`
- `error`

### Model routing

Design drawer includes:

- Friday
- Gemini 2.5
- Claude
- Llama 3
- DeepSeek

Backend needs model catalog and routing:

- `GET /models`
- `PATCH /me/default-model`
- model ids mapped to provider configs server-side
- user plan gating for premium models

Recommendation:

- Use `friday` as default orchestrator model/agent.
- Keep provider names visible as “routes” or “models” only if product wants user choice.
- Avoid putting provider API keys in APK.

### Voice

Two possible backend strategies:

#### Device STT/TTS

- Existing source supports on-device STT/TTS.
- Backend only receives text.
- Lower backend cost.
- Works offline/private.
- Requires model download/storage.

#### Cloud STT/TTS

- Backend receives audio chunks.
- Better quality/latency depending provider.
- Needs audio streaming.
- Higher cost/privacy load.

Suggested hybrid:

- Default: device STT if model installed, fallback cloud STT.
- Default: cloud TTS if product voice quality matters, fallback device TTS.
- User setting: Private Mode = all on-device where possible.

### Memory and personalization

Voice assistant likely needs:

- user preferences
- remembered facts
- local device context permissions
- assistant identity/persona

Endpoints:

- `GET /memory`
- `POST /memory`
- `DELETE /memory/{id}`
- `GET /settings/assistant`
- `PATCH /settings/assistant`

### Device capabilities / agent tools

If “AI agent” means phone assistant, backend/app needs tool boundary:

- open app / intent launcher
- notifications/reminders
- calendar/tasks
- contacts (if allowed)
- files/media
- web search
- location
- home automation / FRIDAY Core integration later

Android implementation must gate permissions per capability.

### Sync/offline

Needed decisions:

- Are chats synced to FRIDAY backend?
- Is local-only mode allowed?
- Are local model files first-class?
- Does backend know about local/private mode?

## 10. Implementation roadmap

### Phase 0 — Product decisions

Need final answers before coding deeply:

1. Is FRIDAY AI cloud-first, local-first, or hybrid?
2. Is login mandatory or optional?
3. Should provider model choice be visible, or only “Friday” assistant?
4. Should chat history sync to backend?
5. Is background `Hey Friday` required for MVP, or post-MVP?
6. Is Google Play distribution required, or side-loaded/private APK only?

### Phase 1 — Rebrand/build sanity

Tasks:

- Change app label to `FRIDAY AI`.
- Add FRIDAY launcher icon/logo.
- Decide package id.
- Verify debug build:
  - `./gradlew :app:compileDebugKotlin`
- Verify APK install path.

### Phase 2 — FRIDAY design system

Tasks:

- Add Poppins or choose Android font fallback.
- Add FRIDAY tokens:
  - yellow accent
  - light/dark tokens
  - surface/input/border tokens
- Add orb asset support.
- Build reusable Compose components:
  - `FridayOrb`
  - `FridayMicButton`
  - `FridayWaveformRing`
  - `FridayDrawer`
  - `FridayChatBubble`
  - `FridayInputBar`

### Phase 3 — Voice-first home

Tasks:

- New start screen after auth/setup: `FridayVoiceScreen`.
- State machine:
  - idle
  - listening
  - transcribing
  - thinking
  - speaking
  - done
  - error
- Connect existing `VoiceModelManager` / `HomeViewModel` functions.
- Support push-to-talk mic.
- Show transcript and answer.
- TTS readout.

### Phase 4 — Chat/history/drawer

Tasks:

- Build design chat screen.
- Build history screen backed by chat repository/backend.
- Build model selector drawer.
- Build theme toggle.
- Add profile/account area.

### Phase 5 — Backend integration

Tasks:

- Add FRIDAY backend API client.
- Add auth token store.
- Add conversation streaming.
- Add model catalog.
- Add memory/settings.
- Add error/retry/offline states.

### Phase 6 — Wake word

Tasks:

- Research/choose KWS engine.
- Implement foreground `Hey Friday` detection.
- Add settings toggle and privacy notice.
- Measure CPU/battery.
- Only then consider background foreground-service mode.

## 11. Migration/adaptation plan for existing source

### Keep

- Gradle/Kotlin/Compose baseline.
- Hilt DI.
- Voice recorder/player.
- Inference service if local/private mode remains.
- Model download/catalog if local mode remains.
- Encrypted storage if security product wants it.
- Chat repository, after simplification.

### Replace or hide

- Current ToolNeuron visual theme.
- Current advanced scaffold/nav for MVP user flow.
- HF explorer, plugin hub, server UI, image generation if not part of MVP.
- Existing intro/setup if it fights FRIDAY onboarding.

### Rebrand

- `app_name`: `FRIDAY AI`.
- Launcher icons.
- Package id decision.
- Strings/screens mentioning ToolNeuron.
- Credits/license notices retained somewhere if fork license requires.

### Caution

Existing repo has strong conventions from `CLAUDE.md`:

- HXS-only persisted storage.
- One root Scaffold.
- ViewModels under `com.dark.tool_neuron.viewmodel`.
- No plan docs under `Sources`.

If we fork heavily, update `Sources/CLAUDE.md` after architecture changes.

## 12. Risk matrix

| Risk | Severity | Notes | Mitigation |
|---|---:|---|---|
| Existing codebase too broad | Medium | Many features irrelevant to MVP | Hide advanced routes; create FRIDAY facade UX |
| Rebrand/package rename breaks native/proguard | Medium | Native libs and manifests may assume names | Do staged rebrand: app label first, package later |
| Wake word expectation too high | High | Android cannot be true system-level hotword without privileged integration | Phase wake word; communicate limitation |
| Background mic privacy/policy | High | Play Store/privacy risk | Foreground notification, explicit opt-in, local-only KWS |
| Cloud vs local architecture conflict | Medium | Source is offline-first; product may be cloud-agent-first | Hybrid architecture with clean backend client boundary |
| Design asset `orb.mp4` playback in Compose | Low-Medium | Need video render loop | Use ExoPlayer/Media3 or pre-rendered animation asset |
| Social login in design | Medium | Requires backend/OAuth setup | Decide auth scope before implementation |
| Model picker provider keys | High | Never ship provider keys in APK | Route cloud providers through backend |

## 13. Answer to core question

### Can we apply the design to existing open-source app?

Yes. Technically feasible and likely faster than rebuilding, because the existing app already has the hard native AI/voice infrastructure.

But do not “skin” it blindly. Build a new FRIDAY-first UX layer on top of existing services.

### Should we rebuild from scratch?

Only if FRIDAY AI is meant to be a thin cloud app with no on-device model, no private mode, no RAG, no local STT/TTS, and no advanced Android-native features.

Given user’s stated goal: Android AI agent, voice-first, possible `Hey Friday`, APK, backend planning — native Android is the right technology. Existing source is a strong foundation.

### If building from scratch, what technology?

Kotlin + Jetpack Compose.

Not React Native/Flutter for this product, because voice, wake word, local inference, foreground services, and low-level Android audio/runtime are native-heavy.

## 14. Immediate next steps

1. Confirm product mode:
   - cloud-first / local-first / hybrid
2. Confirm auth model:
   - mandatory account / optional login / guest mode
3. Confirm wake-word MVP scope:
   - push-to-talk only / foreground `Hey Friday` / background always-on
4. Decide package id:
   - `com.friday.ai`
   - `com.fridayos.ai`
   - other
5. Create implementation branch.
6. Run baseline build of existing source.
7. Implement Phase 1 rebrand + first FRIDAY voice screen spike.

Recommended first technical spike:

- Add app label `FRIDAY AI`.
- Add `FridayVoiceScreen` in Compose using design tokens and `orb.mp4`.
- Wire mic button to existing `VoiceModelManager` STT/TTS.
- Verify on emulator/device.

Success criteria:

- APK launches as `FRIDAY AI`.
- Home screen matches design direction.
- Tap mic records.
- STT returns transcript.
- Assistant response streams or mocked response renders.
- TTS speaks answer.

If this spike succeeds, continue adapting source. If it fails due architecture friction, reconsider clean native app using selected services extracted from ToolNeuron.
