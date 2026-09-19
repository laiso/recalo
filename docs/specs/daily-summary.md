# Daily summary specification

## Requirements

### Day boundary

- The app shall define "today" as the local date of the current time minus five hours (decision 0007).
- The app shall treat a day as spanning 05:00 local through 05:00 local on the following day.
- The app shall group meals into a day by `capturedAt`.
- When a meal is captured or duplicated while a past day is displayed, the app shall stamp it with that day at 05:00 local.
- The app shall compute "today" once per composition, so a session left open across 05:00 keeps the previous day until the screen is recreated.

### Navigation

- The app shall show one day at a time, with no group headers.
- When the user taps the previous-day control or drags right by more than 50 pixels, the app shall display the previous day.
- When the user taps the next-day control or drags left by more than 50 pixels and the displayed day is not today, the app shall display the next day.
- While today is displayed, the app shall disable the next-day control.
- The app shall format the day label with the pattern `MMM d (E)` in the default locale.
- The app shall not animate the transition between days.

### Daily totals

- The app shall sum total calories from stored nutrition results and protein, fat, and carbohydrate from top-level nutrients, matching nutrient names by case-insensitive substring.
- While the day has no meals, the app shall show `-` in place of the calorie and macronutrient values.
- While the day has meals, the app shall show the calorie total followed by `kcal` and each macronutrient truncated to whole grams followed by `g`.
- While the macronutrient sum is greater than zero, the app shall show each macronutrient's share of the sum as a whole percentage; otherwise it shall show `-`.
- The app shall treat a failed meal as contributing zero calories and zero macronutrients (see `meal-analysis.md`).

### Meal list

- The app shall list the displayed day's meals newest first by `capturedAt`.
- While a meal's status is `analyzing`, the app shall show the card text `Analyzing meal` and `Nutrition results will appear when the analysis is complete.` and shall not open the detail screen when tapped.
- While a meal's status is `error`, the app shall show the failure presentation defined in `meal-analysis.md` and shall not open the detail screen when tapped.
- When the displayed day has no meals and the day is today, the app shall show `No Meals Yet` with `Tap the + button to capture your food and let AI do the rest.`
- When the displayed day has no meals and the day is not today, the app shall show `No Data Found` with `There are no meal records for this specific date.`
- While no OpenAI key is stored, the app shall show the banner `OpenAI API key is not configured. Tap to set up.` that opens the settings dialog, in every screen state.

### Previous-meal search

- When the user opens the search dialog, the app shall focus the query field and show the keyboard.
- When the user types a query, the app shall search on every keystroke, matching the nutrition title or an item name case-insensitively.
- When the query is blank, the app shall show `Enter a food or meal name.` and shall not search.
- When the search returns no rows, the app shall show `No matching meals found.`
- The app shall trim the query before searching, and shall return no rows for a whitespace-only query.
- While a search or duplication is running, the app shall show `Searching...` or `Adding meal...` with a progress indicator.
- When the user selects a result, the app shall close the dialog and duplicate the meal as defined in `meal-capture.md`.

## Acceptance scenarios

```gherkin
Feature: Daily summary and meal list

  Background:
    Given an OpenAI API key is stored

  Scenario: AT-SUMMARY-001 — A late-night meal belongs to the previous day
    Given the current local time is 01:30 on 16 September
    When the user captures a meal
    Then the app treats "today" as 15 September
    And the meal is stamped 15 September 05:00
    And the meal is listed under 15 September

  Scenario: AT-SUMMARY-002 — Daily totals
    Given the displayed day contains a 200 kcal meal with 10 g protein, 5 g fat, and 20 g carbohydrate
    And the displayed day contains a 300 kcal meal with 5 g protein, 5 g fat, and 10 g carbohydrate
    Then the summary shows 500 kcal
    And it shows 15 g protein, 10 g fat, and 30 g carbohydrate
    And the macronutrient shares are shown as whole percentages of 55

  Scenario: AT-SUMMARY-003 — An empty day
    Given the displayed day is today and has no meals
    Then the summary shows "-" for calories and for each macronutrient
    And the list shows "No Meals Yet"
    And the body reads "Tap the + button to capture your food and let AI do the rest."

    When the user moves to a previous day with no meals
    Then the list shows "No Data Found"
    And the body reads "There are no meal records for this specific date."

  Scenario: AT-SUMMARY-004 — Move between days
    Given today is displayed
    When the user taps the previous-day control
    Then yesterday is displayed
    And the next-day control is enabled

    When the user taps the next-day control
    Then today is displayed
    And the next-day control is disabled

  Scenario: AT-SUMMARY-005 — A card for an in-progress analysis
    Given a meal was captured and its analysis has not finished
    When the day list is shown
    Then the card reads "Analyzing meal" and "Nutrition results will appear when the analysis is complete."
    And tapping the card does not open the detail screen

  Scenario: AT-SEARCH-001 — Find and reuse a previous meal
    When the user opens the search dialog and types "curry"
    Then matching meals are listed by title or item name
    When the user selects a result
    Then the dialog closes
    And the meal is duplicated into the displayed day

  Scenario: AT-SEARCH-002 — Search edge cases
    When the query is blank
    Then the dialog shows "Enter a food or meal name."
    And no search runs

    When the query matches nothing
    Then the dialog shows "No matching meals found."
```

## Automation status

- Automated by JVM tests: the search SQL matching title or item name, case insensitivity, distinctness, ordering, and the blank and whitespace-only queries (`MealDaoTest`, `MealRepositoryScenarioTest`); duplication landing on the selected day with the summed calories (`MealRepositoryScenarioTest`).
- Manual, device required: `AT-SUMMARY-001` through `AT-SUMMARY-005` and `AT-SEARCH-001`/`AT-SEARCH-002` as user-visible behaviour. The day-boundary arithmetic, the summary totals, the empty states, the day navigation and drag gestures, and the search dialog UI have no automated coverage at all.
- Not covered by any test: the percentage computation, the `-` placeholder rules, and the date-label formatting.

## Known deviations

- The summary's zero state and a genuinely zero-calorie day are not distinguished: a day whose meals all failed shows `0 kcal` rather than an unavailable state.
- Because "today" is captured once per composition, a long-running session can show the wrong day across the 05:00 boundary.
- A duplication failure sets `previousMealSearchError` after the dialog has already closed, so the error can never be seen.
- The search dialog shows `Searching...` and `Adding meal...` from the same condition, so the label depends on which flag is set rather than on a distinct phase.
- The key-missing banner reads the stored key during composition rather than through state, so it disappears only when an unrelated recomposition happens.
