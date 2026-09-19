# ADR 0005: Compress and Downsample Meal Images Before Saving and Upload

- Status: Accepted
- Date: 2026-06-29

## Context

Camera photos on modern Android devices are several thousand pixels wide. Sending that payload to a vision model costs upload time and tokens, keeping the original file on disk costs storage for every meal, and the base64 encoding used for the request inflates the payload by roughly a third.

## Decision

Store and upload a single compressed JPEG produced by `MealImageStorage.saveCompressedJpeg`, and do not retain the original image.

- The longest edge is capped at `DEFAULT_MAX_EDGE_PX = 1280`.
- Encoding uses JPEG quality `DEFAULT_JPEG_QUALITY = 80`.
- Downsampling first picks a power-of-two `inSampleSize` that brings the longest edge to or below the cap, then scales exactly to the cap if it is still above it.
- EXIF orientation is applied during decode so the stored image is upright, and the EXIF metadata itself is not carried forward.
- The stored file is `filesDir/images/meal_<timestampMillis>.jpg`. The UI layer never shows the size, quality, or a compression progress indicator.
- The compressed file, not the original, is the payload for the API request (decision 0003).

## Consequences

- Upload cost and latency are bounded and predictable, and storage per meal stays small.
- The power-of-two step means the result is often well below the cap: a 4032×3024 photo becomes 1008×756, not 1280×960.
- Detail lost here cannot be recovered. Crop-based reanalysis from the saved image is limited, and the open investigation in `docs/investigations/all-zero-analysis-investigation.md` records this as a constraint on fixing all-zero results.
- The whole source stream is read into memory before decoding, so an extremely large source image poses a memory risk on low-end devices.
- Recovering original detail later requires either retaining a second source file or asking the user to re-select the photo; both change the storage contract. Decision 0016 closes the first option, so re-analysis must always ask for a new photo.
- The pre-compression pipeline is covered by JVM tests, but EXIF orientation handling is not.
