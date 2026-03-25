# Meal Detail Screen Display Timestamp Specification

## User Value

Displays "when this meal was recorded" at a glance, making it easier to manage meal recording history.

## Specification Overview

The timestamp displayed in the meal detail screen header shows **"when you recorded this meal"**.

## Display Rules

### Basic Rules

| Situation | Displayed Time | Description |
|-----------|---------------|-------------|
| Recorded today | Today's recording time | "Recorded at 2:30 PM today" |
| Recorded to a past date | Recording time | Even if a past date is selected, shows the actual recording time |

### Examples

**Example 1: Record a meal immediately after eating**
- Recording time: Jan 15, 12:30 PM
- Display: `Jan 15, 12:30 PM`

**Example 2: Record a past meal later**
- Selected date: Jan 10 (Monday)
- Recording time: Jan 15 (Saturday) 2:30 PM
- Display: `Jan 15, 2:30 PM`

## Why This Specification

### ✅ Benefits

1. **Consistency**
   - Always clear "when it was recorded"
   - No confusion when selecting past dates

2. **Helps build recording habits**
   - Can reflect "Oh, I recorded yesterday's meal today"
   - Easier to notice missed recordings

3. **No EXIF dependency**
   - Can display without photo recording time
   - Works with recipe images too

### 📝 Notes

- Displays "when recorded" not "when eaten"
