# ADR 0019: Use English Diagnostics and Allow Failed-Meal Deletion

- Status: Accepted
- Date: 2026-09-19
- Supersedes the diagnostic-language clauses of [0010](0010-user-sent-diagnostics.md) and [0014](0014-no-string-resources.md); their other decisions remain in force.

## Context

Diagnostic actions introduced Japanese labels into an otherwise English interface.
The maintainer requested that this inconsistency be fixed. Failed meals cannot
open the detail screen, but deletion was only offered there, trapping unwanted
failed records in the day list.

## Decision

- Use English for diagnostic action labels, notices, progress, errors, and the
  prefilled report subject and body. Keep the existing hardcoded-string approach.
- Offer `Delete` on every failed meal card, including non-retryable failures.
  Require confirmation and allow cancellation before invoking the existing meal
  deletion path. Keep failed-meal detail navigation disabled.
- Preserve the existing manual ZIP sharing, recipient, reportability rules,
  secret redaction, and diagnostic retention policy.

## Consequences

- The diagnostic flow now matches the rest of the interface. No localization
  framework or locale-dependent UI is introduced.
- Failed meals can be removed without an API key or a successful analysis.
  Existing local cleanup and permission-dependent Health Connect deletion apply.
- Device tests cover cancellation and confirmation for retryable and
  non-retryable failures, along with the English report action.
