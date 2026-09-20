# NullFlow - Git Instructions

## Repo
- GitHub: https://github.com/codezmr/nullflow (public)
- Local: /home/codezmr/APK_Dev/nullflow
- Branch: master (tracks origin/master)
- Original source (do not use): /home/codezmr/AI/find_somthing_new/3Sep2026_app_freez

## Auth
- `gh` CLI authenticated as `codezmr` (scopes: repo, workflow, admin:org)
- Token stored in ~/.git-credentials (credential.helper=store)
- Git identity: codezmr <mdzamiruddin.zmr@gmail.com>

## Conventions
- Commit style: short imperative subject, e.g. "Add Focus Telemetry Console - live interception data"
- Update SESSION_STATE.md when committing feature work (existing pattern in history)
- Never commit: local.properties, .gradle/, build/, *.apk, *.aab, .weave/ (already in .gitignore)
- Push with: git push (tracking already set)

## HARD RULES (Zamir)
- **NEVER work on or touch the `master` branch.** All work happens on feature
  branches (e.g. `dev` or `feature/<name>`), branched off `master`.
- **ALWAYS ask Zamir before any `git commit` or `git push`.** No autonomous
  commits or pushes - wait for explicit go-ahead.
- **ALWAYS create an MR/PR and hand the link to Zamir. NEVER merge it yourself.**
  Zamir reviews and merges.

## Common commands
- New feature: `git checkout -b feature/<name>` (from master), work, commit (after asking), `git push -u origin feature/<name>` (after asking)
- MR: `gh pr create --fill` → give Zamir the PR URL, do NOT merge
- Check status: `gh pr list`, `gh run list`
