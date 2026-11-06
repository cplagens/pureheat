---
name: pureheat-review
description: Code review of the PureHeat Android app for security, privacy, bugs, write safety, i18n, battery/data use, performance, accessibility and tests. Use when the user asks for a review/Prüfung/Code-Review, before larger commits or a release. Argument (optional) - "diff" (default - uncommitted + unpushed + since the last review), "full" (whole app), or a path/package.
---

# PureHeat review

Report to the user in German, concise. Do **not** change code during the review; offer fixes at the
end (the user decides). Keep token use low: work from diffs and Grep hits, read only the line
ranges you need, never whole translation files.

## 1. Scope
- Last reviewed commit: first line of `docs/REVIEW.md` (`last: <hash> <date>`), if present.
- `diff` (default): `git diff --stat <last>..HEAD` plus `git status --short`; review only changed
  hunks (`git diff <last> -- <file>`), plus the callers of changed public functions (Grep).
- `full`: Grep over `app/src/main`, `app/src/test`, manifest, Gradle files, `docs/`, and read the
  security-relevant files: `hmip/HmipClient.kt`, `data/HeatingRepository.kt`,
  `data/CredentialStore.kt`, `AndroidManifest.xml`, `app/build.gradle.kts`.
- Always run `scripts/check.sh` (build, unit tests, lint – short summary).

## 2. Checklist
Areas: **A** public repo & secrets · **B** security · **C** privacy · **D** write safety ·
**E** correctness · **F** UI text & languages · **G** battery & data · **H** performance ·
**I** accessibility · **J** tests & docs. The points per area are in
[checklist.md](checklist.md): with `diff` read only the sections that fit the changed files
(always A and D if code or docs changed); with `full` read all of it.

## 3. Report (German)
1. Scope (commits/files) and build/test/lint result in one line.
2. Findings sorted by severity – **Kritisch / Hoch / Mittel / Niedrig / Hinweis** – each with
   `file:line`, what is wrong, why it matters, suggested fix (one or two lines).
3. One line per checklist area without findings ("B Sicherheit: ok").
4. Ask which findings to fix.

## 4. Afterwards
Write `docs/REVIEW.md` (replace, keep it short): first line `last: <HEAD hash> <date>`, then the open
findings as a checklist. Fixed findings are removed in later reviews.
