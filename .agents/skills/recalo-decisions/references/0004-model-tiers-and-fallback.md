# ADR 0004: Offer Three gpt-5.4 Tiers with Automatic Fallback

- Status: Accepted
- Date: 2026-03-19

## Context

OpenAI's gpt-5.4 series is not available to every project by default; access must be granted in the project's model limits. Users who selected a gpt-5.4 model but had not enabled it received HTTP 403 or 404, which looked like a generic failure and left the app unusable.

The cost difference between the tiers is large enough that a single fixed model is the wrong default: the cheapest tier is about two orders of magnitude below the most expensive per image.

## Decision

Expose three quality levels, each mapping to one gpt-5.4 model, and fall back automatically to `gpt-4o-mini` when the selected model is not accessible.

| Level | Label shown to the user | Description shown | Model id |
| --- | --- | --- | --- |
| `low` (default) | `Low (gpt-5.4-nano)` | `~$0.0002 / image` | `gpt-5.4-nano` |
| `medium` | `Medium (gpt-5.4-mini)` | `~$0.00075 / image` | `gpt-5.4-mini` |
| `high` | `High (gpt-5.4)` | `~$0.02 / image` | `gpt-5.4` |

The fallback triggers only on HTTP 403 or 404 from the primary request and retries once with `gpt-4o-mini`. On success the result is marked with `needsModelUpdateNotice`, which makes the result screen offer `Model Update Available` with `Open Settings`, linking to the OpenAI project settings page. If the fallback also fails, the error is classified from the fallback's status code.

An unrecognized level falls back to `low`.

## Consequences

- A user without gpt-5.4 access still gets an analysis, at the cost of a lower-quality model and an extra request per meal.
- The quality levels are the only user-tunable analysis parameter, and the label prices are hardcoded estimates that must be revised by hand when provider pricing changes.
- The fallback is not a retry policy for transient failures: 429, 5xx, and timeouts go straight to the failure path (decision 0006).
- Save and delete operations are unaffected by which model produced the result.
- Adding a provider or a fourth tier means changing `AiConfig` and the decision record, not the request shape (decision 0003).
