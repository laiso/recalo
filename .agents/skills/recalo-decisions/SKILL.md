---
name: recalo-decisions
description: Consult Recalo's historical architectural decisions when a change affects a listed decision area. Do not use for routine implementation that does not touch those areas.
whenToUse: Use when a change would contradict an existing constraint, when asked why something is built the way it is, or when one of the listed decision areas is involved.
---

# Recalo decisions

Use this skill only to recover the reasoning behind an architectural or operational constraint. The current behavior is defined by code, tests, and `docs/specs/`; decision records explain how and why it was chosen.

Read only the reference whose area overlaps the task. Do not load every decision, and do not treat superseded decisions as current requirements.

## Decision index

| Order | Date | Status | Decision | Read when |
| ---: | --- | --- | --- | --- |
| 0001 | 2026-03-14 | Accepted | [Keep meal history on the device with no application backend](references/0001-no-application-backend.md) | Adding a server, sync, account, analytics, or any outbound call that is not a provider API |
| 0002 | 2026-03-14 | Accepted | [Require users to bring their own OpenAI key, encrypted at rest](references/0002-bring-your-own-key.md) | Changing credential storage, key entry, or the settings screen |
| 0003 | 2026-03-14 | Accepted | [Estimate nutrition with a hosted vision model](references/0003-hosted-vision-analysis.md) | Considering on-device inference, a different provider, or a change to the request shape |
| 0004 | 2026-03-19 | Accepted | [Offer three gpt-5.4 tiers with automatic fallback](references/0004-model-tiers-and-fallback.md) | Changing model ids, the quality options, or the fallback rule |
| 0005 | 2026-06-29 | Accepted | [Compress and downsample meal images before saving and upload](references/0005-compress-meal-images.md) | Changing image size, format, quality, storage path, or reanalysis from the original |
| 0006 | 2026-07-15 | Accepted | [Model analysis as a persisted status with stable error codes](references/0006-persisted-analysis-status.md) | Changing analysis failure handling, retry, error codes, or the error card |
| 0007 | 2026-03-25 | Accepted | [Fix the daily boundary at 05:00 and stop exposing it as a setting](references/0007-fixed-day-boundary.md) | Changing day grouping, daily totals, or adding a day-start setting |
| 0008 | 2026-03-25 | Accepted | [Show the recording time, not the photo time, in the meal detail header](references/0008-recording-time-in-detail.md) | Changing the detail header, grouping dates, or EXIF handling |
| 0009 | 2026-06-29 | Accepted | [Write Health Connect records keyed by the meal id](references/0009-health-connect-client-record-id.md) | Changing Health Connect writes, updates, or duplicate handling |
| 0010 | 2026-09-18 | Accepted | [Ship diagnostics as a user-sent ZIP instead of telemetry](references/0010-user-sent-diagnostics.md) | Adding crash reporting, analytics, or changing the report flow |
| 0011 | 2026-06-29 | Accepted | [Distribute unsigned APKs through GitHub Releases](references/0011-apk-distribution.md) | Changing release packaging, signing, versioning, or store distribution |
| 0012 | 2026-03-14 | Accepted | [Keep CI on JVM tests and leave device checks manual](references/0012-jvm-only-ci.md) | Changing the CI test command, adding an emulator job, or claiming CI verifies UI behavior |
| 0013 | 2026-03-14 | Accepted — review target | [Keep all screens in one Compose file with state-driven navigation](references/0013-single-compose-file.md) | Adding or splitting screens, or introducing a navigation library |
| 0014 | 2026-03-14 | Accepted — review target | [Hardcode UI strings in Kotlin without resource localization](references/0014-no-string-resources.md) | Adding or changing user-facing text, or introducing localization |
| 0015 | 2026-03-14 | Accepted — known debt | [Keep legacy `caroli` identifiers after the Recalo rename](references/0015-legacy-caroli-identifiers.md) | Touching database, preference, keystore, or theme names |
| 0016 | 2026-09-18 | Accepted | [Do not retain original meal images](references/0016-do-not-retain-original-images.md) | Retaining a full-resolution copy, storing a second source file, cropping the saved image, or re-analyzing from a higher-resolution source |
| 0017 | 2026-09-18 | Superseded by 0018 | [Save nothing when a re-analysis fails](references/0017-save-nothing-on-reanalysis-failure.md) | Changing what a failed analysis does to a meal that already has a result, or reusing the failure branch for re-analysis |
| 0018 | 2026-09-19 | Accepted | [Defer re-analysis until failure evidence is reviewed](references/0018-defer-reanalysis-until-failure-evidence.md) | Planning recovery from all-zero completed results or deciding replacement-attempt diagnostics |
| 0019 | 2026-09-19 | Accepted | [Use English diagnostics and allow failed-meal deletion](references/0019-english-diagnostics-and-failed-meal-deletion.md) | Changing diagnostic language or failed-meal deletion |

Each reference states the context, the decision, and the consequences that are still binding. `Review target` marks an accepted decision that the maintainer intends to revisit; `known debt` marks an accepted decision that is an acknowledged oversight rather than an intent; `Proposed` marks a recommendation that is not yet binding and must not be treated as current behavior.

When a decision changes, add a new numbered record instead of rewriting its rationale. Mark the earlier entry `Superseded` and link both records.
