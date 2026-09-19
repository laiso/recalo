# ADR 0010: Ship Diagnostics as a User-Sent ZIP Instead of Telemetry

- Status: Accepted
- Date: 2026-09-18

## Context

Users reported analyses that completed with every nutrition value at zero, and there was no way to tell whether the zeros came from the provider response, the read, or the save. Reproducing it against the live API did not work: repeated requests with the same photo returned non-zero values (see `docs/investigations/all-zero-analysis-investigation.md`).

The usual answer is crash reporting and analytics. With no backend (decision 0001) and a stated promise of no data collection, adding a third-party telemetry SDK would contradict the product.

## Decision

Let the user build a diagnostic archive on demand and send it themselves. Do not add crash reporting, analytics, or any automatic upload.

- A reportable meal is one whose status is `error`, or whose status is `completed` with no stored nutrition result or with every calorie and nutrient value zero. One shared predicate drives both the card button and whether records are retained.
- The action is `問題を報告`, with the notice `解析に使用した写真と診断データを添付します`.
- The archive contains `report.json`, `request.json`, `response.json`, `values.json`, and `image.jpg`, and is shared through the system share sheet as `application/zip` addressed to `support@lai.so`.
- The archive captures the exact image that was sent, the request configuration, the raw responses per attempt, and the database values at each stage, with explicit missing-data reasons instead of guessed values.
- Each analysis attempt gets its own diagnostic id linked to the meal id; a retry never overwrites the previous failure.
- Diagnostic records live in `noBackupFilesDir/analysis_diagnostics` with 7-day and 20-attempt retention, and are deleted with the meal. Archives are cleaned up on the next report once older than 24 hours.
- `SecretRedactor` removes the active key, bearer tokens, `sk-` shaped strings, and credential-shaped JSON fields. Raw provider responses are no longer written to the normal log.
- Opening the mail composer is never reported to the user as "sent".
- Diagnostic read and write failures are swallowed so they cannot change an analysis result.

## Consequences

- Diagnosis depends on the user choosing to report and on their mail app; nothing arrives without their action.
- The user's meal photo leaves the device again when they send the archive, which they see and confirm in their own mail client.
- Records are unavailable for meals analyzed before this feature existed; those reports are built from the stored image and database values only, with a note that the original response is unavailable.
- The interface only covers reportable meals, so a wrong-but-nonzero result is not diagnosable this way.
- Adding telemetry later requires a new decision record that supersedes this one and a change to `PRIVACY_POLICY.md`.
