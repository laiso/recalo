# ADR 0003: Estimate Nutrition with a Hosted Vision Model

- Status: Accepted
- Date: 2026-03-14

## Context

The core feature is turning a photo of a meal into calories, protein, fat, and carbohydrates with per-item detail. The repository contains no on-device model, no classifier assets, and no food database; the feature was built directly against a provider API.

Free-form meals do not map cleanly onto a fixed food table, and the app has no barcode scanning or portion measurement input to fall back on.

## Decision

Send the photo to a hosted vision model and treat the model's structured JSON response as the nutrition data. Use the OpenAI Responses API endpoint (`https://api.openai.com/v1/responses`) with a system prompt that instructs the model to return nutrients and detected items, and with a user-selectable output language.

The request carries the compressed JPEG as base64 plus the prompting configuration. The response is parsed into a title, total calories, confidence, and item-level nutrition.

## Consequences

- Analysis quality and availability are outside this project's control; a provider outage or model change becomes a user-visible failure (decision 0006).
- Every analysis needs network access and a paid key (decision 0002), and the user's photo leaves the device.
- The image is downsampled before upload to bound cost and latency (decision 0005), which trades detail for a smaller payload.
- The response is trusted as numeric input; a response with missing numeric fields deserializes to zero, which is why an all-zero result is treated as reportable rather than as a valid meal (decision 0010).
- Model choice is a configuration concern rather than a code concern, so switching models does not require changing the request shape (decision 0004).
