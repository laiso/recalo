# Settings and credentials specification

## Requirements

### Opening settings

- The app shall open the settings dialog when the user taps the settings control in the summary card.
- The app shall open the settings dialog when the user taps the `OpenAI API key is not configured. Tap to set up.` banner.
- The app shall open the settings dialog when a capture path detects that no key is stored.
- The app shall not provide a logout or clear-key action.

### Contents

- The app shall show the sections `OpenAI API Key` and `Analysis Model Quality`.
- The app shall mask the key field with a password transformation and a password keyboard type.
- The app shall offer the link `Get API Key from OpenAI`, which opens `https://platform.openai.com/api-keys`.
- The app shall offer exactly three model quality choices, with the labels, descriptions, and model identifiers in decision 0004.
- While the key field is blank, the app shall disable `Save`.
- The app shall not validate the key format and shall not verify the key against the provider before saving.

### Persistence

- When the user saves, the app shall store the key encrypted with the Android Keystore and shall store the selected model level (decision 0002).
- When the user saves, the app shall close the dialog.
- When no model level is stored, the app shall use `low`.
- When the stored level is not recognized, the app shall use `low`.
- When the app restarts, the app shall restore the stored key and model level.
- If the key cannot be decrypted, then the app shall treat the key as absent and the banner of a missing key shall be shown.
- If the encrypted preference store cannot be created, then the app shall fall back to plain preferences rather than failing.

## Acceptance scenarios

```gherkin
Feature: Settings and credentials

  Scenario: AT-SETTINGS-001 — Save a key and a model level
    Given the settings dialog is open
    And the key field contains a non-blank value
    When the user selects "High (gpt-5.4)" and taps "Save"
    Then the key is stored encrypted
    And the model level is stored as high
    And the dialog closes
    And subsequent analyses request gpt-5.4

  Scenario: AT-SETTINGS-002 — Require a key before saving
    Given the settings dialog is open
    And the key field is blank
    Then "Save" is disabled
    And the model quality choices remain visible

  Scenario: AT-SETTINGS-003 — Restore settings after a restart
    Given an encrypted key is stored
    And the model level is medium
    When the app is relaunched
    Then the key is available to the app without re-entry
    And analyses request gpt-5.4-mini

  Scenario: AT-SETTINGS-004 — Undecryptable key
    Given the stored ciphertext cannot be decrypted
    When the app reads the key
    Then the app treats the key as absent
    And the missing-key banner is shown
    And the app does not crash

  Scenario: AT-SETTINGS-005 — Reach settings without a key
    Given no key is stored
    When the user taps the add button and selects "Take Photo"
    Then the settings dialog opens
    And no camera app opens
```

## Automation status

- Automated by instrumented tests only, device required: the Android Keystore round-trip, randomised ciphertext, invalid base64, empty string, and a 10 000-character value (`SecurityUtilsTest`). This test never runs in CI (decision 0012).
- Not covered by any test: `SessionManager` itself, including saving, reading, clearing, the default and unrecognized model level, the decryption-failure path, and the plaintext fallback. Also uncovered: the settings dialog, its save-enabled rule, and the `Get API Key from OpenAI` link.

## Known deviations

- The default level is `low`, which is the cheapest and lowest-quality model, and the settings dialog does not say that this is the default.
- The plaintext fallback on encrypted-preference failure is silent to the user, so a device in that state stores the key less protectively without saying so.
- The price strings in the quality options are hardcoded and must be updated by hand when provider pricing changes.
- Quitting and relaunching is required to confirm `AT-SETTINGS-003`; no test covers it.
