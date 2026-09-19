# ADR 0009: Write Health Connect Records Keyed by the Meal Id

- Status: Accepted
- Date: 2026-06-29

## Context

Nutrition data is written to Google Health Connect so it sits alongside other health metrics (decision 0001). Health Connect requires a record identity to relate an app's own record to the platform copy, and deleting or rewriting a record needs a stable handle that does not depend on a platform-assigned id.

The connected tests originally located written records by matching their calorie value or by recency inside a time window, and cleaned up by deleting every record found in the last 24 hours. That was unstable: a value match could hit a different record, and the read window kept moving.

## Decision

Give every written record a client-side identity derived from the meal, and use that identity for reads and deletes.

- The record is a `NutritionRecord` with `Metadata.manualEntry(clientRecordId = meal.id, clientRecordVersion = 1)`.
- The record's interval is one second: `startTime` is the meal's `capturedAt`, falling back to `analysisCompletedAt` and then to the current time, and `endTime` is one second later. Both zone offsets are the system offset at that instant.
- Fields written: energy in kilocalories; protein, total carbohydrate, total fat, dietary fiber, and sugar in grams; sodium in milligrams.
- Nutrient names are matched case-insensitively against fixed spellings (`protein`, `carbohydrates`/`carbs`, `fat`, `fiber`/`dietary fiber`, `sugar`/`sugars`, `sodium`) and the first match wins. The stored unit field is ignored.
- Deletion targets `clientRecordIdsList = [meal.id]` after the local row is deleted.
- Connected tests identify records by `clientRecordId` rather than by value.

## Consequences

- A meal id is the join key between the app's database and Health Connect, so a meal's identity must not be regenerated in place.
- The written unit is fixed per nutrient, so a value stored in a different unit is written as if it were the expected one.
- Only the fields above are written; other stored nutrients are dropped on the way to Health Connect.
- The app never calls `updateRecords` and always uses `clientRecordVersion = 1`, so re-saving a meal relies on Health Connect's own duplicate handling of `(clientRecordId, clientRecordVersion)`. Whether a second save updates, duplicates, or fails is not established by any test in this repository.
- The read helper only looks back 24 hours from the call, so a meal whose `capturedAt` is older than that is written but not visible to that read.
- Health Connect availability, permission, and write behavior are only checked on a device by tests that self-skip when permissions are missing (decision 0012).
