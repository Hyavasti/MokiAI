# Moki — On-Device Android AI Voice Assistant

![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Xiaomi%20HyperOS-3DDC84?style=flat-square&logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white)
![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)
![AI](https://img.shields.io/badge/On--Device%20AI-Gemma--3%20%7C%20LiteRT--LM-FF6F00?style=flat-square)
![Database](https://img.shields.io/badge/Storage-Room%20%26%20DataStore-009688?style=flat-square)
![Status](https://img.shields.io/badge/Status-Active%20Development-F59E0B?style=flat-square)

**Moki** is a native Android on-device AI voice assistant built with **Kotlin** and **Jetpack Compose** for **Xiaomi HyperOS**. Designed around a modular, tiered routing architecture, Moki executes device controls, dual-SIM messaging, and UI automation locally in milliseconds while running **Gemma-3 1B** on-device for offline natural conversation.

> **Project Status:** Active Development — Core voice pipeline, Tier 0 deterministic rules & actions, local Gemma-3 inference, and Accessibility UI automation are operational. Ongoing development focuses on native low-power wake-word detection, multi-step chained workflows, and broader system/app automation.

---

## Architecture Overview

Moki routes every voice or text turn through a three-tier pipeline (`MokiBrainRouter`) to minimize latency and preserve privacy:

1. **Tier 0 — Deterministic Rule & Action Engine (`< 5 ms`):**
   * Modular regex and natural-language rules (`brain/rules/`) match user intent and route directly to isolated, single-responsibility skill classes (`actions/`).
   * Handles hardware toggles, dual-SIM SMS, notes, navigation, and Messenger automation locally with zero model overhead.
2. **Tier 1 — On-Device LLM Inference:**
   * Runs **Gemma-3 1B** locally via **LiteRT-LM / Gemini Nano** (`LiteRtLmEngine` / `GeminiNanoEngine`) for conversational replies, general Q&A, and fallback tool routing—completely offline.
3. **Tier 2 — Optional Cloud Escalation:**
   * Configurable fallback (`CloudEscalationEngine`) for complex queries when explicitly enabled in settings.

---

## Project Structure

```text
com.example.moki/
├── MainActivity.kt                  # Entry point & ASSIST intent handler
├── MokiApplication.kt               # Singleton dependency container
├── actions/                         # Discrete, modular skill executors
│   ├── AlarmAction.kt
│   ├── AppLaunchAction.kt
│   ├── CalculatorAction.kt
│   ├── CallAction.kt
│   ├── DeviceStatusAction.kt
│   ├── DndAction.kt
│   ├── EmailAction.kt
│   ├── FlashlightAction.kt
│   ├── LockScreenAction.kt
│   ├── MessengerAction.kt
│   ├── NavigationAction.kt
│   ├── NotesAction.kt
│   ├── SmsAction.kt
│   ├── SmsReadAction.kt
│   └── TimerAction.kt
├── brain/                           # Tiered intent router & LLM engines
│   ├── CloudEscalationEngine.kt
│   ├── DeterministicMatch.kt
│   ├── DeterministicMatcher.kt
│   ├── GeminiNanoEngine.kt
│   ├── LiteRtLmEngine.kt
│   ├── MokiBrainRouter.kt
│   ├── MokiPersonaPrompt.kt
│   └── rules/                       # Modular DeterministicRule & helper classes
│       ├── ContactAliasResolver.kt
│       ├── DeterministicRule.kt
│       ├── MessageTextNormalizer.kt
│       ├── MessengerRule.kt
│       ├── NotesRule.kt
│       └── ParsedUtterance.kt
├── data/                            # Room persistence, DataStore & model worker
│   ├── local/                       # ChatMessage, Note, TraceLog DAOs & Entities
│   ├── model/                       # ModelDownloadWorker (WorkManager)
│   └── preferences/                 # MokiSettingsRepository
├── services/                        # Android system & Accessibility services
│   ├── MokiAccessibilityService.kt
│   ├── MokiCallScreeningService.kt
│   └── MokiVoiceInteractionService.kt
├── skills/                          # Structured JSON tool registry & sanitizer
├── ui/                              # MVVM ViewModel, Compose screens & components
└── voice/                           # Foreground wake service, STT & TTS pipeline
    ├── MokiTtsSpeaker.kt
    ├── MokiVoiceService.kt
    ├── SpeechCaptureManager.kt
    ├── SpeechWakeDetector.kt
    └── VoiceState.kt
```

---

## Current Capabilities

### Voice Pipeline & Conversation Control
* **Fuzzy Wake-Word Standby (`SpeechWakeDetector`):** Listens continuously in the background for `"Hey Moki"` and phonetic speech-to-text variants (`"Hey Maki"`, `"Timoki"`, `"Hi Moki"`), supporting both wake-then-command and inline commands (e.g., *"Hey Moki, turn on flashlight"*).
* **Confirmation Gate:** High-impact actions (SMS, phone calls, Messenger messages) pause for explicit confirmation via voice (`"yes"`, `"yes confirm"`, `"no"`, `"cancel"`) or the interactive `ActionConfirmCard`.
* **Instant Loop & Memory Reset:** Saying `"stop"` or `"go to sleep"` immediately returns Moki to passive standby without invoking the LLM; saying `"reset"` or `"reset chat"` (or tapping the top-bar reset icon) clears Room chat history and resets conversation context.
* **Custom TTS Tuning:** Configurable voice pitch and speech rate stored in DataStore.

### Messaging & Calls
* **Dual-SIM SMS (`SmsAction`):** Sends immediate text messages using the active default SIM via `SubscriptionManager`, with automatic pronoun normalization (e.g., *"text 09XX... tell him I'm home"* → *"I'm home"*).
* **Scheduled SMS:** Queues delayed text messages for a specified time (e.g., *"text [number] at 11:30 PM saying..."*).
* **Incoming SMS Reader (`SmsReadAction`):** Reads recent incoming text messages aloud (*"who texted me"*).
* **Facebook Messenger Automation (`MokiAccessibilityService` + `MessengerAction`):** Navigates Messenger via Accessibility nodes to search contacts, open the target chat thread, enter text, and initiate **Messenger audio or video calls** hands-free.
* **Contact Alias Resolution (`ContactAliasResolver`):** Maps nicknames and speech-to-text mishearings to canonical contact names for reliable targeting.

### Productivity, Navigation & Device Controls
* **Smart Notes (`NotesRule` + `NotesAction`):** Create new notes, append items to existing named notes, read saved notes aloud, and share/sync to the system Notes app with local Room persistence.
* **Google Maps Navigation (`NavigationAction`):** Launches turn-by-turn navigation directly to spoken destinations (*"navigate to SM Southmall"*).
* **Alarms, Timers, Calculator & Email:** Sets system alarms and timers, evaluates math expressions locally, and drafts emails via intents.
* **HyperOS & Hardware Controls:** Toggles the flashlight (`FlashlightAction`), switches Do Not Disturb / Modes access (`DndAction`), locks the screen (`LockScreenAction`), launches installed apps (`AppLaunchAction`), and reports live battery percentage and time (`DeviceStatusAction`).

---

## Setup & Installation

### Prerequisites
* **Android Studio** (Ladybug / Meerkat or newer) with JDK 17+
* Physical Android device running **Android 12+** (tested on **Xiaomi HyperOS** with dual-SIM)
* On-device **Gemma-3 1B** LiteRT-LM model binary (downloaded in-app via `ModelDownloadWorker` or pushed to app storage)

### Build & Run
1. **Clone the repository:**
   ```bash
   git clone [https://github.com/Hyavasti/Moki.git](https://github.com/Hyavasti/Moki.git)
   cd Moki
   ```
2. **Open in Android Studio:**
   * Select **File -> Open** and choose the `Moki` root folder.
   * Allow Gradle sync to complete.
3. **Configure Local Keys (Optional):**
   * Enter your **Gemini API Key** (for optional Tier 2 cloud escalation) or **Porcupine Access Key** inside the in-app **Settings** screen—no secrets are hardcoded in the repository.
4. **Deploy to Device & Grant Permissions:**
   * Run the `app` configuration on your Android device.
   * Open **Settings** inside Moki and grant:
     * **Microphone (`RECORD_AUDIO`)** for voice wake-word and command capture
     * **SMS & Phone (`SEND_SMS`, `READ_SMS`, `READ_PHONE_STATE`)** for dual-SIM messaging
     * **Notification Policy / Modes Access** for Do Not Disturb toggles
     * **Accessibility Service (`MokiAccessibilityService`)** for Messenger UI automation and screen lock
     * *(HyperOS Tip)* Enable **Autostart** and set **Battery Saver** to **No restrictions** so `MokiVoiceService` stays active in standby.

---

## Roadmap & Ongoing Development

* [ ] **Low-Power Native Wake-Word Engine:** Transition from continuous `SpeechRecognizer` polling to a dedicated native audio wake-word pipeline (Porcupine / on-device keyword spotting) to eliminate microphone indicator blinking in standby.
* [ ] **Multi-Step Compound Commands:** Chain multiple actions in a single utterance (e.g., checking route ETA on Maps and automatically messaging the contact).
* [ ] **Expanded Accessibility Automation:** Add support for additional messaging platforms, media playback controls, and system quick-settings toggles.
* [ ] **Richer Local Tool-Calling:** Strengthen structured JSON tool invocation (`MokiSkillRegistry` + `JsonSanitizer`) with Gemma-3 for conversational commands outside deterministic regex rules.
* [ ] **Trace Inspector & Diagnostics UI:** Expand `TraceLogDao` visualization for real-time Tier 0 / Tier 1 latency and routing inspection.
