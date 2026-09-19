# ADR 0002: Require Users to Bring Their Own OpenAI Key, Encrypted at Rest

- Status: Accepted
- Date: 2026-03-14

## Context

Analysis calls cost money and require an OpenAI credential. Without a backend (decision 0001), there is no server that could hold a shared key or proxy requests. A key therefore has to live on the device.

An API key in plain preferences is readable by anyone with access to the app's private storage or a rooted device, and it is easy to leak into logs.

## Decision

The app ships with no credentials. The user enters their own OpenAI API key in the settings dialog, and the app stores it encrypted on the device.

- The key is encrypted by `SecurityUtils` using an AES-256-GCM key held in the Android Keystore (`caroli_openai_master`).
- The ciphertext is stored under `encrypted_openai_key` in `EncryptedSharedPreferences` (`caroli_prefs_encrypted`).
- The non-secret model quality choice is stored as a plain preference, `openai_model_level`.
- If encrypted preferences cannot be created at all, `SessionManager` falls back to plain `SharedPreferences` (`caroli_prefs`) and logs the failure, so the user is not locked out of the app.
- The app never validates the key format and never verifies it against the API before saving.

## Consequences

- A user with no key sees the banner `OpenAI API key is not configured. Tap to set up.` and capture paths open Settings instead of analyzing.
- The key is not protected by biometric or device-credential authentication; a rooted or compromised device can recover it.
- The plaintext fallback means the encryption guarantee is best-effort. A failure there is silent to the user and visible only in logcat.
- Diagnostics must redact the key: `SecretRedactor` removes the active key, bearer tokens, `sk-` shaped strings, and credential-shaped JSON fields from responses, exceptions, archives, and logs (decision 0010).
- Every user pays for their own analysis usage, and setup requires an OpenAI project with access to the selected model (decision 0004).
