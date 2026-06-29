# Changelog

## v1.2.1 - Current stable

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
