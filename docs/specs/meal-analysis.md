# Meal analysis specification

## Scope

This document describes the current feature contract. Completed-meal re-analysis
is deferred by [decision 0018](../../.agents/skills/recalo-decisions/references/0018-defer-reanalysis-until-failure-evidence.md).
That decision does not add a recovery action or change current retry and
diagnostic retention rules. Reporting a completed all-zero meal remains supported.

## Requirements

### Lifecycle

- The app shall insert a meal row with `analysisStatus = analyzing` before making the provider request.
- When analysis succeeds, the app shall store the nutrition result, its items, and its nutrients, and shall set the status to `completed`.
- When analysis succeeds, the app shall replace any existing nutrition result atomically, so a partially saved result is never observable.
- If analysis fails after the meal row exists, then the app shall set the status to `error` and store a stable error code.
- If the image cannot be saved before the meal row is created, then the app shall fail without creating a failed meal card.
- The app shall not display a failed analysis as a valid `0 kcal` result.

### Network timeouts

- The provider client shall use a 10-second connection timeout and 60-second
  read and write timeouts. These are per-operation limits, not a deadline for
  the complete analysis; connection retries can extend the total wait.
- Diagnostic request settings shall record the connection, read, and write
  timeouts separately. The legacy `timeoutSeconds` field remains the read/write
  timeout for compatibility.

### Failure classification

- The app shall store exactly one of the following codes in `analysisError`, and shall store no raw provider response, HTTP status, exception class name, or stack trace.

| Condition | Stored code | User guidance | Retry offered |
| --- | --- | --- | --- |
| HTTP 401 | `AUTH_INVALID` | `OpenAI API key is invalid. Check it in Settings.` | No |
| HTTP 429 | `RATE_LIMITED` | `The analysis limit was reached. Please try again later.` | Yes |
| HTTP 403 or 404 | `MODEL_UNAVAILABLE`, or the code for the fallback's status when the fallback also fails | `The selected AI model is unavailable.` | Yes |
| HTTP 5xx | `SERVICE_UNAVAILABLE` | `The AI service is temporarily unavailable.` | Yes |
| HTTP 408 or socket timeout | `TIMEOUT` | `The analysis took too long. Please try again.` | Yes |
| DNS, connection, or I/O failure | `NETWORK_UNAVAILABLE` | `Check your internet connection and try again.` | Yes |
| Stored image missing | `IMAGE_UNAVAILABLE` | `The saved meal image is no longer available.` | No |
| Invalid JSON response | `INVALID_RESPONSE` | `The analysis result could not be read. Please try again.` | Yes |
| Any unclassified failure | `UNKNOWN` | `The meal could not be analyzed. Please try again.` | Yes |

- When the stored code is missing or unrecognized, the app shall present it as `UNKNOWN`.

### Model fallback

- When the primary request returns HTTP 403 or 404, the app shall retry once with `gpt-4o-mini` (decision 0004).
- When the fallback succeeds, the app shall mark the result so that the result screen offers `Model Update Available` with an `Open Settings` action linking to the OpenAI project settings page.
- When the fallback also fails, the app shall classify the failure from the fallback's status code.

### Failure card

- While a meal's status is `error`, the app shall show the card title `Analysis failed`, the guidance mapped from the stored code, and no calorie or nutrient values.
- While a meal's status is `error`, the app shall not open the meal detail screen when the card is tapped.
- While a meal's status is `error`, the app shall keep the meal image available for fullscreen preview.
- When the stored code is retryable, the app shall offer `Try again` on the card.

### Delete a failed meal

- Every error card shall offer `Delete`, including non-retryable failures.
- Deletion shall require a `Delete Meal` confirmation; canceling shall preserve the meal.
- Confirming shall use the existing meal deletion path to remove the meal and its
  associated local data, including diagnostic records, and attempt Health Connect
  deletion when permission is available. The user shall remain on the day list.

### Retry

- When the user retries, the app shall keep the original meal identifier and the stored image.
- When the user retries, the app shall set the status to `analyzing`, clear the stored error, and re-analyze using the currently configured key and model.
- When a retry succeeds, the app shall reuse the same meal and store a fresh nutrition result with no stored error.
- When a retry fails, the app shall store the newly classified code.
- The app shall not create a second meal row for a retry.
- If the stored image is gone, then the app shall fail the retry as `IMAGE_UNAVAILABLE` without making a provider request.
- While a meal's status is `completed`, the app shall not offer retry, because reanalysis of a completed meal is not part of this contract.

### Daily total interaction

- The app shall count a failed meal as zero in daily totals, and a day containing only failed meals may display `0 kcal`. Replacing that with an unavailable-data state is outside this specification.

## Acceptance scenarios

```gherkin
Feature: Meal analysis

  Background:
    Given an OpenAI API key is stored
    And the user has captured a meal photo

  Scenario: AT-ANALYSIS-001 — Recoverable failure and successful retry
    When the provider returns HTTP 500
    Then the meal status is error
    And the stored code is SERVICE_UNAVAILABLE
    And no nutrition result is stored
    And the card shows "Analysis failed"
    And the card shows "The AI service is temporarily unavailable."
    And the card does not show the HTTP status
    And "Try again" is offered

    When the user taps "Try again" and the provider returns a valid result
    Then the same meal identifier is used
    And exactly one meal exists
    And the status is completed
    And no analysis error is stored
    And "Try again" is no longer offered

  Scenario: AT-ANALYSIS-002 — Model access falls back automatically
    Given the selected model is not accessible to the project
    When the provider returns HTTP 404 for the primary model
    Then the app retries once with gpt-4o-mini
    And exactly two requests are made
    And the result screen shows "Model Update Available"
    And "Open Settings" opens the OpenAI project settings page

  Scenario: AT-ANALYSIS-003 — Non-retryable failure
    When the provider returns HTTP 401
    Then the stored code is AUTH_INVALID
    And the card shows "OpenAI API key is invalid. Check it in Settings."
    And "Try again" is not offered

  Scenario: AT-ANALYSIS-004 — Retry without the stored image
    Given the stored image file has been removed
    When the user taps "Try again"
    Then the stored code becomes IMAGE_UNAVAILABLE
    And no provider request is made
    And "Try again" is not offered

  Scenario: AT-ANALYSIS-005 — Remove a meal while its analysis is in progress
    Given a meal is stored with status analyzing
    When the meal is deleted before the analysis resolves
    Then the meal and its nutrition data are removed
    And no nutrition result remains

  Scenario: AT-ANALYSIS-006 — Delete a failed meal from its card
    Given a failed meal with a retryable or non-retryable error
    When the user taps "Delete" on the card and then "Cancel"
    Then the meal remains stored
    When the user taps "Delete" and confirms deletion
    Then the meal is removed from storage and the day list
```

## Automation status

- Instrumented: `DiagnosticReportUiTest.failedMealsCanBeDeletedAfterConfirmation` covers cancellation and deletion for retryable and non-retryable errors.

- Automated by JVM tests: the error-code mapping for 401, 404, 429, and 503 and for a missing image (`AnalysisErrorCodeTest`); the failure card presentation, the retry reusing the same meal id, exactly two requests, and the missing-image retry making no request (`AnalysisFailureE2ETest`); the fallback request count and 403 handling and the diagnostic recording of a 500 (`MealRepositoryDiagnosticsTest`); atomic replacement and the single-claim retry (`MealDaoTest`).
- Manual, device required: `AT-ANALYSIS-002` as a whole cannot be checked automatically, because the fallback notice and the settings link are UI-only and no test asserts the `needsModelUpdateNotice` flag.
- Not covered by any test: the `RATE_LIMITED` card rendering, the status colours, and every path that needs an actually inaccessible model on a real account.

## Known deviations

- The analyzing screen offers no cancel control, no progress, and no timeout.
- `uploadImage` returns early and leaves the analyzing state on screen when the key is empty; UI callers pre-check the key, so this is currently unreachable.
- `healthConnectMessage` and `SaveState` are written for analysis failures and never rendered, so an analysis failure is communicated only through the resulting card.
