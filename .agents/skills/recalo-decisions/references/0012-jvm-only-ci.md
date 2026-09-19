# ADR 0012: Keep CI on JVM Tests and Leave Device Checks Manual

- Status: Accepted
- Date: 2026-03-14

## Context

The valuable logic in this app — the analysis pipeline, Room persistence, retry and error classification, portion arithmetic, and diagnostic archive construction — is testable without a device. The parts that are not are exactly the parts that need Android itself: the camera app, the photo picker, runtime permission dialogs, the Android Keystore, Health Connect, and the share sheet.

Running those on CI would mean an emulator job. The tradeoff is a slower pipeline, hosted-runner flakiness, and a Health Connect environment that is difficult to provision and to keep permissioned. Choosing the JVM suite kept the pipeline fast and deterministic.

## Decision

CI runs the JVM/Robolectric suite only, and device-dependent verification stays a manual step.

- `android-ci.yml` runs `testDevDebugUnitTest` on push and pull requests to `main`, plus `lint` and `ktlintCheck`, both with `continue-on-error: true`.
- `android-delivery.yml` runs the same unit tests as a quality gate before building APKs (decision 0011).
- No workflow invokes `connectedAndroidTest`.
- JVM tests use Robolectric, MockWebServer, and in-memory Room so they need no network, no key, and no emulator.
- Instrumented tests under `src/androidTest` exist for Keystore round-trips, Health Connect, and the real share sheet, and are run by hand on a device.

## Consequences

- A green CI run says nothing about the UI, Room migrations, Health Connect, permissions, or sharing. Specifications must be the place where that gap is written down, which is why `docs/specs/` records acceptance scenarios that only a manual check can confirm.
- Because lint and ktlint cannot fail the build, style regressions pass CI and are enforced only by local discipline.
- Instrumented tests that self-skip when Health Connect is unavailable or permissions are missing report success while asserting nothing, so a manual run is only meaningful on a device that is actually provisioned.
- Some instrumented tests are currently weaker evidence than they appear: the permission test asserts nothing, and one repository regression test asserts only `assertTrue(true)`.
- Adding an emulator job would need a new decision record and would change what "CI is green" means for every other record here.
