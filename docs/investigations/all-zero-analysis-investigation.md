# All-zero image analysis: reproduction and retry design

## Report

An unchanged photo frequently produces zero for every nutrition value. Cropping
the photo and uploading it again can succeed. The user subsequently supplied a
1260×2800 screenshot showing the photo and identified the model as `gpt-5.4`.
The original uploaded photo, successful crop, and original failing provider
response have not been collected.

## Live API check (2026-09-18)

Six direct Responses API requests used `gpt-5.4`, the current app system/user
prompts, Japanese output language, and TextConfig exported from the compiled app
class. The returned model was `gpt-5.4-2026-03-05` throughout.

| Input | Calories across three identical requests | All-zero results |
|---|---|---|
| Attached screenshot, 1260×2800 PNG | 335, 390, 392 | 0/3 |
| Screenshot resized to 315×700 JPEG, quality 80 | 245, 205, 245 | 0/3 |

All six responses were HTTP 200 with status `completed`, populated nutrition
fields, and two meal items. **The reported all-zero failure did not reproduce.**
No crop comparison was performed because the baseline failure did not reproduce.
This small sample does not rule out intermittent failure.

The resized variant matches the app's output dimensions/quality parameter for
this screenshot, but ImageMagick encoding is not byte-identical to Android image
processing. The screenshot is not confirmed to be the actual failed upload.
The installed app version and device language are also unverified.

Food identification changed from anko mochi in the full screenshot to nori-wrapped
mochi/dango in the smaller JPEG. Neither identification has ground-truth
confirmation; this is sensitivity to image representation, not reproduction of
the zero-result defect.

Local evidence is in `/tmp/recalo-api-repro/RESULTS.md`, with six raw responses,
request IDs, and request/image hashes. No credentials are stored there. The next
useful input is the actual failed upload or the JPEG sent by the app, ideally
together with its failed provider response.

## Current code and controlled reproductions

Additional live check: the user supplied `Screenshot_20260918-163503.png`
(cheese/fish snack packaging). With the same `gpt-5.4` setup, original PNG runs
returned 330, 223, and 315 kcal; 315×700 quality-80 JPEG runs returned 336, 330,
and 330 kcal. All six were HTTP 200 / completed, with nonzero protein, fat,
and carbohydrates; only fiber was zero. The all-zero failure again did not
reproduce. The same screenshot/encoding limitations apply. Evidence:
`/tmp/recalo-api-repro/CHEETARA-RESULTS.md` and its linked raw responses.

- `OpenAiService` accepts explicit zero values as a successful result without
  validating whether the meal was recognized.
- Gson can deserialize missing primitive numeric fields as zero. Kotlin's
  non-null types do not prevent this. A controlled missing-field response tests
  this independently of a model returning explicit zeros.
- An all-zero successful response is persisted as `completed`; the existing
  `beginAnalysisRetry` query only accepts `error`. Image changes in the current
  failure-retry path alone would therefore miss this case.
- Failure retry resends the same saved JPEG; it does not preprocess differently.
- Only the compressed image is saved. Downsampling chooses the first power of
  two that brings the image below 1280 pixels, before exact scaling. For a
  4032×3024 photo this produces 1008×756. Lost source detail cannot be restored by
  enlarging or cropping the saved JPEG.

These findings do not prove which condition caused the reported incident.
The new characterization tests intentionally assert current behavior and should
be changed to the desired behavior when a fix is implemented.

Validation on 2026-09-18: the service and image-storage test classes passed;
the end-to-end test class also passed in a separate run. This confirms the
controlled all-zero persistence/retry behavior and the image dimensions, not
live-model behavior. No production code was changed.

Run the controlled reproductions with:

```sh
cd apps/android
./gradlew testDevDebugUnitTest \
  --tests 'so.lai.recalo.data.openai.OpenAiServiceTest' \
  --tests 'so.lai.recalo.data.image.MealImageStorageTest' \
  --tests 'so.lai.recalo.e2e.AnalysisFailureE2ETest'
```

## Photo comparison experiment

Use the same model, prompt, language, and request settings for each variant.
Repeat each variant a small fixed number of times (for example five), interleave
the order, and report counts rather than treating one success as a fix.

| Variant | Question |
|---|---|
| Original photo through current preprocessing | Does the reported failure reproduce? |
| User's successful crop through current preprocessing | Does the crop consistently change the outcome? |
| Full original sampled above the target, then resized exactly to 1280 | Does retaining more detail help without removing food? |
| Full original re-encoded without cropping, with matched output settings | Is cropping necessary, or is re-encoding sufficient? |
| Crop from the already saved JPEG | Can existing records benefit without the source photo? |

Record input/output dimensions, image hashes, selected and returned model,
response status, field presence, calories, confidence, item count, and nutrient
names/amounts. Keep any diagnostic provider output local, exclude API keys, and
do not put raw responses in the persisted user-facing error field. Inspect the
actual sent images for orientation, legibility, and whether all dishes remain.
Classify HTTP failures, malformed/missing data, explicit zeros, and valid results
separately. Correctness also requires retaining all food, not just positive
calories.

## Current disposition (2026-09-19)

[Decision 0018](../../.agents/skills/recalo-decisions/references/0018-defer-reanalysis-until-failure-evidence.md)
defers completed-meal re-analysis pending review of failure evidence. The
maintainer's observation that the same image frequently returns zero is not
contradicted by the screenshot API checks: those checks did not establish that
they submitted the original failing bytes. Neither is proof of an app-side or
provider-side cause.

First collect an existing diagnostic ZIP from an affected meal and compare the
sent image, raw response, parsed values, and saved values. Historical meals may
lack the original response; record that gap rather than reconstructing it.
Compare a confirmed failing input with a successful crop before selecting an
image transformation. Positive calories alone do not prove correct recognition.

Photo re-selection with a user-prepared crop is a candidate recovery operation,
not an implemented capability. If implemented, it must preserve the existing
meal on failure. Diagnostic retention and reporting for that attempt are a
separate open decision, not implied by preserving the meal. Current error-only
retry and diagnostic report behavior remain unchanged.

## Historical proposed fix sequence (not approved)

The following proposals are retained as investigation history. Step 4 was
rejected by [decision 0016](../../.agents/skills/recalo-decisions/references/0016-do-not-retain-original-images.md).
The remaining items require evidence and a separate implementation decision;
they are not a delivery checklist.

1. Validate required numeric fields before deserialization can silently fill
   them with zeros. Reuse validation for both the primary and fallback model
   paths. Missing data should use the existing invalid-response failure flow.
2. Distinguish an unrecognized meal from a valid zero-calorie subject. Calories
   equal to zero alone must not reject water or other valid zero results. An
   explicit recognition outcome would make this distinction clearer than an
   arbitrary confidence threshold.
3. Offer **Adjust image and reanalyze** for completed results as well as failed
   analyses. Show a crop preview so the user can retain every dish; do not
   silently assume the food is in the center. Keep the same meal ID and original
   preview, prevent concurrent retries, and replace existing nutrition only
   after the new result passes validation. Preserve the previous result if
   reanalysis fails. This requires a deliberate extension of the current
   error-only retry state transition.
4. Preserve a source image for future edits if original-resolution cropping is
   required, with cleanup/reference handling. Existing records only have the
   compressed JPEG and may need the user to select the original again.
5. If comparison confirms the benefit, fix the initial downsampling to decode
   above the target and then scale down to 1280. Benchmark memory use on large
   images. Merely upscaling an existing saved JPEG does not recover detail.

Automatic image adjustment during retry remains an experiment until the photo
comparison establishes an effective transformation. Network/authentication
failures should not trigger image changes. Production behavior is unchanged by
this investigation.
