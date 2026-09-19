# ADR 0011: Distribute Unsigned APKs Through GitHub Releases

- Status: Accepted
- Date: 2026-06-29

## Context

The app is in an early phase aimed at developers and early adopters (`README.md`), and it requires a user-supplied OpenAI key to do anything (decision 0002). That makes a store listing a poor fit: the audience is small, the setup has prerequisites, and the app is unsigned when built in CI.

The build already has a `dev` flavor (`.dev` application id suffix, `-dev` version name suffix) and a `prod` flavor, so both variants need to be reachable.

## Decision

Publish APKs as GitHub Release assets and do not publish to Google Play.

- `android-delivery.yml` runs on pushes to `main` and on `v*` tags.
- A `quality-gate` job runs `testDevDebugUnitTest` before anything is built; `build-apk` depends on it and runs `assembleDebug assembleRelease`.
- APKs are uploaded as a workflow artifact on every run, and on a `v*` tag a `github-release` job creates or updates the release and attaches them with `--clobber`.
- No signing configuration is committed; releases carry unsigned builds.
- `CHANGELOG.md` and `versionCode`/`versionName` in `app/build.gradle` are updated by hand per release.

## Consequences

- Distribution needs no store account or review, and a tag push publishes the current build.
- Users install manually and must clear the "Open Anyway" block in system settings, as documented in `README.md`.
- No Play Store update path exists, so users do not get update notifications and must re-download.
- Unsigned builds are rejected by some managed devices and cannot be updated in place if the signing identity changes.
- Because builds are unsigned, Android cannot verify upgrade provenance, and there is no staged rollout or crash reporting from a store console (decision 0010).
