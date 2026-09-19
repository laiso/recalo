# ADR 0016: Do Not Retain Original Meal Images

- Status: Accepted
- Date: 2026-09-18

## Context

Decision 0005 stores only a compressed JPEG at a 1280 px cap and quality 80, and records that recovering original detail later would require either retaining a second source file or asking the user to re-select the photo.

Re-analyzing a completed meal from a higher-resolution source was proposed, and proposed step 4 of `docs/investigations/all-zero-analysis-investigation.md` asks to preserve a source image for later cropping. That would mean keeping the full-resolution source for every meal.

## Decision

Retain no original or full-resolution meal image. The compressed JPEG remains the only stored image.

The maintainer's stated reasons are privacy and data volume:

- The meal photo is the most sensitive artifact this app holds. A retained full-resolution copy would sit in application storage indefinitely and could be re-uploaded at full resolution, which is a larger disclosure than the compressed image already sent.
- Keeping a second multi-megabyte file per meal multiplies local storage for a benefit only some meals would use.

Re-analysis therefore has to obtain a new source: the user re-selects a photo through the system photo picker, and that image is treated as a new input rather than as a recovery of the original.

## Consequences

- No new column, no database version bump, no migration, and no reference-counting change is needed for re-analysis. `MealLogEntity.imagePath` stays the only image path.
- The picked image is a different image, possibly a different crop or angle, so a re-analysis result is not comparable to the first analysis and cannot be attributed to resolution alone.
- Detail discarded at capture time (decision 0005) is permanently unrecoverable, so cropping the saved JPEG cannot restore it either.
- This rejects proposed step 4 of `docs/investigations/all-zero-analysis-investigation.md`. The rest of that investigation's fix sequence is unaffected.
- The camera temp file in `cacheDir/images`, which is never deleted by app code, is not an archive and must not be relied on as one.
- The feature has to ask the user for a photo on every re-analysis, and the UI must say so before the user starts rather than failing afterwards.
- Local storage per meal stays bounded, and backup and uninstall behaviour is unchanged (decision 0001).
- Uploading a larger user-chosen photo sends more data to the provider than the compressed path did; the fact that the photo leaves the device matters more at full resolution (decision 0003).
