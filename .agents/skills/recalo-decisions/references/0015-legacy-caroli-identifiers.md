# ADR 0015: Keep Legacy `caroli` Identifiers After the Recalo Rename

- Status: Accepted — known debt
- Date: 2026-03-14

## Context

The app was renamed from Caroli AI to Recalo. The user-visible name, the Gradle namespace and application id (`so.lai.recalo`), and the package directory were updated.

Several internal identifiers were not. The maintainer reports this as an oversight rather than a deliberate compatibility measure, recorded here as debt.

## Decision

Leave the legacy identifiers in place for now, and treat renaming them as pending work rather than as an intended design.

Known legacy identifiers:

| Identifier | Location | Kind |
| --- | --- | --- |
| `CaroliDatabase` | `data/local/CaroliDatabase.kt`, Room schema directory `app/schemas/so.lai.recalo.data.local.CaroliDatabase/` | Kotlin class name and Room schema file path |
| `caroli_prefs_encrypted` | `SessionManager` | Encrypted preference file name |
| `caroli_prefs` | `SessionManager` | Plaintext fallback preference file name |
| `caroli_openai_master` | `SecurityUtils` | Android Keystore key alias |
| `Theme.Caroliai` | `AndroidManifest.xml`, `res/values/themes.xml` | Android theme name |

## Consequences

- The rename is cosmetic on the surface but each identifier has a different cost to change: a preference file name change silently loses the stored API key and model level, and a Keystore alias change makes existing ciphertext undecryptable.
- The Room schema path embeds the class name, so renaming the database class changes the exported schema location and the migration test story.
- `res/values-night/themes.xml` declares `Theme.CaroliAI`, which does not match the applied `Theme.Caroliai`, so the night variant does not override anything. This is part of the same leftover naming and is also unreviewed.
- Any rename needs a migration or an accepted data loss decision per identifier, which is why this is recorded rather than fixed opportunistically.
- Marked as known debt so a future change can supersede it with a record that states the migration path.
