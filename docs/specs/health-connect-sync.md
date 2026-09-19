# Health Connect sync specification

## Requirements

### Availability

- The app shall request exactly the write and read permissions for `NutritionRecord`.
- The manifest shall declare `android.permission.health.WRITE_NUTRITION` and `android.permission.health.READ_NUTRITION` and no other health permission.
- The app shall determine the provider package from the Health Connect settings intent, falling back to `com.google.android.apps.healthdata`.
- The app shall treat only `SDK_AVAILABLE` as available, so a provider that needs an update counts as unavailable.
- If Health Connect is unavailable, then a write shall fail, a delete shall fail, and a read shall return no records.
- The app shall declare a rationale activity reachable through the Health Connect rationale intent actions, showing the text `Health Connect access is needed to write nutrition results.`, `You can revoke access any time in Health Connect settings.`, and an `OK` control that finishes the activity.

### Permission

- When the user taps `Save & Complete` on the result screen and permissions are not granted, the app shall request them.
- When the permission request is granted in full, the app shall refresh the stored permission state.
- If the permission request is denied or only partly granted, then the app shall leave the stored permission state unchanged.
- While the stored permission state is false, the result screen shall show the indicator `Health Connect permissions required`.
- The app shall not re-check permissions when the app or screen resumes.

### Write

- When the user saves a result with permissions granted, the app shall write one `NutritionRecord` for the meal.
- The app shall write energy in kilocalories; protein, total carbohydrate, total fat, dietary fiber, and sugar in grams; and sodium in milligrams.
- The app shall identify the record with `clientRecordId = <meal id>` and `clientRecordVersion = 1`, and shall not call `updateRecords` (decision 0009).
- The app shall write the record over a one-second interval starting at the meal's `capturedAt`, falling back to `analysisCompletedAt` and then to the current time, using the system zone offset.
- The app shall match nutrient names case-insensitively against `protein`, `carbohydrates`/`carbs`, `fat`, `fiber`/`dietary fiber`, `sugar`/`sugars`, and `sodium`, using the first match.
- If a nutrient is absent, then the app shall write no value for that field.
- If the nutrition result is absent, then the app shall fail with `No nutrition result`.
- If calories are absent, then the app shall fail with `Missing calories in nutrition result.`

### Delete

- When the user deletes a meal and permissions are granted, the app shall delete the Health Connect record by `clientRecordId`.
- The app shall delete the local row before attempting the Health Connect deletion.
- If the Health Connect deletion fails, then the app shall not restore the local row and shall not report the failure to the user.

### Read

- The app shall read nutrition records over the 24 hours ending at the time of the call.
- If Health Connect is unavailable, then the read shall return an empty list and log a warning.
- The app shall use the read only as a post-write verification log, and shall not display its result.

## Acceptance scenarios

```gherkin
Feature: Health Connect sync

  Scenario: AT-HC-001 — Connect and write a meal
    Given Health Connect is available
    And the write and read permissions are not yet granted
    When the user saves an analyzed meal
    Then the Health Connect permission dialog is shown
    When the user grants both permissions
    Then the app records that permissions are granted
    And the result screen no longer shows "Health Connect permissions required"

  Scenario: AT-HC-002 — Deny the permission request
    Given Health Connect permissions are not granted
    When the user saves an analyzed meal
    And denies the permission request
    Then the result screen still shows "Health Connect permissions required"
    And no record is written
    And the meal remains stored locally

  Scenario: AT-HC-003 — Record contents
    Given a completed meal of 450 kcal with protein, carbohydrate, fat, fiber, sugar, and sodium stored
    When the meal is written to Health Connect
    Then one NutritionRecord exists with clientRecordId equal to the meal id
    And it carries 450 kcal
    And protein, carbohydrate, fat, fiber, and sugar are in grams
    And sodium is in milligrams
    And its interval is one second long starting at the meal's recorded time

  Scenario: AT-HC-004 — Delete removes the record
    Given a meal has been written to Health Connect
    When the user deletes the meal
    Then the local meal is removed
    And no record with that clientRecordId remains in Health Connect

  Scenario: AT-HC-005 — Health Connect is not installed
    Given Health Connect is unavailable on the device
    When the user saves an analyzed meal
    Then the write fails
    And the meal remains stored locally
    And no user-visible error is shown

  Scenario: AT-HC-006 — Rewrite the same meal
    Given a meal has already been written to Health Connect
    When the same meal is written again with the same clientRecordId and version 1
    Then Health Connect's duplicate handling decides the outcome
    And the app does not call updateRecords
```

## Automation status

- Automated by instrumented tests only, device required, and never run in CI (decision 0012): availability accepted as `SDK_AVAILABLE` or update-required, a write-and-read round trip, and a complete write/read flow with expected values (`HealthConnectIntegrationTest`). Several of those tests return early without failing when Health Connect or the permissions are unavailable, and the permission test asserts nothing, so they are weak evidence.
- Manual, device required: the real permission dialog, the platform's invocation of the rationale activity, denial and partial grant, revocation while the app is running, and the values as they appear in the Health Connect app. `AT-HC-001`, `AT-HC-002`, `AT-HC-005`, and `AT-HC-006` are in this group.
- Not covered by any test: `deleteNutrition`, all three intent builders, the sodium, fiber, and sugar mappings, the timestamp fallbacks, the 24-hour read window, and the unit assumptions. `apps/android/docs/TESTING.md` refers to a `health/HealthConnectManagerTest.kt` that does not exist.

## Known deviations

- Granting permission from `Save & Complete` does not perform the pending write, so `AT-HC-001` cannot currently complete a write on the first grant.
- A denial does not update the stored permission state, so the indicator can be stale, and there is no in-session revocation check.
- `hasAllPermissions` performs no availability check and no error handling, so a device without Health Connect may throw inside a coroutine.
- No user-visible message is produced for any Health Connect failure; the `statusMessage` strings and `healthConnectMessage` are never rendered, and the install and manage-permission intents are unused.
- Record values are written in a fixed unit per nutrient regardless of the stored unit, so a sodium value stored in grams would be written as milligrams.
