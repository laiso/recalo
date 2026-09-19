# ADR 0013: Keep All Screens in One Compose File with State-Driven Navigation

- Status: Accepted — review target
- Date: 2026-03-14

## Context

The app has four full-screen surfaces (idle list, analyzing, result, detail) plus a set of dialogs. A conventional Compose app would use Navigation Compose or a route enum and separate files per screen.

There is no deep link, no back stack requirement, no second entry point, and no test that drives the UI, so the structure was never forced to change.

## Decision

Keep every screen and dialog in `ui/screens/HomeScreen.kt` and switch screens with a four-value `ScreenState` (`IDLE`, `ANALYZING`, `RESULT`, `DETAIL`) rendered through `AnimatedContent`.

- `MainActivity` only sets the theme and hosts `HomeScreen`; there is no `NavHost`.
- The `HomeViewModel` is declared in the same file as the UI that uses it.
- There is no `BackHandler`, so system back during ANALYZING, RESULT, or DETAIL is left to the Activity default.
- Screen transitions are driven by application state, not by navigation events: a successful analysis sets the state to `RESULT`, and Save or Cancel resets to `IDLE`.

## Consequences

- The single file is 2774 lines and holds all ViewModel logic for the app, so any UI change requires editing the most contended file in the repository.
- Nothing can be verified by CI: no automated test covers the screens (decision 0012), and only one test tag, the diagnostic report button, is exercised even by the device test.
- Unused duplicates accumulated: `ui/components/MealCard.kt`, `MealItemCard.kt`, and `NutrientBadges.kt` are not referenced anywhere, and `ui/screens/LoginScreen.kt` is unreachable.
- There is no back stack, so the system back button cannot be relied on to return to a previous screen, and there are no deep links.
- Marked a review target: splitting screens into files or introducing a navigation library is a deliberate decision to make later, and this record should be superseded rather than edited.
