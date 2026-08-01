# Zed project notes (MixAuto)

Agent model/profile settings live in **user** Zed config (`%APPDATA%\Zed\settings.json`) — project `.zed/settings.json` cannot override most agent keys.

## Recommended Agent Panel choices

| Control | Value |
| ------- | ----- |
| Profile | **MixAuto Write** (fixes) or **MixAuto Ask** (plan-only) |
| Model | `mixauto-qwen3-coder:30b` (or `:14b`) |
| Slash | `/map-bug …` / `/map-plan …` / `/local-map-triage` |

**MixAuto Write** disables `list_directory` (Zed often returns empty for `.`, which derails local models).

Recreate Ollama tags after Modelfile edits:

```powershell
.\scripts\create-mixauto-ollama-models.ps1
```

See repo root `AGENTS.md` (Local agent cold start) and `tools/ollama/`.
