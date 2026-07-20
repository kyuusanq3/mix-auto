---
name: local-compact
description: Saves the current session conversation to a structured markdown file in C:\dev\skills\session-history\{repo}\, then updates project-scoped rules in AGENTS.md or global rules in .gemini/config/AGENTS.md with key learnings from the session. Use when the user says "compact", "save session", "save this chat", "update rules", or "local-compact".
---

# local-compact

Persist the current session for cross-agent reuse, then distill durable learnings into project or global rules in AGENTS.md.

## When to run

Run this skill when the user asks to compact, save, or archive the session, or to update agent rules from what was learned.

## Workflow checklist

```
Task Progress:
- [ ] Step 1: Identify repo
- [ ] Step 2: Build save path
- [ ] Step 3: Write session file
- [ ] Step 4: Update agent rules
- [ ] Step 5: Report results
```

---

## Step 1: Identify repo

Determine the repo slug from the active workspace:

1. Run `git remote get-url origin` in the workspace root.
2. Extract the repo name from the URL (strip `.git`, take the last path segment).
   - `git@github.com:user/ai-chat-stash.git` → `ai-chat-stash`
   - `https://github.com/user/ai-chat-stash` → `ai-chat-stash`
3. If git is unavailable, derive from the workspace folder name (e.g. `c:\dev\proj\ai-chat-stash` → `ai-chat-stash`).

Record: repo slug, full workspace path, and (if available) git remote URL.

---

## Step 2: Build save path

**Base directory:** `C:/dev/skills/session-history/{repo-slug}/`

Create the directory if it does not exist.

**Timestamp:** local time, format `YYYY-MM-DD_HHMM` (e.g. `2026-06-13_0623`).

**Title slug:** 5–6 word kebab-case summary of the session topic.
- Lowercase, hyphens only, no special characters.
- Example: `local-compact-skill-creation` from "Create local-compact skill for session archiving"

**Filename:** `{title-slug}_{timestamp}.md`

**Full path example:**
`C:/dev/skills/session-history/ai-chat-stash/local-compact-skill-creation_2026-06-13_0623.md`

---

## Step 3: Write session file

Write the file using the template below. Source content from the current conversation context.

### Content rules

- Include every user message and assistant reply in order.
- Summarize tool calls and long outputs — do not paste raw tool JSON or full file dumps.
- For code changes, note the file path and a one-line description of what changed.
- Preserve decisions, rationale, and unresolved questions.
- Keep the file readable by any future AI agent with no prior context.

### Session file template

```markdown
# {Session Title}

## Metadata

| Field | Value |
|-------|-------|
| Date | {YYYY-MM-DD HH:MM local} |
| Repo | {repo-slug} |
| Workspace | {full workspace path} |
| Remote | {git remote URL or "n/a"} |
| Saved by | local-compact skill |

## Summary

{2–4 sentence overview of what this session accomplished.}

## Key outcomes

- {Outcome 1}
- {Outcome 2}

## Conversation

### User
{First user message}

### Assistant
{First assistant reply}

### User
{Next user message}

...

## Files touched

| File | Change |
|------|--------|
| {path} | {brief description} |

## Open items

- {Unresolved question or follow-up, or "None"}
```

Write the file with the file-write tool. Confirm the path exists after writing.

---

## Step 4: Update agent rules

Review the session for durable learnings worth persisting in agent rules.

### What qualifies

- New architectural patterns or contracts discovered
- Bugs fixed and their root cause
- Environment or tooling gotchas (PM2 cwd, path anchoring, proxy config, etc.)
- API or schema conventions established
- Repeated mistakes to avoid

Skip ephemeral details: one-off debugging steps, transient errors, or content already in rules.

### Where to write

Write to `AGENTS.md` at either the project workspace root or the global customizations directory.
- If the learning is specific to the current project/workspace, append to the project-level `AGENTS.md` (e.g. `c:\dev\proj\mix-auto\AGENTS.md` or equivalent workspace root).
- If the learning is general or applies universally to all tasks, append to the global `AGENTS.md` in `C:\Users\caleb\.gemini\config\AGENTS.md`.

### How to update

1. Read the target `AGENTS.md` first.
2. Append under a `## Lessons learned` or existing equivalent section at the end of the file (create it if it does not exist).
3. Use bullet points — one insight per bullet, include file paths when relevant.
4. Do not duplicate content already present.
5. Keep additions concise.

---

## Step 5: Report results

Tell the user:

1. **Session saved:** full path to the markdown file
2. **Rules updated:** which `.mdc` / `AGENTS.md` files changed and a brief summary of what was added
3. **Skipped:** anything intentionally not persisted and why

Example:

```
Session saved: C:/dev/skills/session-history/ai-chat-stash/local-compact-skill-creation_2026-06-13_0623.md
Rules updated: C:\Users\caleb\.gemini\config\AGENTS.md — added note about personal skills junction path
```

---

## Reading saved sessions

Any agent can load prior context by reading files from:

`C:/dev/skills/session-history/{repo-slug}/`

List the directory to find recent sessions, then read the relevant `.md` file.

---

## Path reference

| Purpose | Path |
|---------|------|
| Skill (canonical, Cursor) | `C:/dev/skills/local-compact/SKILL.md` |
| Skill (this copy, OpenCode, project-scoped) | `.opencode/skills/local-compact/SKILL.md` |
| Gemini discovery | `~/.gemini/config/skills/local-compact/` (junction) |
| Session archive | `C:/dev/skills/session-history/{repo-slug}/` |
| Project rules | `{workspace}/AGENTS.md` |
| Global rules | `C:/Users/caleb/.gemini/config/AGENTS.md` |
