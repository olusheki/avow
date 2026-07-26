# aVow — Privacy Policy

_Last updated: 2026-07-26_

aVow is a digital-wellbeing app that helps you block distracting apps and websites that **you**
choose. This policy explains what the app accesses, why, and what happens to it.

**The short version: aVow has no servers, no accounts, no analytics, and no advertising, and it
collects nothing about you.** Everything it needs stays on your device — with one exception: if you
turn on the optional "block in all browsers" filter, your device's ordinary DNS lookups are sent to a
public resolver (Cloudflare's `1.1.1.1`) so they can be resolved, exactly as they would be by any DNS
server. aVow itself still receives and stores nothing.

## What the app accesses, and why

**Accessibility Service.** To enforce the limits you set, aVow uses Android's Accessibility Service
to read (a) which app is currently in the foreground and (b) the address bar of your browser. It uses
this only to detect when you open an app or website you asked it to block, and to show the blocking
screen. This information is processed on your device in real time and is **not** recorded, stored, or
sent anywhere.

**Usage access (UsageStatsManager).** With your permission, aVow reads your device's app-usage
statistics to show you how much time you spend on social media (for example, the onboarding "how much,
really?" comparison and the mascot's weekly reflection). These figures are computed on your device and
are **not** stored off-device or shared.

**Notifications.** aVow can post a notification (for example, a doomscroll warning). This does not
involve collecting any data.

## What is stored, and where

The only things aVow stores are kept **locally on your device**:

- Your settings and active vow state (in the app's private storage).
- A local history of your completed focus sessions (duration, device pickups, and a "zen score"),
  kept in a private on-device database so the app can show you your Focus Insights.

None of this leaves your device. Uninstalling aVow deletes all of it.

## What aVow does NOT do

- It does **not** collect or transmit your browsing history, the contents of any app, keystrokes,
  messages, or personal information.
- It does **not** use the Accessibility Service to read screen content beyond the foreground app
  package name and the browser address bar needed to block sites.
- It uses the internet permission **only** so the optional "block in all browsers" domain filter can
  forward your device's ordinary DNS lookups to a public resolver (Cloudflare's `1.1.1.1`). Blocked
  domains are dropped on your device and never sent anywhere; aVow has no server of its own and
  uploads **none** of your data.
- It does **not** share data with any third party, because it has no data to share.
- It is **not** an accessibility tool and does not represent itself as one.

## Children

aVow is a general-purpose productivity app and is not directed at children.

## Changes

If this policy changes, the updated version will be posted at this URL with a new "Last updated" date.

## Contact

Questions about this policy: **avowtheapp@gmail.com**

> Hosting note: publish this file at a public URL (e.g. GitHub Pages) and put that URL in the Play
> Console listing.
