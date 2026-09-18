# Analysis Failure Diagnostic Report Specification

## User Value

When a meal analysis fails, or completes with no usable nutrition values, the
user can send a single diagnostic ZIP from the meal card. The ZIP contains the
exact image that was sent for analysis plus the request, the raw API responses,
and the database values at each stage, so support can tell where a value became
zero: the API response, the read, or the save.

Automatic sending, external crash collection, image adjustment, and automatic
retry are intentionally **not** part of this feature. The user reviews the mail
in their own mail app and sends it.

## Reportable Conditions

One shared predicate (`AnalysisReportability`) drives both the card button and
whether a diagnostic attempt is kept on disk.

| Condition | Reportable | Reason |
|---|---|---|
| `analysisStatus = error` | Yes | `ANALYSIS_ERROR` |
| `completed`, all total and per-item calories/nutrients zero | Yes | `ALL_ZERO_VALUES` |
| `completed`, no nutrition values stored | Yes | `MISSING_VALUES` |
| `completed`, at least one non-zero value | No | - |
| `analyzing` / `pending` | No | - |

A normal zero result (for example water) keeps its `completed` status; it is
never rewritten into an analysis error. Missing numeric fields deserialize to
zero and therefore fall into the all-zero case, which is reportable. Partial
zeros (some nutrients zero, others not) are never reportable.

## User Interface

For a reportable meal card:

- Show a **問題を報告** button next to the existing **Try again** button.
- Show the notice **解析に使用した写真と診断データを添付します** near the button.
- On failure to build the ZIP or to open a share target, show the error text on
  the card instead. Opening the mail composer is never reported as "sent".

The same action is offered on the result screen when a just-analysed meal is
all-zero, so the user does not have to save a meaningless result first.

## Send Flow

1. Build the report source directory (retained record, or an on-demand legacy
   report).
2. ZIP it into `cacheDir/analysis_reports/Recalo-diagnostic-<id>.zip`.
3. Share through the Android share sheet with the existing `FileProvider` and a
   temporary read grant for that ZIP only:
   - To: `support@lai.so`
   - Subject: `Recalo 食事解析の問題報告`
   - Body: diagnostic id, meal id, analysis time, and what is attached.

Archives are **not** deleted immediately after sharing; they are cleaned up on
the next report once they are older than 24 hours.

## Diagnostic Record

Each analysis attempt gets its own diagnostic id, linked to the meal id. A
retry starts a new id and never overwrites the previous failure.

| File | Content |
|---|---|
| `report.json` | Diagnostic id, timestamp, app and Android versions, requested/actual model, language, analysis status, HTTP status, request id, missing data reasons |
| `request.json` | Prompt, schema, analysis settings, and the image SHA-256. Image bytes are not duplicated |
| `response.json` | Raw API response body per attempt (primary, fallback, exception), redacted |
| `values.json` | Database values after load, after save, and at report time, including the portion-ratio multiplier |
| `image.jpg` | Exactly the image sent for analysis, when it is still available |

Records are stored under `context.noBackupFilesDir/analysis_diagnostics`, which
is excluded from backup. Only failed/reportable attempts are kept. Retention is
7 days and at most 20 attempts, oldest removed first. Deleting a meal deletes
its records.

### Missing data

Every stage is allowed to be missing. The records carry explicit reasons
(`no_http_response`, `image_unavailable`, `values_read_failed`, ...) instead of
guessed values. Meals analysed before this feature existed are reported from the
currently stored image and database values only, with a note that the original
response is unavailable.

### Secrets

The OpenAI key, authorization headers, full settings, and other meals are never
included. `SecretRedactor` removes the active key, bearer tokens, `sk-` keys,
and credential-shaped JSON fields from responses, exceptions, and logs. Raw API
responses are no longer written to the normal log; the service logs the
diagnostic id and status instead.

## Implementation Notes

- `NutritionAnalyzer.analyzeNutrition` takes an optional
  `AnalysisDiagnosticsRecorder`; the default is a no-op, so existing callers and
  test doubles are unaffected.
- `AnalysisDiagnosticsStore`, `DiagnosticReportZipBuilder`, and
  `DiagnosticReportSharer` are separate classes. No database schema change.
- Diagnostic read/write failures are swallowed so they can never change the
  analysis result.

## Tests

- Reportability: error, all-zero (including items), missing values, partial
  zeros, normal results, and water-like completions.
- Store: retention by count and age, deletion by meal, report-time values, and
  storage failure.
- ZIP: required entries, model/HTTP details, non-zero response versus zero saved
  value, legacy note, and secret redaction.
- Repository end-to-end: HTTP error, missing fields, explicit all-zero, normal
  result, partial zero, retry isolation, delete cascade, no-HTTP failure, and
  key leakage into response/logs.
- FileProvider URI and share-intent extras, plus a no-share-target error.

The real-device mail app check (attachment readable, to/subject/body filled) is
a manual verification step and is not covered by unit tests.
