# Meal detail specification

## Requirements

### Entry

- When the user taps a meal card whose status is `completed`, the app shall open the meal detail screen for that meal.
- The app shall not open the detail screen for a meal whose status is `error` or `analyzing`.

### Header timestamp

- The app shall show the meal's `createdAt` in the header, formatted with `MMM d, h:mm a` in the default locale (decision 0008).
- The app shall describe that timestamp as when the meal was recorded, and shall not describe it as when the meal was eaten.
- The app shall not read EXIF capture time for display.

### Content

- The app shall show the meal title, or `Unknown Meal` when none is stored.
- The app shall show the portion chip, the image, the calories with the `kcal` unit, and protein, fat, and carbohydrate badges, using the display rules in `analysis-result.md`.
- The app shall show `Detected Items (<n>)` followed by one card per stored item, each with its calories.
- The app shall not offer a per-item portion control on the detail screen; portion editing happens only through the meal-level chip.

### Fullscreen image

- When the user taps the image, the app shall show it fullscreen on a black background with fit scaling.
- When the user taps the fullscreen image or the close control, the app shall dismiss it.

### Delete

- When the user taps the delete control, the app shall ask for confirmation with the title `Delete Meal` and the body `Are you sure you want to delete this meal? It will also be removed from Health Connect.`
- When the user confirms, the app shall delete the meal, its nutrition result, its items, and its nutrients from local storage.
- When the user confirms and Health Connect permissions are granted, the app shall also delete the corresponding Health Connect record.
- When the user confirms, the app shall return to the idle state.
- If the Health Connect deletion fails, then the app shall keep the local deletion and shall not report the failure to the user.
- When the user dismisses the confirmation, the app shall keep the meal.
- When the user leaves the detail screen with the back control, the app shall return to the idle state without changing the meal.

## Acceptance scenarios

```gherkin
Feature: Meal detail

  Background:
    Given a completed meal is stored with a recorded time of 15 March, 2:30 PM

  Scenario: AT-DETAIL-001 — Show when the meal was recorded
    When the user opens the meal detail screen
    Then the header shows "Mar 15, 2:30 PM"
    And the header describes it as when the meal was recorded

  Scenario: AT-DETAIL-002 — Recorded time differs from the grouped day
    Given the meal was captured into a day at 05:00 on 10 March
    And the meal was recorded on 15 March at 2:30 PM
    Then the meal is listed under 10 March
    And the detail header shows "Mar 15, 2:30 PM"

  Scenario: AT-DETAIL-003 — Enlarge the photo
    When the user taps the meal image
    Then the photo is shown fullscreen on a black background
    And tapping the photo dismisses it

  Scenario: AT-DETAIL-004 — Delete a meal
    When the user taps the delete control
    Then a confirmation dialog titled "Delete Meal" is shown
    And it warns that the meal will also be removed from Health Connect

    When the user confirms
    Then the meal and its nutrition data are removed locally
    And the app returns to the idle state

  Scenario: AT-DETAIL-005 — Keep a meal
    When the user taps the delete control
    And dismisses the confirmation
    Then the meal is unchanged

  Scenario: AT-DETAIL-006 — A failed meal has no detail screen
    Given a meal whose status is error
    When the user taps its card
    Then the detail screen does not open
```

## Automation status

- Automated by JVM tests: cascade deletion of a meal's nutrition data and the removal of the meal from the queries and the observed flow (`ResultScreenCancelTest`, `MealDaoTest`, `MealRepositoryScenarioTest`).
- Manual, device required: every visible behaviour above. `AT-DETAIL-001` through `AT-DETAIL-006` have no automated coverage, and the timestamp formatting and locale handling have none at all.
- Not covered by any test: the Health Connect deletion branch, because the JVM test invokes the delete path with no Health Connect manager.

## Known deviations

- The detail screen displays `createdAt` while the list groups by `capturedAt` (decision 0008), so `AT-DETAIL-002` records a real inconsistency rather than an intended design.
- Deleting a meal logs a Health Connect failure but shows nothing to the user, so a meal can disappear locally and remain in Health Connect.
- The delete control's tint is the error colour but there is no undo or confirmation of the completed deletion.
