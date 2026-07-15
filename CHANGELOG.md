# Changelog

## v1.3.0 - Current stable

Released: 2026-07-15

### Added

- Added actionable meal-analysis failure messages with stable error categories.
- Added retry support that reuses the existing meal and saved image without creating duplicates.
- Added end-to-end coverage for initial success, failed analysis, successful retry, and missing saved images.

### Changed

- Failed analyses now display an error card instead of appearing as a valid `0 kcal` result.
- Analysis results are replaced atomically to prevent partially saved nutrition data.
- Set Android app version to `1.3.0` with `versionCode 4`.

## v1.2.1

Released: 2026-06-29

### Fixed

- Fixed camera capture analysis failing when the captured image file could not be reopened through the capture URI.
- Fixed gallery image analysis failing with `Unable to open image` for some Photo Picker/content-provider URIs by loading image data once and falling back through typed/file descriptor access.
- Stabilized Health Connect connected tests by matching test records with `clientRecordId` and cleaning up records created during tests.

### Changed

- Set Android app version to `1.2.1` with `versionCode 3`.

## v1.2.0

Released: 2026-06-29

### Added

- Added compressed meal image storage for camera and gallery registration.
- Added `MealImageStorage` to resize images to a 1280px maximum edge, save JPEGs at quality 80, and apply EXIF orientation correction.
- Added unit tests covering resized JPEG output and image-loading failure behavior.

### Changed

- Meal records and OpenAI nutrition analysis now use the compressed JPEG saved under `context.filesDir/images/`.
- Original full-resolution images are no longer copied directly into internal app storage.
