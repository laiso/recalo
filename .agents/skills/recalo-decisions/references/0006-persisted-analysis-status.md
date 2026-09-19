# ADR 0006: Model Analysis as a Persisted Status with Stable Error Codes

- Status: Accepted
- Date: 2026-07-15

## Context

Analysis is an asynchronous, network-dependent operation that can fail for many reasons: an invalid key, a rate limit, an inaccessible model, a provider outage, a timeout, a connection failure, a missing local image, or an unreadable response.

Before this change, a failed analysis was indistinguishable from a valid result with no nutrition data, and a day containing only failed meals displayed a total of `0 kcal`. A user could not tell "the model says this meal has no calories" from "the analysis never produced a number", and there was no way to recover without re-capturing the photo.

## Decision

Make the analysis outcome an explicit persisted state on the meal, and classify every failure into a stable, stored code.

- `analysisStatus` is one of `pending`, `analyzing`, `completed`, `error`. A meal row is inserted as `analyzing` before the request is made.
- `analysisError` stores the name of an `AnalysisErrorCode`, never raw provider text, HTTP status numbers, exception class names, or stack traces.
- The code set is `AUTH_INVALID`, `RATE_LIMITED`, `MODEL_UNAVAILABLE`, `SERVICE_UNAVAILABLE`, `TIMEOUT`, `NETWORK_UNAVAILABLE`, `IMAGE_UNAVAILABLE`, `INVALID_RESPONSE`, `UNKNOWN`, and each carries a user-facing message and a retryable flag.
- Only `AUTH_INVALID` and `IMAGE_UNAVAILABLE` are non-retryable; the rest offer `Try again`.
- An unrecognized stored value is read as `UNKNOWN`.
- Retry keeps the original meal id, sets the status back to `analyzing`, clears the error, and re-analyzes the stored image. It never creates a second meal row.
- On success, nutrition data is replaced atomically so a partially written result cannot be observed.

## Consequences

- A failed meal is a first-class card state with a title, a mapped guidance message, and a conditional retry action, so a `0 kcal` card can no longer be mistaken for a valid result.
- The stored vocabulary is a contract: renaming a code changes persisted data and the presentation mapping, so codes are additive rather than renamed.
- Users can act on `AUTH_INVALID` and `RATE_LIMITED` themselves, while `MODEL_UNAVAILABLE` points at model access (decision 0004).
- Because the failure is persisted, diagnostic records can be attached to it after the fact (decision 0010).
- Retry re-sends the same stored JPEG, so it cannot recover detail that was discarded at capture time (decision 0005).
- A day containing only failed meals still totals `0 kcal`; the summary does not yet distinguish "no data" from a real zero.
