# ADR 0014: Hardcode UI Strings in Kotlin Without Resource Localization

- Status: Accepted — review target
- Date: 2026-03-14

- Diagnostic-language clauses superseded by [0019](0019-english-diagnostics-and-failed-meal-deletion.md); other decisions remain in force.

## Context

Android convention is to declare user-facing text in `res/values/strings.xml` and translate it through alternative resource directories. In this repository every UI string is a Kotlin string literal instead, and `res/values/strings.xml` contains only `app_name`.

The diagnostic report feature was later added by a Japanese-speaking maintainer for a Japanese-speaking support workflow, so its strings are Japanese while the rest of the UI stayed English.

## Decision

Keep user-facing strings as Kotlin string literals and do not use string resources for UI text.

- `res/values/strings.xml` contains only `app_name`.
- The UI chrome is English: for example `Add Meal`, `Save & Complete`, `Analysis failed`, `No Meals Yet`.
- The diagnostic report flow is Japanese: `問題を報告`, `解析に使用した写真と診断データを添付します`, `診断データを作成しています…`, and the error messages and mail body in `AnalysisReportService` and `DiagnosticReportSharer`.
- There are no locale resource directories and no in-app language choice.

## Consequences

- The app ships only as it is written; a non-English user cannot switch the UI language and the chrome is not translated.
- The two-language mix is visible to users in one screen: the report button and its notice are Japanese while the surrounding card is English.
- No string can be verified, reused, or counted without grepping Kotlin; there is no lint guarantee that a string is translatable later.
- Several strings are written into `HomeViewModel` fields such as `healthConnectMessage` and never rendered, so grepping string literals does not reveal what the user can actually see.
- Marked a review target: moving to resources and choosing one UI language is a deliberate decision to make later, and this record should be superseded rather than edited.
