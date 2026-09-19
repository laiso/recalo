# Technical specifications

Specifications define current behavior, external contracts, and observable acceptance criteria for one feature or technical capability. They are working documents for agents changing that area, not a general architecture guide.

This repository's CI runs JVM tests only (decision 0012), so a specification is the only place where behavior that needs a device, a real account, or a human eye is written down. Each specification therefore ends with an `Automation status` section separating what tests already enforce from what only a manual check can confirm.

| Area | Specification | Acceptance format | Automated by CI |
| --- | --- | --- | --- |
| Adding a meal | [Meal capture](meal-capture.md) | EARS requirements and Gherkin scenarios | Image compression and history duplication only |
| Analysis run and failure | [Meal analysis](meal-analysis.md) | EARS requirements and Gherkin scenarios | Error classification, retry, and fallback request count |
| Result screen | [Analysis result](analysis-result.md) | EARS requirements and Gherkin scenarios | Portion-ratio arithmetic only |
| Day list and totals | [Daily summary](daily-summary.md) | EARS requirements and Gherkin scenarios | Search SQL and duplication landing only |
| Meal detail | [Meal detail](meal-detail.md) | EARS requirements and Gherkin scenarios | Cascade deletion only |
| Credentials and model quality | [Settings and credentials](settings-and-credentials.md) | EARS requirements and Gherkin scenarios | Nothing (Keystore test is device-only) |
| Google Health Connect | [Health Connect sync](health-connect-sync.md) | EARS requirements and Gherkin scenarios | Nothing (device-only tests are weak evidence) |
| Problem reports | [Diagnostic report](diagnostic-report.md) | EARS requirements and Gherkin scenarios | Archive, retention, redaction, and share intent |

Add a specification only when code and tests alone do not communicate the contract clearly. Historical decisions are supporting context, not current requirements; see `recalo-decisions`.

## Known deviations

Each specification lists the places where the code currently does not satisfy its own requirements. Those sections are not requirements: they record work that a change should either fix or promote into the contract deliberately.

## Investigations

Open questions and measurement records that are not yet a contract live in `docs/investigations/`. When an investigation concludes, either promote its outcome into a specification and a decision record, or record why no change was made.
