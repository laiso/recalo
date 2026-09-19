# Analysis result specification

## Requirements

### Presentation

- When analysis succeeds, the app shall show the result screen with the analyzed image, the meal title, the portion chip, the detected items, the calories, and protein, fat, and carbohydrate badges.
- When the response contains no title, the app shall show `Analysis Result` on the result screen and `Untitled Meal` on cards and search results.
- The app shall look up protein, fat, and carbohydrate by case-insensitive substring match on the nutrient name, and shall show `0` when a nutrient is absent.
- The app shall truncate nutrient amounts to whole grams for display and shall always label them `g`, regardless of the unit returned by the provider.
- The app shall display only calories, protein, fat, and carbohydrate; other stored nutrients are not shown.
- When the portion ratio is not `1.0`, the app shall show it as `<ratio> x` on cards; the result and detail screens shall always show the chip.

### Portion editing

- When the user taps the portion chip, the app shall open the `Adjust Portion` dialog with the choices `0.5 x`, `1.0 x`, `1.5 x`, and `2.0 x`.
- When the user confirms a portion, the app shall store the new ratio and rescale the meal from the previously stored ratio, so repeated edits do not compound.
- When the user confirms a portion, the app shall rescale each item's calories and nutrients, rewrite the leading quantity number in each item's quantity text, and recompute the meal total as the sum of the item calories.
- When the stored ratio is not one of the four choices, the app shall select no chip and shall re-apply the stored ratio if the user confirms without choosing.

### Save

- When the user taps `Save & Complete` and Health Connect permissions are granted, the app shall write the meal to Health Connect.
- When the user taps `Save & Complete` and Health Connect permissions are not granted, the app shall request them.
- When the user taps `Save & Complete`, the app shall return to the idle state and shall keep the meal and its nutrition data in local storage.
- When the nutrition result is absent, the app shall not attempt a Health Connect write.

### Discard

- When the user taps `Cancel` on the result screen, the app shall delete the analyzed meal from local storage.
- When the user taps `Cancel` and Health Connect permissions are granted, the app shall also delete the corresponding Health Connect record.
- When the user taps `Cancel`, the app shall return to the idle state.

### Fullscreen image

- When the user taps the analyzed image, the app shall show it fullscreen on a black background with fit scaling.
- When the user taps the fullscreen image or the close control, the app shall dismiss it.

## Acceptance scenarios

```gherkin
Feature: Analysis result

  Background:
    Given an analysis completed successfully

  Scenario: AT-RESULT-001 — Save with Health Connect permission already granted
    Given Health Connect permissions are granted
    When the user taps "Save & Complete"
    Then a NutritionRecord is written to Health Connect
    And the meal remains stored locally
    And the app returns to the idle state

  Scenario: AT-RESULT-002 — Save without Health Connect permission
    Given Health Connect permissions are not granted
    When the user taps "Save & Complete"
    Then the Health Connect permission dialog is shown
    And the app returns to the idle state
    And the meal remains stored locally

  Scenario: AT-RESULT-003 — Reduce the portion
    Given the stored portion ratio is 1.0
    And an item has 200 kcal
    When the user opens "Adjust Portion" and chooses "0.5 x" and saves
    Then the stored ratio is 0.5
    And the item has 100 kcal
    And the meal total is the sum of the rescaled items

  Scenario: AT-RESULT-004 — Edit the portion again
    Given the stored portion ratio is 1.5
    And an item has 300 kcal
    When the user chooses "2.0 x" and saves
    Then the item has 400 kcal
    And the item calories are not compounded from the previous edit

  Scenario: AT-RESULT-005 — Discard the result
    When the user taps "Cancel"
    Then the meal is removed from local storage
    And no nutrition result remains for it
    And the app returns to the idle state

  Scenario: AT-RESULT-006 — Enlarge the analyzed photo
    When the user taps the analyzed image
    Then the photo is shown fullscreen on a black background
    And tapping the photo dismisses it
```

## Automation status

- Automated by JVM tests: portion-ratio storage, rescaling without compounding, quantity rewriting, per-item nutrients, and total recomputation (`MealRepositoryPortionRatioTest`); removing a meal and its nutrition data (`ResultScreenCancelTest`).
- Manual, device required: every scenario above as a user-visible flow. `AT-RESULT-001` through `AT-RESULT-006` need a device, because no automated test drives this screen.
- Not covered by any test: the rescaling arithmetic with a ratio outside the four choices, and the display rounding and unit rules.

## Known deviations

- Granting Health Connect permission from `Save & Complete` does not perform the pending write: the permission callback only refreshes the permission state, so the meal is saved locally but never written to Health Connect in that interaction. The user must rely on a later write path, which does not exist yet.
- The app returns to the idle state immediately after launching the permission request, regardless of its result.
- No success or failure feedback is rendered: `saveState` and `healthConnectMessage` are set but never read by any composable, so `Saved to Health Connect.` and the failure messages in `AT-RESULT-001`/`AT-RESULT-002` are invisible.
- `Save & Complete` returns to the idle state before the Health Connect write coroutine finishes.
- The portion dialog's `itemId` comes from the meal's first item, so tapping a specific item's portion control edits the whole meal.
