# /local-opencode — mix-auto

| Field | Value |
|-------|--------|
| `project_id` | `mix-auto` |
| `prefer_model` | `ollama/qwen3-coder-14b-24k` |
| `temp_dir` | `%TEMP%\mix-auto-opencode\` |
| `verify_cmd` | `powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1"` |
| `verify_done` | `MIXAUTO_VERIFY_DONE` |
| `verify_notes` | Gradle may sit silent for minutes — ScriptOnly idle **300s**. After test / Turn A edits add `-UnitTestCompile`. Stale `UP-TO-DATE` after a non-empty owner diff → force `:app:compileDebugKotlin --rerun-tasks`. Never `&&`; never `.\scripts\` under Git Bash. |
| `opencode_skills_edit_once` | `local-opencode-loop`; map bugs → `local-map-triage`; map features → `local-map-feature` (Turns A→B→C, one turn per run) |
| `forbid_skills_script_first` | Edit, Write, triage/feature/apk, pitch tools (unless `-AllowPitchTools`) |
| `default_apply` | `script-first` for multi-hunk / Compose / EOL; `edit-once` only for narrow body fills |
| `owners_hint` | See `AGENTS.md` / `llms.txt`; map puck/camera → map-engine owners |

## Verify (shell-only step 3)

```
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "./scripts/verify-debug.ps1"
Expect BUILD SUCCESSFUL then MIXAUTO_VERIFY_DONE exit=0. Gradle may take several minutes; wait.
```

Optional: same command + `-UnitTestCompile` when unit tests / Turn A are in scope.

## Notes for conductor

- `recommend-model.ps1 -TaskClass <class> -Project mix-auto`
- Product loop + package map: `AGENTS.md` — do not duplicate here.
