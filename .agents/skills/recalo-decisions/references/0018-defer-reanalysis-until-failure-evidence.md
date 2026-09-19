# ADR 0018: Defer Re-analysis Until Failure Evidence Is Reviewed

- Status: Accepted
- Date: 2026-09-19
- Supersedes: [0017 — Save nothing when a re-analysis fails](0017-save-nothing-on-reanalysis-failure.md)

## Context

The reported problem is a meal whose nutrition values are all zero even though
analysis is stored as `completed`. The maintainer reports that resending the
same image frequently gives zero again, while cropping and submitting it again
can succeed. The direct API checks with supplied screenshots did not reproduce
this failure; they did not use a confirmed copy of the actual failed upload.
Neither observation establishes whether image content, resolution, encoding,
provider output, parsing, or persistence caused the incident.

Decision 0017 assumed that a completed meal has non-zero values. That assumption
excludes the case motivating recovery. The current reportability predicate also
accepts completed meals with all-zero or missing values. Leaving those values
unchanged does not make `persistDiagnosticSession` discard a diagnostic session.
The claim in 0017 that existing behavior automatically implements its no-record
policy is therefore incorrect for this case.

## Decision

- Defer implementation of re-analysis of completed meals while the failure
  evidence is collected and reviewed. Do not present unchanged-image resubmission
  as a demonstrated fix or select an automatic image transformation yet.
- Define the problem as recovery from an unsuitable analysis result, including
  all-zero completed meals, rather than assuming replacement of a valid result.
- Use the existing user-sent diagnostic ZIP to compare the actual sent image,
  raw provider response, parsed values, and stored values. Separate observations
  from hypotheses and from controlled reproductions.
- Keep photo re-selection, including a user-prepared crop, as the smallest
  candidate recovery flow under decision 0016. It is a proposal, not an
  implemented feature or a confirmed remedy. Preserve the meal identifier and
  previous photo, nutrition, and status if a future replacement attempt fails.
- Decide diagnostic retention and reporting for replacement attempts separately
  from preserving meal data. Withdraw 0017's blanket prohibition on diagnostic
  records; do not replace it with an implementation requirement to retain every
  attempt. The retention policy and report action for this future flow remain
  open and must be settled before implementation.
- Keep current feature contracts and error-only retry behavior unchanged. A
  legitimate zero result, such as water, must not become an error merely because
  its nutrition values are zero.

## Consequences

- No application code, schema, retry action, or diagnostic retention behavior
  changes as part of this decision. Original images are still not retained.
- The investigation's earlier fix sequence is historical proposal material,
  not an approved implementation plan. Its source-image retention proposal was
  already rejected by decision 0016.
- Review a failing diagnostic capture before choosing a fix. Compare an actual
  failing image with a successful crop using matched model/request settings;
  include re-encoding and resizing controls if needed to isolate the cause.
  If evidence remains unavailable, record that limitation rather than claiming
  the cause or a successful remedy.
- Before implementing a recovery flow, settle result validation, diagnostic
  retention/reporting, concurrency, and photo/result replacement semantics.
  Update the affected feature specifications with the implementation and verify
  that failure preserves the previous meal. A second all-zero response is not
  evidence that recovery succeeded merely because the API returned HTTP 200.
