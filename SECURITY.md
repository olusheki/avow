# Security Policy

aVow deliberately restricts what a device can do, so security cuts both ways here: the risk of a vow
being **bypassed**, and the risk of a user being **locked out unexpectedly** — especially in the full
(Device Owner) edition, where a bug could be hard to recover from. Both are taken seriously.

## Reporting a vulnerability

Please report vulnerabilities **privately** — do not open a public issue.

📧 **avowtheapp@gmail.com**

Include:

- what you found and its impact,
- steps to reproduce,
- the edition (lite or full) and your Android version.

You'll get an acknowledgement within a few days, and updates as a fix and disclosure timeline come
together.

## In scope

- Bypassing an active vow — disabling enforcement, escaping a block, or defeating the tamper-evident
  vow state.
- Any bug that could lock a user out with no reasonable recovery path.
- Local data exposure (aVow keeps everything on-device).

## Out of scope

- Attacks that require a rooted device or physical access with developer tools already unlocked.
- The user intentionally removing Device Owner through the app's documented deactivation flow.

## Supported versions

aVow is pre-1.0; security fixes land on `main` and the latest release. Please reproduce against the
current `main` before reporting.
