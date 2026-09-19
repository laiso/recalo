# Diagnostic report specification

## Scope

This document describes the current feature contract. Completed-meal re-analysis
is deferred by [decision 0018](../../.agents/skills/recalo-decisions/references/0018-defer-reanalysis-until-failure-evidence.md).
That decision does not add a recovery action or change current retry and
diagnostic retention rules. Reporting a completed all-zero meal remains supported.

## Requirements

### Reportable conditions

- The app shall use one shared predicate for both the card action and whether a diagnostic record is retained.
- The app shall treat a meal as reportable when its status is `error`.
- The app shall treat a meal as reportable when its status is `completed` and no nutrition result is stored.
- The app shall treat a meal as reportable when its status is `completed` and every calorie and every nutrient amount is zero or missing.
- The app shall not treat a meal as reportable when any calorie or nutrient value is non-zero.
- The app shall not treat a meal as reportable while its status is `analyzing` or `pending`.
- The app shall keep a valid zero result, such as water, at status `completed` and shall not rewrite it into an error.
- The app shall treat partial zeros as not reportable.

### Action

- While a meal is reportable, the app shall show the action `Report a problem` with the notice `Attaches the photo used for analysis and diagnostic data` on the day-list card and on the result screen.
- The app shall not show the action on the meal detail screen.
- While a report is being prepared for a meal, the app shall show `Preparing diagnostic report…`, disable the action, and ignore further taps for that meal.
- The app shall never report to the user that a report has been sent; opening the mail composer is not sending.

### Archive

- When the user reports a meal, the app shall build a ZIP at `cacheDir/analysis_reports/Recalo-diagnostic-<safeId>.zip`, where `<safeId>` is the diagnostic identifier reduced to letters, digits, `-`, and `_`.
- The archive shall contain `report.json`, `request.json`, `response.json`, `values.json`, and `image.jpg` when the image is still available, in that order.
- `report.json` shall record the diagnostic and meal identifiers, timestamp, app and Android versions, requested and actual model, language, analysis status, HTTP status, request identifier, and missing-data reasons.
- `request.json` shall record the prompt, schema, analysis settings, and the image SHA-256, and shall not duplicate the image bytes.
- `response.json` shall record the raw provider response body per attempt, including the primary, fallback, and exception cases, redacted.
- `values.json` shall record the database values after load, after save, and at report time, including the portion-ratio multiplier.
- `image.jpg` shall be exactly the image that was sent for analysis.
- The app shall record explicit missing-data reasons such as `no_http_response`, `image_unavailable`, and `values_read_failed` instead of guessing values.
- The app shall report a meal analyzed before this feature existed from the stored image and current database values, with a note that the original response is unavailable.

### Share

- When the archive is built, the app shall open the Android share chooser with an `ACTION_SEND` intent of type `application/zip`.
- The intent shall set the recipient `support@lai.so`, the subject `Recalo meal analysis problem report`, a body containing the diagnostic identifier, meal identifier, and analysis time, and the archive as a `content://` FileProvider URI.
- The app shall grant read permission on the archive URI for that share only.
- The app shall not restrict the share to a specific package.
- If no app can handle the intent, then the app shall show `No app is available to share the report.`
- If opening the share target fails for any other reason, then the app shall show `Could not open the sharing app. Please try again later.`
- If preparing the report source fails, then the app shall show `Could not prepare the diagnostic report. Please try again later.`
- The app shall show report errors on the meal card that produced them, and shall clear the previous error when a new report starts.

### Retention

- The app shall retain diagnostic records under `noBackupFilesDir/analysis_diagnostics`, which is excluded from backup.
- The app shall keep only reportable attempts.
- The app shall retain records for 7 days and at most 20 attempts, removing the oldest first.
- When a meal is deleted, the app shall delete its diagnostic records.
- When a retry runs, the app shall create a new diagnostic identifier and shall not overwrite the previous failure.
- The app shall delete report archives older than 24 hours at the start of the next report, and shall not delete the archive immediately after sharing.
- The app shall clean up report work directories older than one hour.

### Secrets

- The app shall never include the active API key, authorization headers, full settings, or other meals in an archive.
- The app shall remove the active key, bearer tokens, `sk-` shaped strings, and credential-shaped JSON fields from responses, exceptions, archives, and logs.
- The app shall not write raw provider responses to the normal log, and shall log the diagnostic identifier and status instead.
- If a diagnostic read or write fails, then the app shall swallow the failure so it cannot change an analysis result.

## Acceptance scenarios

```gherkin
Feature: Diagnostic report

  Background:
    Given an OpenAI API key is stored

  Scenario: AT-REPORT-001 — Report a failed analysis through the share sheet
    Given a meal whose status is error
    When the user taps "Report a problem"
    Then the archive "Recalo-diagnostic-<id>.zip" is built in the cache directory
    And it contains report.json, request.json, response.json, values.json, and image.jpg
    And the share chooser opens for application/zip
    And the recipient is support@lai.so
    And the subject is "Recalo meal analysis problem report"
    And control of sending remains with the user

  Scenario: AT-REPORT-002 — Report an all-zero completed meal
    Given a meal whose status is completed with every value zero
    Then the meal is reportable
    And the action "Report a problem" is offered on its card

  Scenario: AT-REPORT-003 — A valid result is not reportable
    Given a meal whose status is completed with at least one non-zero value
    Then the meal is not reportable
    And no action is offered
    And no diagnostic record is retained

  Scenario: AT-REPORT-004 — No mail application
    Given no installed app can handle the share intent
    When the user reports a meal
    Then the card shows "No app is available to share the report."

  Scenario: AT-REPORT-005 — Retention
    Given more than 20 diagnostic records exist for the device
    When a new record is saved
    Then the oldest record is removed
    And records older than 7 days are removed

    Given a meal has diagnostic records
    When the user deletes the meal
    Then its diagnostic records are gone

  Scenario: AT-REPORT-006 — A retry does not overwrite the previous failure
    Given a meal has one failed attempt with a diagnostic record
    When the user retries and the retry also fails
    Then two diagnostic records exist for the meal
    And they have different diagnostic identifiers

  Scenario: AT-REPORT-007 — No credentials in the archive
    Given the provider response echoes the API key
    When the user reports a meal
    Then no entry in the archive contains the key
    And the key does not appear in the log

  Scenario: AT-REPORT-008 — A meal analyzed before this feature
    Given a meal with no diagnostic record
    When the user reports it
    Then the archive is built from the stored image and database values
    And it notes that the original response is unavailable

  Scenario: AT-REPORT-009 — Verify the report in a real mail app
    Given the share chooser is open on a device
    When the user selects their mail app
    Then the recipient, subject, and body are prefilled
    And the ZIP attachment is readable
```

## Automation status

- Automated by JVM tests: the reportability rules including all-zero, partial zero, missing values, and the states that are never reportable (`AnalysisReportabilityTest`); retention by count and age, deletion by meal, report-time values, and a save failure (`AnalysisDiagnosticsStoreTest`); archive entries, model and HTTP details, the legacy note, and secret redaction (`DiagnosticReportZipBuilderTest`); the share intent, chooser, FileProvider URI, no-mail-app error, and the legacy, expired, and missing-image paths (`AnalysisReportServiceTest`); key redaction and retry isolation end to end (`MealRepositoryDiagnosticsTest`).
- Automated by instrumented tests only, device required, never run in CI (decision 0012): the real share sheet opening with the archive name visible, driven through the app (`DiagnosticReportUiTest`).
- Manual, device required: `AT-REPORT-009`. The maintainer documented the real mail app check as a manual step, because no test asserts that a specific mail client prefills the recipient, subject, and body or can read the attachment.
- Not covered by any test: the report button rendering on the result screen and the day-list card as a user-visible flow, and the `Preparing diagnostic report…` progress state.

## Known deviations

- A ZIP build failure inside `prepareAndShare` surfaces the raw builder exception message, such as `Failed to create report cache directory`, instead of the localized `Could not prepare the diagnostic report. Please try again later.`
- `createReportZip` maps every failure to the localized message, but production never calls it; only tests do.
- `clearReportError` has no callers, so an error clears only when another report starts.
- `report.json` exists per attempt and `meta.json` is written to the source directory but is deliberately not included in the archive; nothing documents that omission for a reader of the archive alone.
