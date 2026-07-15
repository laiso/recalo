# Meal Analysis Failure and Retry Specification

## User Value

When meal analysis fails, users can distinguish the failure from a valid `0 kcal` result, understand what action to take, and retry recoverable failures without creating a duplicate meal.

## Specification Overview

Meal analysis has three persisted states:

| State | `analysisStatus` | `analysisError` | Nutrition result |
|---|---|---|---|
| Analysis in progress | `analyzing` | `null` | Not available |
| Analysis completed | `completed` | `null` | Available |
| Analysis failed | `error` | Stable error code | Not available |

The existing nullable `analysisError` field stores a stable error-code name. Raw provider responses, API keys, exception class names, and stack traces are not displayed to users or persisted in this field.

## Failure Classification

| Condition | Stored code | User guidance | Retry available |
|---|---|---|---|
| HTTP 401 | `AUTH_INVALID` | Check the OpenAI API key in Settings | No |
| HTTP 429 | `RATE_LIMITED` | Try again later | Yes |
| HTTP 403 or 404, including fallback failure | `MODEL_UNAVAILABLE` | The selected AI model is unavailable | Yes |
| HTTP 5xx | `SERVICE_UNAVAILABLE` | The AI service is temporarily unavailable | Yes |
| HTTP 408 or socket timeout | `TIMEOUT` | The analysis took too long | Yes |
| DNS, connection, or I/O failure | `NETWORK_UNAVAILABLE` | Check the internet connection | Yes |
| Saved image is missing | `IMAGE_UNAVAILABLE` | The saved meal image is no longer available | No |
| Invalid JSON response | `INVALID_RESPONSE` | The analysis result could not be read | Yes |
| Any unclassified failure | `UNKNOWN` | The meal could not be analyzed | Yes |

## User Interface Rules

### Failed Meal Card

For a meal whose `analysisStatus` is `error`:

- Display `Analysis failed` as the card title.
- Display the user guidance mapped from the stored error code.
- Do not display calories or nutrient values on the card.
- Do not open the meal detail screen when the card is tapped.
- Keep the meal image available for fullscreen preview.
- Display `Try again` only when the error classification is retryable.

Legacy or unknown values in `analysisError` are treated as `UNKNOWN`.

### Daily Summary

Failed meals have no nutrition result and therefore contribute zero to the daily nutrient totals. In the current implementation, a day containing only failed meals can still display a daily total of `0 kcal`. Replacing that summary with an unavailable-data state is outside the scope of this specification.

## Retry Behavior

Retry uses the existing meal and stored image:

1. Keep the original meal ID.
2. Set `analysisStatus` to `analyzing`.
3. Clear `analysisError`.
4. Analyze the stored image using the currently configured API key and model.
5. On success, save nutrition data and set the state to `completed`.
6. On failure, set the state back to `error` with the newly classified error code.

Retry must not create a second meal record.

## Error Persistence Rules

- API failures returned as a failed result must update the meal to `error`.
- Exceptions thrown after the meal has been created must also attempt to update the meal to `error`.
- Failures that occur before a meal record is created, such as an unreadable source image, cannot produce a failed meal card.
- Successful retry clears the previous error code.

## Testability and CI

The nutrition analyzer is provided through an interface so production uses the OpenAI service while tests can use a controlled local HTTP server.

The CI-safe end-to-end test covers the following application path:

```text
test image
  -> image compression and storage
  -> HTTP request returning 500
  -> SERVICE_UNAVAILABLE classification
  -> Room error persistence
  -> user-facing error presentation
  -> retry with HTTP 200
  -> nutrition persistence
```

The test must verify:

- The first analysis fails and stores `error` with `SERVICE_UNAVAILABLE`.
- No nutrition result exists after failure.
- The user-facing message does not expose HTTP status `500`.
- The error is marked retryable.
- Retry succeeds with the same meal ID.
- Exactly one meal exists after retry.
- The successful nutrition result contains the expected calories.
- The completed meal has no stored analysis error.
- Exactly two HTTP requests were made.

The test runs under `testDevDebugUnitTest` with Robolectric, MockWebServer, and an in-memory Room database. It does not require a live OpenAI API key, external network access, or an Android emulator.

## Out of Scope

- Emulator-driven Compose UI interaction tests
- Support or correlation IDs
- Persisting HTTP status, provider request ID, model name, or retry count in separate database columns
- Automatic retry or retry backoff
- Changing the daily summary from `0 kcal` to an unavailable-data state
