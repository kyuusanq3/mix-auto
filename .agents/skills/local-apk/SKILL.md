---
name: local-apk
description: >-
  GitHub release / version bump / signed release APK for mix-auto (assembleRelease
  → mix-auto.apk → optional commit, push, GitHub Releases). Use when the user says
  "local-apk", "local-apk publish", "ship release", "publish to GitHub", "bump
  version and release", or asks to produce/ship a release APK. Do NOT use for
  debug builds, compile checks, or verify gates — those use
  powershell.exe -File "./scripts/verify-debug.ps1" (assembleDebug) via
  local-opencode-loop / local-map-triage.
---

# local-apk

**Purpose:** GitHub updates and releases — version bump, signed **release** APK, optional commit/push + GitHub Releases upload.

**Not for debug APKs.** Do **not** run this skill to compile-check a fix, validate a patch, or produce `app-debug.apk`. For that, use:

```bash
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1"
```

(`assembleDebug` → `BUILD SUCCESSFUL` / `MIXAUTO_VERIFY_DONE`). See `local-opencode-loop` and `local-map-triage`.

Only load **local-apk** after a verified fix when the user explicitly wants a **sideload release** or **GitHub publish**.

Builds a signed release APK (`assembleRelease`), copies it to `mix-auto.apk`, and drafts commit/release notes. When the user asks to **publish** ("local-apk publish", "build and publish", "commit and release"), also commits, pushes to `dev`, and uploads `mix-auto.apk` to GitHub Releases via `gh`.

---

## Modes

| Trigger | Steps |
|---------|-------|
| "local-apk", "build the release apk", "build and sign" | Steps 1–6 only (release build + artifacts) |
| "local-apk publish", "build and publish", "commit and release", "ship to GitHub" | Steps 1–7 (release + commit + push + GitHub release) |

**Never** use `assembleDebug` or `verify-debug.ps1` inside this skill — release only (`assembleRelease`).
**Never commit or push** unless the user explicitly requested publish in the same message.
**Never** run during diagnosis / planning — wait for an explicit release/publish request after verify.

---

## Step 1 — Summarize what changed (local diff on dev)

Run the following from **repo root** to collect local uncommitted changes and recent commits on the `dev` branch:

```powershell
git diff --stat
git diff --stat --cached
git log --oneline -10
```

- `git diff --stat` → unstaged local changes vs HEAD on dev
- `git diff --stat --cached` → staged local changes vs HEAD on dev
- `git log --oneline -10` → recent dev commits for context

Read the output carefully. Use it to:
- Understand the scope of changes (bug fixes, features, refactors, etc.)
- Draft the suggested commit message (see Step 6)
- Draft the user-facing release description (see Step 6) — translate code changes into what drivers will notice

---

## Step 2 — Determine the next version number

Read `app/build.gradle.kts` to get the current `versionCode` and `versionName`.

Always increment the **PATCH** component only (the last number), regardless of the size or nature of the changes. This project uses sequential patch increments for all releases until a deliberate major/minor milestone is declared by the user.

- `versionCode` → increment by 1
- `versionName` → increment the patch number by 1 (e.g. `"0.0.4"` → `"0.0.5"`)

Edit `app/build.gradle.kts` with the new values.

---

## Step 3 — Verify keystore exists

Check for `app/mixauto-release.jks`. If missing, generate it:

```powershell
if (-not $env:JAVA_HOME) {
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
keytool -genkeypair -v `
  -keystore app\mixauto-release.jks `
  -alias mixauto -keyalg RSA -keysize 2048 -validity 10000 `
  -storepass mixauto123 -keypass mixauto123 `
  -dname "CN=MixAuto, OU=Dev, O=kyuusanq3, L=Bacolod, ST=Negros Occidental, C=PH"
```

Signing config in `app/build.gradle.kts` (add inside `android {}` if absent):

```kotlin
signingConfigs {
    create("release") {
        storeFile = file("mixauto-release.jks")
        storePassword = "mixauto123"
        keyAlias = "mixauto"
        keyPassword = "mixauto123"
    }
}
buildTypes {
    release {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

---

## Step 4 — Build release (not debug)

```powershell
if (-not $env:JAVA_HOME) {
  $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
}
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat assembleRelease
```

Allow at least 5 minutes for this build to complete. Expected output: `BUILD SUCCESSFUL`.

**Wrong task:** `assembleDebug` / `./scripts/verify-debug.ps1` — those are the compile/verify gate, not this skill.

**Gradle done = done:** treat `BUILD SUCCESSFUL` or `BUILD FAILED` as command finished. If the agent UI still shows Running / `esc interrupt` after that line, do not wait or re-run — proceed from the exit code.

---

## Step 5 — Copy to project root

```powershell
Copy-Item "app\build\outputs\apk\release\app-release.apk" "mix-auto.apk" -Force
```

The file at the project root is always named `mix-auto.apk` (no version suffix).

---

## Step 6 — Generate release artifacts

After a successful build, output ALL of the following to the user in a single formatted block:

### Suggested commit message

Write **one brief sentence** that summarizes the local uncommitted changes on `dev` — not a generic version-bump line.

Rules:
- Derive from `git diff --stat`, `git diff --stat --cached`, and `git log --oneline -10` (Step 1), not from raw commit subjects alone
- One sentence only; plain English; focus on user-visible or release-worthy changes
- Prefer conventional-commit prefix when obvious (`feat:`, `fix:`, `chore:`); otherwise a plain sentence is fine
- Do **not** use `chore: bump version to …` unless the diff contains nothing but version metadata

Examples:
```
feat: add TomTom traffic overlay, off-route rerouting, and in-app update check
```
```
fix navigation camera twitching and route line rendering on raster tiles
```

### Next version number

State the new `versionName` and `versionCode` explicitly.

### Release title

Format:
```
Mix Auto v{versionName}
```

### Release description

Write a **user-facing** changelog for people sideloading the APK on a head unit or phone — not a developer changelog. Describe what they will notice when using Mix Auto, not how the code changed.

**Tone and language**
- Plain, friendly English — like release notes you'd read in an app store
- Focus on behavior: what works better, what's new, what was fixed from a driver's perspective
- One short sentence per bullet; group related items into a single bullet when it reads naturally
- Omit sections with nothing to say

**Do NOT include**
- File names, class names, composables, APIs, or library names (e.g. MapLibre, OSRM, Compose, ViewModel)
- Implementation details (refactors, lifecycle, coroutines, git/branch metadata)
- Raw commit subjects or diff stat lines
- Jargon like "overlay composable", "SharedPreferences", "intent-filter", "bbox query"

**Prefer user-visible wording**

| Instead of… | Write something like… |
|-------------|----------------------|
| Fix MapLibreEngineImpl route layer z-order | Route line stays visible on the map while navigating |
| Add LauncherPreferences.show3dBuildings toggle | Optional 3D buildings on vector maps (Settings) |
| Wire SpeechRecognizer to KEYCODE_VOICE_ASSIST | Voice search in destination search (hardware mic button) |
| Refactor SettingsContent Surface color | Settings text is readable on dark background |

Use this structure (omit empty sections):

```
## What's new in v{versionName}

### New
- <what users can do now that they couldn't before>

### Better
- <quality-of-life or polish they'll notice>

### Fixed
- <problems that no longer happen>

---
Install: download `mix-auto.apk` and sideload over the previous version.
```

Derive content from Step 1 (`git diff`, staged diff, recent commits) — translate technical changes into the experience above. If the diff is only a version bump with no other changes, keep the description short (e.g. "Maintenance release — same features as the previous build with updated version metadata." is fine; do not invent user-facing changes).

If **not** publishing, stop here and remind the user they can ask to publish to ship to GitHub.

---

## Step 7 — Publish to GitHub (publish mode only)

Run only when the user explicitly requested publish in the same message.

### Prerequisites — GitHub auth (Windows)

The publish script needs GitHub credentials. **Pick one:**

**Option A — GitHub CLI (recommended)**

```powershell
winget install --id GitHub.cli -e --accept-source-agreements --accept-package-agreements
```

Open a **new terminal** after install (PATH refresh), then log in once:

```powershell
gh auth login
# GitHub.com → HTTPS → Login with browser (or paste token)
gh auth status
```

The publish script auto-adds `C:\Program Files\GitHub CLI` to PATH if `gh` is not found.

**Option B — Personal access token (no gh login)**

Create a classic PAT at https://github.com/settings/tokens with **`repo`** scope, then set it for the session:

```powershell
$env:GITHUB_TOKEN = "ghp_xxxxxxxx"
```

The script falls back to the GitHub REST API when `gh` is missing or not logged in but `GITHUB_TOKEN` is set.

### Other prerequisites

- Current branch is `dev` (default release branch for this project)
- Steps 1–5 completed; `mix-auto.apk` exists at project root
- GitHub release tag **must equal** `versionName` from Step 2 (e.g. `0.0.19`) — `AppUpdateRepository` compares `tag_name` to `BuildConfig.VERSION_NAME`
- Uploaded asset **must** be named `mix-auto.apk` — in-app download URL is `releases/latest/download/mix-auto.apk`

### 7a — Write release notes to a temp file

Write the Step 6 release description (user-facing markdown only) to a temp file. Do **not** commit this file.

```powershell
$notesPath = Join-Path $env:TEMP "mix-auto-release-notes.md"
# Write the release description content to $notesPath (UTF-8)
```

### 7b — Run the publish script

Use the commit message and version from Step 6:

```powershell
& ".\.opencode\skills\local-apk\scripts\publish-release.ps1" `
  -Version "{versionName}" `
  -CommitMessage "{commit message from Step 6}" `
  -NotesFile $notesPath
```

The script will:
1. `git add -A` (excluding `mix-auto.apk` and keystore via gitignore + explicit reset)
2. `git commit` with the provided message (skip if nothing to commit)
3. `git push origin dev`
4. Create GitHub release + upload `mix-auto.apk` via `gh` (if logged in) or GitHub REST API (if `GITHUB_TOKEN` is set)

Optional draft release (user must ask explicitly):

```powershell
& ".\.opencode\skills\local-apk\scripts\publish-release.ps1" `
  -Version "{versionName}" `
  -CommitMessage "{commit message}" `
  -NotesFile $notesPath `
  -Draft
```

### 7c — Verify

If `gh` is available:

```powershell
gh release view {versionName} --repo kyuusanq3/mix-auto
```

Otherwise open https://github.com/kyuusanq3/mix-auto/releases/tag/{versionName} in a browser.

Confirm the release lists `mix-auto.apk` as an asset. Report the download URL to the user:

```
https://github.com/kyuusanq3/mix-auto/releases/latest/download/mix-auto.apk
```

### Publish failures

| Error | Action |
|-------|--------|
| `gh` not recognized | Run `winget install --id GitHub.cli -e`, open a new terminal |
| `gh auth status` fails | Run `gh auth login`, or set `$env:GITHUB_TOKEN` with a PAT (`repo` scope) |
| Tag already exists | Delete/recreate release on GitHub, or upload with `gh release upload {version} mix-auto.apk --clobber` |
| `git push` rejected | Pull/rebase on `dev`, resolve conflicts, retry push before release |
| Nothing to commit but version bump missing | Ensure Step 2 edited `app/build.gradle.kts` before build |

---

## Notes

- **Not a debug skill.** Compile/verify = `./scripts/verify-debug.ps1` / bash-safe `powershell.exe -File "./scripts/verify-debug.ps1"` (`assembleDebug`). This skill = `assembleRelease` + GitHub release workflow only.
- `JAVA_HOME` must point to Android Studio's embedded JRE (`jbr`). The environment variable persists across chained shell calls in a session.
- Keystore credentials are stored in plaintext in `build.gradle.kts` — acceptable for a personal/sideload release; not for Play Store publishing.
- `mix-auto.apk` and `app/mixauto-release.jks` are in `.gitignore` and must never be committed.
- Always run Step 1 before bumping the version — the local diff on `dev` (`git diff` + `git diff --cached`) informs the commit message and release description; version bumps are always PATCH increments.
- Release descriptions are for end users sideloading the APK — avoid technical/code terminology in that block; the commit message can stay developer-oriented.
- Publish script path: `.opencode/skills/local-apk/scripts/publish-release.ps1` (project-scoped, ported from the original Cursor skill at `~/.cursor/skills/local-apk/scripts/publish-release.ps1`).
