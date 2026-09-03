# AfrozVoiceBubble

A 100% standalone, lightweight, **offline** Android app. It runs a draggable animated floating bubble and uses an Accessibility Service to read on-screen text / subtitles aloud in Hindi, and to diagnose + speak common Termux/Linux terminal errors in Hindi.

> **No INTERNET permission.** Everything runs locally on-device. 100% private.

## Features

- **Draggable Animated Floating Bubble** (SYSTEM_ALERT_WINDOW) that floats on screen edges.
  - Tap the bubble to toggle the screen reader ON/OFF.
  - Drag it; it snaps to the nearest screen edge.
- **Accessibility Screen Reader** — reads visible on-screen text & subtitles from any app (YouTube, browser, terminal) and speaks a Hindi translation via offline TTS.
- **Termux / Linux Error Helper** — while inside Termux, detects 30+ common errors (apt, permission, no space, npm, pip, git, memory, etc.) and shows + speaks the fix in clear Hindi.
- **Offline Hindi TTS** (hi-IN), falling back to the default device voice.
- **On-device STT** via `SpeechRecognizer` (prefers offline speech data; no network ever requested).

## Permissions (minimal)

| Permission | Why |
|---|---|
| `SYSTEM_ALERT_WINDOW` | To draw the floating bubble over other apps. |
| `RECORD_AUDIO` | Microphone for speech recognition. |
| `BIND_ACCESSIBILITY_SERVICE` | Required by the Android system to bind the Accessibility Service. |

There is **no INTERNET permission** in the manifest.

## Build

Requires JDK 17 and Android SDK (compileSdk 33).

```bash
./gradlew assembleDebug
```

The APK is produced at `app/build/outputs/apk/debug/`.

## GitHub Actions CI/CD

A workflow at `.github/workflows/build.yml` automatically builds the debug APK on every push to `main`. The APK is uploaded as a build artifact (Actions → run → Artifacts).

## Push to a new GitHub repository

This project intentionally uses a **fresh, separate repo** and never touches the old one.

### Option A — Create the repo on GitHub first (recommended)

1. On github.com create a new **empty** repository, e.g. `AfrozVoiceBubble` (do NOT add a README/.gitignore — this project already has them).
2. From this project directory run:

```bash
cd /storage/emulated/0/1AfrozVoiceBubble
git init
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/AfrozVoiceBubble.git
git add -A
git commit -m "Initial commit: AfrozVoiceBubble offline screen reader & Termux error helper"
# push (use a PAT or gh auth):
git push -u origin main
```

The GitHub Actions workflow will then build the APK automatically.

### Option B — Create and push with the GitHub CLI

```bash
cd /storage/emulated/0/1AfrozVoiceBubble
gh repo create AfrozVoiceBubble --public --source=. --remote=origin --push
```

## Setup on your Android device

1. Install the debug APK (from GitHub Actions artifacts).
2. Open the app → grant **"Display over other apps"** permission when prompted.
3. In the app, tap **"एक्सेसिबिलिटी (Accessibility) सेटिंग खोलें"** and enable **AfrozVoiceBubble**.
4. Tap **"बबल चालू करें"** to show the floating bubble.
5. Tap the bubble to start/stop the on-screen reader. Open Termux to trigger the error helper.
