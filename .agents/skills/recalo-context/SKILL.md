---
name: recalo-context
description: Locate Recalo's current specifications and non-obvious cross-cutting constraints before changes that span capture, analysis, storage, Health Connect sync, diagnostics, settings, or the CI boundary. Do not use for isolated implementation work whose behavior is clear from code and tests.
whenToUse: Use when a change touches more than one of capture, analysis, storage, Health Connect, diagnostics, or settings; when adding or changing user-visible behavior; or when you need to know what CI actually verifies.
---

# Recalo context

Use this skill to find context that cannot be recovered reliably from a single source file. Inspect code and tests for current structure rather than maintaining a duplicate architecture map.

## Sources of truth

1. Code and tests define implemented behavior.
2. `docs/specs/` defines current feature contracts, including the acceptance criteria that only manual or device checks can verify.
3. `recalo-decisions` preserves historical intent and tradeoffs; it does not define current behavior.

When these disagree, update the applicable specification with the implementation and tests. Do not rewrite history in an accepted decision record.

## Read by change area

- Camera or gallery capture, runtime permissions, or image compression: read `docs/specs/meal-capture.md`.
- Retaining a source image, cropping the saved image, or re-analyzing from a higher-resolution source: read decision 0016.
- Re-analyzing a meal that already has a result, or changing what a failed analysis does to a completed meal: read decision 0018 (0017 is superseded).
- Analysis run, failure card, error codes, retry, or model fallback: read `docs/specs/meal-analysis.md`.
- Result screen, portion ratio, save, or discard: read `docs/specs/analysis-result.md`.
- Day list, day boundary, daily totals, empty state, or previous-meal search: read `docs/specs/daily-summary.md`.
- Meal detail, header timestamp, fullscreen photo, or delete: read `docs/specs/meal-detail.md`.
- API key entry, model quality, or credential storage: read `docs/specs/settings-and-credentials.md`.
- Health Connect availability, permission, write, read, or delete: read `docs/specs/health-connect-sync.md`.
- The Report a problem action, diagnostic archive contents, or the share sheet: read `docs/specs/diagnostic-report.md`.
- Why the API key is encrypted rather than proxied: decision 0002.
- Why analysis has statuses and stable error codes: decision 0006.
- Why images are compressed to a 1280 px JPEG: decision 0005.
- Why the day starts at 05:00: decision 0007.
- Why Health Connect records are keyed by meal id: decision 0009.
- Why CI runs only JVM tests: decision 0012.
- Before adding a settings screen or a new screen: decisions 0013 and 0014.

## Cross-cutting constraints

These are routing aids, not the authoritative statement. Where a constraint below has a decision record, read that record before relying on this summary; the record is what a change must not contradict.

- `apps/android` is the only application. All UI lives in `ui/screens/HomeScreen.kt` behind a four-state `ScreenState` (`IDLE`, `ANALYZING`, `RESULT`, `DETAIL`); there is no navigation library. Adding a screen means adding a state, not a route.
- Only the JVM/Robolectric suite runs in CI (`testDevDebugUnitTest`). Anything under `src/androidTest` needs a device and never runs automatically, so it is manual evidence rather than a gate. A green CI run does not cover the UI, migrations, or Health Connect.
- A meal row is inserted with `analysisStatus = analyzing` before the API call. Analysis failure is a persisted state, not only an exception, and retry reuses the same meal id.
- Only the compressed JPEG is kept. No original may be retained for privacy and storage reasons (decision 0016), so reanalysis has to ask the user for a new photo; detail discarded at capture time is unrecoverable.
- Provider secrets live in EncryptedSharedPreferences plus the Android Keystore, and plaintext fallback exists if encrypted preferences cannot be created. Nothing is sent to a server this project operates and there is no telemetry; diagnostic data leaves the device only when the user sends it.
- User-facing strings are hardcoded in Kotlin; `strings.xml` holds only `app_name`. The UI chrome and diagnostic report flow are English (decision 0019).
- Room schema changes need a new `Migration` and a database version bump. Migrations 1→2 and 2→3 exist and no test exercises them.
- The day boundary is computed once per composition with `remember`, so a session left open across 05:00 keeps the previous "today".

Run `./gradlew testDevDebugUnitTest` from `apps/android` for analysis, storage, or diagnostics changes. `apps/android/docs/TESTING.md` documents lint, ktlint, and single-test invocations.
