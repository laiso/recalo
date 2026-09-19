# ADR 0001: Keep Meal History on the Device with No Application Backend

- Status: Accepted
- Date: 2026-03-14

## Context

Recalo analyzes photos of meals and keeps a nutrition history that is also written to Google Health Connect. A conventional design would put meal records behind a service so that multiple devices, accounts, and support access become possible. That design requires operating a database, an account system, and a place where user meal photos would sit.

The maintainer's stated reason for not doing so is privacy: the product promise is that meal photos and records never reach a server this project operates.

## Decision

Store all meal records locally in a Room database and treat the device as the only source of truth. Do not run an application backend, do not add accounts, and do not add analytics or tracking SDKs.

The only outbound network calls are to the AI provider the user configured, and Google Health Connect is the only cross-application integration.

`PRIVACY_POLICY.md` states this as a product commitment: no personal information, no meal photos on our servers, no analytics, no usage data transmitted externally.

## Consequences

- Meal history does not survive uninstall or device loss unless the user relies on Android backup or Health Connect. `analysis_diagnostics` is stored in `noBackupFilesDir`, so diagnostic records deliberately do not survive either.
- Nothing can be queried server-side, so support depends on the user sending a diagnostic archive (decision 0010).
- There is no server-side secret to protect, but every user must obtain and pay for their own provider key (decision 0002).
- Any future feature that needs a server contradicts this record and requires a new decision record rather than an edit to this one.
