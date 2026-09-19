# Meal capture specification

## Requirements

### Entry paths

- The app shall offer exactly three ways to add a meal: camera capture, photo picker, and duplication of a previous meal. Manual entry of nutrition values is not supported.
- The app shall present these as `Take Photo`, `Choose from Gallery`, and `Search Previous Meal` in the `Add Meal` dialog.
- The app shall show the `Add Meal` action only while no analysis, result, or detail screen is open.

### Missing credential

- When a capture starts and no OpenAI key is stored, the app shall open the settings dialog instead of analyzing.
- When the photo picker returns an image and no OpenAI key is stored, the app shall discard the picked image rather than queueing it.

### Camera

- When the user selects `Take Photo` and the camera permission is already granted, the app shall open the system camera and write the capture to `cacheDir/images/capture_<millis>.jpg` through the FileProvider authority `${applicationId}.fileprovider`.
- When the user selects `Take Photo` and the camera permission is granted, the app shall close the `Add Meal` dialog before opening the camera.
- When the user selects `Take Photo` and the camera permission has not been granted, the app shall request the `android.permission.CAMERA` permission.
- When the camera permission request is denied, the app shall leave the `Add Meal` dialog open and shall not display an error message, a rationale, or a link to application settings.
- When the camera returns a successful capture and a key is configured, the app shall start analysis of that capture.
- When the camera returns a failed or cancelled capture, the app shall return to the idle state without a user-visible message.

### Photo picker

- The app shall use the Android system photo picker (`PickVisualMedia` with `ImageOnly`) so that no storage or media runtime permission is required.
- The manifest shall not declare a media read permission.
- When the photo picker returns no image, the app shall make no change.

### Image preparation

- The app shall store and upload only a compressed JPEG, and shall not retain the original image (decision 0005).
- The app shall cap the longest edge at 1280 pixels using a power-of-two subsample followed by an exact scale when still above the cap.
- The app shall encode the JPEG at quality 80.
- The app shall apply EXIF orientation while decoding, so the stored image is upright, and shall not carry EXIF forward.
- The app shall store the file at `filesDir/images/meal_<timestampMillis>.jpg`.
- If compression fails, then the app shall delete the partial file and report a failure rather than store a corrupt image.
- If the source cannot be opened or decoded, then the app shall fail the capture instead of storing an empty image.
- The app shall not display the image size, the encoding quality, or a compression progress indicator.

### Duplicate from history

- When the user duplicates a previous meal, the app shall copy the meal, its nutrition result, its items, and its nutrients with new identifiers.
- When the user duplicates a previous meal, the app shall set the copy's `capturedAt` to 05:00 local on the day currently displayed, and shall set its status to `completed`.
- When a meal's image is shared by a duplicate, the app shall delete the image file only after the last meal referencing it is deleted.
- The app shall make the previous-meal path available without an OpenAI key, because it performs no analysis.

## Acceptance scenarios

```gherkin
Feature: Meal capture

  Background:
    Given Recalo is running as the only activity
    And an OpenAI API key is stored

  Scenario: AT-CAPTURE-001 — Capture a meal with the camera
    Given the camera permission is granted
    When the user taps the add button
    And selects "Take Photo"
    Then the Add Meal dialog closes
    And the system camera opens

    When the user takes a photo and confirms it
    Then the app stores a compressed JPEG under filesDir/images
    And the analyzing screen is shown with the captured photo

  Scenario: AT-CAPTURE-002 — Deny the camera permission
    Given the camera permission has not been granted
    When the user taps the add button
    And selects "Take Photo"
    Then the system permission dialog is shown
    And the Add Meal dialog remains open

    When the user denies the permission
    Then no error message is shown
    And no link to application settings is offered
    And no camera app opens

  Scenario: AT-CAPTURE-003 — Choose an existing photo
    When the user taps the add button
    And selects "Choose from Gallery"
    Then the system photo picker opens
    And no media permission is requested

    When the user picks an image
    Then the analyzing screen is shown with the picked image

  Scenario: AT-CAPTURE-004 — Capture without an API key
    Given no OpenAI API key is stored
    When the user taps the add button
    And selects "Take Photo"
    Then the Add Meal dialog closes
    And the settings dialog opens
    And no camera app opens

  Scenario: AT-CAPTURE-005 — Duplicate a previous meal
    Given a completed meal recorded on an earlier day
    When the user taps the add button
    And selects "Search Previous Meal"
    And enters a matching food name
    And selects the result
    Then a new meal exists with a new identifier
    And the copy belongs to the displayed day at 05:00
    And the copy reuses the original image file

    When the user deletes the original meal
    Then the shared image is still available to the copy

    When the user deletes the copy
    Then the shared image is deleted

  Scenario: AT-CAPTURE-006 — Bound the stored image size
    Given a 4032x3024 source photo
    When the app prepares the image for upload
    Then the stored JPEG is 1008x756
    And the stored JPEG is no larger than 1280 pixels on its longest edge
```

## Automation status

- Automated by JVM tests: the compression pipeline (target path, maximum edge, JPEG encoding, `content://` sources, undecodable input, and the 4032×3024 → 1008×756 characterization) in `MealImageStorageTest`; duplicate-from-history identifiers, `capturedAt` override, copied data, and shared-image deletion in `MealRepositoryScenarioTest`.
- Manual, device required: everything that needs the camera app, the photo picker, or a runtime permission dialog. No automated test covers `AT-CAPTURE-001` through `AT-CAPTURE-004`.
- Not covered by any test: EXIF orientation handling, and the behaviour when the key is missing at each capture entry point.

## Known deviations

- `showSourceSelection` is not cleared on the camera-permission branch, so the `Add Meal` dialog stays open while the system permission dialog is shown. `AT-CAPTURE-002` records this as current behaviour.
- The temporary camera file in `cacheDir/images` is never deleted by app code.
- The app reads the whole source stream into memory before decoding, so a very large source image is a memory risk on low-end devices.
