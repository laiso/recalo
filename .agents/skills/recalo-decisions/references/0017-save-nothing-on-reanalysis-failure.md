# ADR 0017: Save Nothing When a Re-analysis Fails

- Status: Superseded by [0018](0018-defer-reanalysis-until-failure-evidence.md)
- Date: 2026-09-18

## Context

Re-analyzing a meal that already has a valid result creates a case the current code has never had: a `completed` meal whose replacement analysis fails.

Two verified behaviours make reusing the existing analysis path unsafe:

- The failure branch of `MealRepository.analyzeMeal` writes `analysisStatus = error` and a stored error code onto the meal. The nutrition rows survive, but the app then treats the meal as having no usable result: the card shows `Analysis failed` with no values, the detail screen refuses to open, and the meal becomes reportable as `ANALYSIS_ERROR`.
- `MealDao.replaceAnalysisResult` runs inside `@Transaction`, so its delete-then-insert is atomic and is **not** itself a data-loss risk. The damage comes from the status overwrite, not from the replace.

Decision 0006 defines `error` as the state of a meal whose analysis never produced a number, and states that the persisted status vocabulary is a contract whose codes are additive rather than renamed.

## Decision

Treat re-analysis of a `completed` meal as a replacement, not as error recovery. When it fails, persist nothing.

- Analyze first. Leave stored nutrition, `analysisStatus`, and `analysisError` untouched until the new result has been parsed and validated.
- On success, replace the nutrition result atomically under the same meal id.
- On failure, write nothing at all. The meal keeps its previous nutrition, its `completed` status, and its stored error field exactly as they were.
- Report the failure as a transient message on the screen that started the re-analysis. Do not persist the message, and do not turn the card into an error card.
- Do not retain a diagnostic record for a failed re-analysis. The meal is `completed` with non-zero values, so `AnalysisReportability` returns `NotReportable` and `persistDiagnosticSession` already discards the session. This is intended, not an oversight.
- Add no new persisted status. `error` keeps its current meaning.
- Guard against concurrent re-analysis with a single-claim update that mirrors `beginAnalysisRetry`, rather than widening that statement's `error`-only predicate.

## Consequences

- The failure branch of `analyzeMeal` cannot be reused as it stands, because it writes `analysisStatus = error` and a stored code. The analysis path must distinguish "first analysis" from "replacement of an existing result", since the two need opposite failure behaviour on the meal row.
- Because nothing is persisted, a failed re-analysis leaves no trace once the screen is left. The day list will not show that an attempt happened.
- The only evidence of a failed re-analysis is the transient message and logcat.
- A failed re-analysis is deliberately not reportable, which is an exception to the purpose of `docs/specs/diagnostic-report.md`. This is a conscious trade: reporting would require a retained record and a reportable meal state, and this decision preserves neither, so the user cannot send this particular failure. Do not extend the reportability predicate to cover it without superseding this record.
- Retry semantics are unchanged: `error` meals keep using `beginAnalysisRetry`, and `completed` meals are never retried through that path.
- Because the cost of getting the ordering wrong is the silent loss of a visible result, a JVM test must cover "re-analysis fails, previous values and status survive" rather than relying on review.
- `docs/specs/meal-analysis.md`, `meal-detail.md`, and `meal-capture.md` need updating when the feature is implemented, together with the `while a meal's status is completed, the app shall not offer retry` requirement in `meal-analysis.md`.
