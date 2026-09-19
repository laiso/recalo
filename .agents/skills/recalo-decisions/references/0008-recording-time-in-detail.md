# ADR 0008: Show the Recording Time, Not the Photo Time, in the Meal Detail Header

- Status: Accepted
- Date: 2026-03-25

## Context

The meal detail header needs one timestamp. Two candidates exist: `capturedAt`, which is assigned when the meal is added, and `createdAt`, which is when the row was written.

An earlier version displayed `capturedAt`, which for gallery imports can be the time the photo was taken rather than the time the meal was recorded, and which for a meal added while viewing a past day is set to that day at 05:00 (decision 0007). Neither is "when I recorded this", and relying on EXIF would fail for screenshots and recipe images that carry no capture time.

## Decision

The meal detail header shows `createdAt`, formatted with `MMM d, h:mm a` in the default locale, described to the user as when the meal was recorded.

EXIF capture time is not read for display and is not relied on for grouping.

## Consequences

- The header answers "when did I record this", which matches the habit the feature is meant to support and works for images with no camera metadata.
- The header can disagree with the day the meal is listed under, because the list groups by `capturedAt` (decision 0007). A meal added today into a past day shows today's recording time on a past day's list.
- Duplicating a meal from history sets a new `capturedAt` but leaves the copied `createdAt`, so a duplicate's header does not show when it was duplicated.
- Because the displayed value is the local row creation time, nothing needs to be persisted beyond `createdAt`, which already defaults to `System.currentTimeMillis()`.
