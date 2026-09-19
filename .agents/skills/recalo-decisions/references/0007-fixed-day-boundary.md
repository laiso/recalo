# ADR 0007: Fix the Daily Boundary at 05:00 and Stop Exposing It as a Setting

- Status: Accepted
- Date: 2026-03-25

## Context

Meals are grouped into days for the home list and the daily totals. A midnight boundary misfiles anything eaten late at night: a meal at 01:00 lands on the next day, away from the dinner it belongs with.

An earlier version exposed a per-user "Day Start Time" setting and stored it under `day_start_hour`. The setting was removed along with the fix, on the grounds that it added UI and persistence surface for a value the maintainer judged not worth configuring.

The maintainer's stated reason for choosing 05:00 specifically is to keep late-night meals with the previous day.

## Decision

Use a fixed day boundary of 05:00 local time and remove the day-start setting from the UI and from `SessionManager`.

- `SessionManager.DEFAULT_DAY_START_HOUR` is `5`.
- "Today" is the local date of `now` minus five hours.
- A day's window is that date at 05:00 local through 24 hours later, and meals are grouped by `capturedAt`.
- The setting's save and read methods are deleted; the previously stored `day_start_hour` value is ignored.

## Consequences

- A meal eaten at 01:00 appears on the previous day, matching how the user thinks about a late dinner.
- There is no way for a user with a different schedule to change the boundary, and no migration path for a value written by an older version.
- The boundary is computed once per composition with `remember`, so a session left open across 05:00 keeps the previous "today" until the screen is recreated.
- Grouping uses `capturedAt` while the detail header shows `createdAt` (decision 0008), so a meal can be listed under one day and dated another.
- Changing the boundary now means changing a constant and this record, not a settings key.
