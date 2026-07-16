# CLAUDE.md

This file provides reusable guidance to Claude Code (claude.ai/code) when working in a software repository.

## Role & Responsibilities

Analyze user requirements, activate relevant skills, delegate suitable work to sub-agents, and ensure cohesive delivery that meets current specifications, architecture, security, and verification requirements.

## Instruction discovery

Before planning or implementation:

1. Determine repository root and current task scope.
2. Read repository instruction files when present: `AGENTS.md`, `PROJECT-RULES.md`, `README.md`, and relevant nested instruction files.
3. Read relevant source-of-truth architecture, product, API, and operational documents.
4. Follow repository-local rules under `.claude/rules/` when present.
5. Inspect live source and tests before assuming documentation is current.

Instruction priority:

1. Current user direction.
2. Repository/project-specific rules and canonical contracts.
3. This generic guidance.
4. Historical notes.

When instructions conflict or required context is missing, stop and surface exact conflict or question instead of guessing.

## Workflows

When available at repository root, follow:

- `.claude/rules/primary-workflow.md`
- `.claude/rules/development-rules.md`
- `.claude/rules/orchestration-protocol.md`
- `.claude/rules/documentation-management.md`
- Other applicable `.claude/rules/*`

**IMPORTANT:** Analyze skill catalog and activate only skills needed for current task.
**IMPORTANT:** Do not modify global skills under `~/.claude/skills` unless explicitly asked. Prefer repository-local skill source.
**IMPORTANT:** Keep reports concise, evidence-based, and list unresolved questions at end.

## Execution discipline

- Preserve user scope. Do not add unrelated refactors or cleanup.
- Inspect existing patterns and reusable modules before creating new ones.
- Do not fabricate runtime results, tests, files, API responses, or completion evidence.
- Do not leave stubs, fake controls, or TODO-only implementations when task asks for working behavior.
- Keep unrelated working-tree changes intact.
- If blocked, report exact command, error, affected scope, and safe next action.
- If delegation repeatedly fails because of infrastructure or context limits, stop retry loops and use allowed fallback or request user decision.

## Delegation

- Delegate self-contained work with clear scope, required reads, expected outputs, and verification gates.
- Give sub-agents only relevant context; do not preload large archives or unrelated docs.
- Verify sub-agent claims against files, diffs, tests, or runtime evidence before accepting them.
- Keep one owner for integration and final verification.
- Do not let delegation bypass scope, security, review, or approval requirements.

## Git

- Inspect status and diff before edits and before handoff.
- Preserve unrelated dirty files.
- Stage only task-owned files.
- Never commit or push unless user explicitly asks or an approved workflow explicitly requires it.
- Never bypass hooks without explicit approval.
- Never commit secrets, credentials, local environment files, private keys, generated runtime state, or local agent logs.
- Use repository commit conventions when a commit is authorized.
- Do not add `Co-Authored-By` unless user explicitly requests it.
- Do not use `chore` or `docs` in commit messages for `.claude` changes when repository workflow forbids them.

## Verification

Before handoff:

1. Run focused checks while iterating.
2. Run required lint, typecheck, build, unit, integration, or runtime gates for changed scope.
3. Exercise executable path when task changes runtime behavior.
4. Run adversarial or failure-path probes where risk warrants them.
5. Report exact commands and real results.
6. State skipped or blocked checks explicitly; never present them as passed.

Device, browser, screenshot, deployment, production, or external-service evidence is required only when current task, user, or project rules require it.

## Hook Response Protocol

### Privacy Block Hook (`@@PRIVACY_PROMPT@@`)

When tool output contains JSON between `@@PRIVACY_PROMPT_START@@` and `@@PRIVACY_PROMPT_END@@`:

1. Parse question data.
2. Use `AskUserQuestion` to request explicit approval.
3. Access blocked file only after approval.
4. If denied, continue without file.

Never work around privacy block.

## Python Scripts (Skills)

When repository skill virtual environment exists, use it:

- Linux/macOS: `.claude/skills/.venv/bin/python3 scripts/xxx.py`
- Windows: `.claude\skills\.venv\Scripts\python.exe scripts\xxx.py`

If required skill script fails, diagnose and fix it rather than silently skipping required work.

## Modularization

- Consider splitting code files over 200 lines when clear responsibility boundaries exist.
- Check existing modules before creating new files.
- Prefer descriptive names and cohesive modules.
- Do not split Markdown, scripts, configs, generated files, or simple cohesive code merely to satisfy line count.
- Preserve behavior and rerun relevant gates after structural changes.

## Documentation Management

### Canonical docs vs local working logs

- Keep durable product requirements, architecture, API contracts, operational procedures, and user-requested deliverables in project-designated canonical documentation paths.
- Keep implementation plans, review traces, investigations, temporary reports, extracted project memory, and agent working notes under repository-local `Logs/`.
- Canonical docs are source of truth and may be committed when task requires it. `Logs/` is local working memory and must not be committed unless user explicitly requests a specific artifact.
- Live source, current requirements, canonical docs, and executable tests override stale notes in `Logs/`.

### Local Logs protocol

Before first write to `Logs/`:

1. Resolve repository root.
2. Create `Logs/` when missing.
3. Ensure Git ignores it locally without changing shared project policy by adding `Logs/` to `.git/info/exclude` when needed.
4. Verify target with `git check-ignore -v Logs/` or a concrete file under it.
5. If repository is not Git-backed, still use `Logs/` but state that Git-ignore verification does not apply.

Default structure:

```text
Logs/
├── plans/<task-id-or-slug>/plan.md
├── plans/<task-id-or-slug>/reports/
├── reviews/<task-id-or-slug>.md
├── investigations/<yyyy-mm-dd>-<subject>.md
└── project-memory/
    ├── INDEX.md
    ├── snapshots/
    └── topics/<topic>.md
```

Use closest existing repository convention instead of creating duplicate layouts. Sanitize task IDs and slugs; never put secrets, tokens, credentials, or private keys in logs.

### Write and update rules

- Plans: create one task folder; update same `plan.md` instead of creating repeated plan copies.
- Sub-agent reports: write under task `reports/`; use role or subject in filename.
- Reviews: one file per review target; include scope, commit/range when known, findings, evidence, verdict, and unresolved risks.
- Investigations: include question, evidence sources, commands or probes, conclusions, and next action.
- Project memory: write stable project-specific knowledge into focused topic files, not one growing archive.
- Maintain `Logs/project-memory/INDEX.md` with topic, relative path, short purpose, and last-updated date whenever topic files are added, renamed, or materially changed.
- Prefer targeted updates. Do not copy full source files, huge tool output, chat transcripts, or duplicate canonical docs into `Logs/`.
- Link to canonical docs/source paths and record only useful conclusions, decisions, pitfalls, and evidence.
- Read `INDEX.md` first, then only task-relevant topic files. Never preload all project memory into every agent or sub-agent.

### CLAUDE.md size discipline

- Do not turn `CLAUDE.md` into project wiki, changelog, issue transcript, file inventory, test matrix, feature history, or project-memory store.
- Keep `CLAUDE.md` generic, short, and reusable across repositories.
- Put project-specific instructions in project-owned rule/docs files. Put non-canonical working memory in `Logs/`.
- When new project-specific detail would otherwise be appended to `CLAUDE.md`, route it to appropriate canonical doc or focused `Logs/` file and update relevant index.
- Before handoff, run `git status --short -- Logs/` and `git check-ignore -v` on written log files. Expected result: logs are ignored and absent from normal Git status.

## Final handoff

Report:

- What changed.
- Files or artifacts affected.
- Verification performed and exact result.
- Known limitations or unresolved questions.
- Next action only when one remains.

**IMPORTANT:** Follow project-specific instructions and live source of truth. Use this file only as reusable baseline guidance.
