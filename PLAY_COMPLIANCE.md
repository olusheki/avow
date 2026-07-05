# aVow (lite) — Google Play Console answer sheet

Ready-to-paste answers for the Play Console forms when submitting the **lite** build (`assembleLiteRelease`).
Not legal advice — verify against the current Play policy pages before submitting.

> **Do NOT set `android:accessibilityTool="true"`** anywhere. aVow is not an accessibility tool; from
> Oct 2026 that flag is restricted to apps for users with disabilities. aVow relies on the prominent
> disclosure + Permissions Declaration Form route instead.

---

## 1. App content / category

- **Category:** Productivity. (Do **not** choose Parental Controls — that pulls in the Families
  policy and extra requirements; aVow is self-imposed, not for supervising others.)
- **Store listing:** describe it as a self-control / digital-wellbeing app that blocks apps and
  websites the user chooses. Avoid "monitor," "track," or "spy" language.
- **Privacy policy URL:** the hosted URL of `PRIVACY_POLICY.md`.

## 2. AccessibilityService — Permissions Declaration Form

**Is your app's use of the AccessibilityService API permitted?** Yes — describe the single core
feature:

> aVow is a digital-wellbeing app. Its core feature is blocking apps and websites that the user has
> chosen to restrict during self-imposed focus sessions ("vows") and scheduled blocks. It uses the
> AccessibilityService to detect which app is in the foreground and to read the browser address bar,
> so it can present a blocking screen when the user opens something they asked to be blocked. No
> screen content is collected, stored, or transmitted; all processing is on-device.

- **Is it an accessibility tool (isAccessibilityTool)?** No.
- **Prominent disclosure + consent:** Yes — shown in onboarding before the user is sent to enable the
  service (`AccessibilityDisclosureDialog`), stating what is accessed, why, and that nothing leaves
  the device. Record a short screencast of this flow for the submission if asked.
- **Narrower APIs considered:** Foreground-app detection could partly use UsageStatsManager, but
  reliable real-time interception and browser-URL blocking require the AccessibilityService; the app
  uses the minimum scope needed (foreground package + address bar only).

## 3. Other sensitive permissions

- **PACKAGE_USAGE_STATS (usage access):** declared for the digital-wellbeing screen-time figures.
  User grants it explicitly via system settings; used on-device only.
- **QUERY_ALL_PACKAGES:** **not requested.** Replaced by a scoped `<queries>` launcher-intent
  declaration (we only list launchable apps for the picker).
- **Foreground service (VPN):** the optional "Block in all browsers" domain filter runs a
  `specialUse` foreground service (`DomainVpnService`). Declare the `specialUse` subtype in the
  Console: _"On-device DNS content filter that blocks the websites the user chose, in every browser.
  No traffic leaves the device."_ It only runs when the user turns the toggle on and grants the VPN
  consent dialog. If Google pushes back on `specialUse`, the feature can be shipped off by default or
  removed from lite without affecting the rest of the app.
- **VpnService / local VPN:** aVow runs a **local, no-server** VpnService. It routes only the
  app-facing DNS address through the tunnel and either sinkholes blocked domains or forwards other
  DNS queries to a public resolver over a protected socket. It does **not** proxy, inspect, or
  transmit user traffic. Note this in the listing so reviewers don't mistake it for a remote VPN.
- **POST_NOTIFICATIONS:** standard runtime permission; no declaration needed.
- **RECEIVE_BOOT_COMPLETED:** to re-establish state after reboot.
- **INTERNET:** used only by the local domain-filter VPN to forward non-blocked DNS lookups to a
  public resolver. No app/user content is transmitted; aVow has no backend.

## 4. Data Safety form

- **Does your app collect or share any user data?** No.
  - Rationale: Play defines "collection" as transmitting data off the device. aVow transmits nothing;
    the accessibility- and usage-derived data is processed on-device and never leaves it. The local
    session history is on-device app storage, not "collection."
- **Is data processed ephemerally?** The foreground-app / URL signals are used in real time and not
  stored — you may note this.
- **Is data encrypted in transit?** N/A (no transmission).
- **Can users request deletion?** Uninstalling removes all local data.

## 5. Pre-submission checklist

- [ ] Host `PRIVACY_POLICY.md` at a public URL and put the URL in the listing (contact email `avowtheapp@gmail.com` already set in the policy and in-app Settings).
- [ ] Build & upload `assembleLiteRelease` (applicationId `com.avow.app`, "aVow").
- [ ] Confirm the lite manifest has no device-admin surface and no QUERY_ALL_PACKAGES (both verified
      in the flavor-split / compliance branches).
- [ ] Complete the AccessibilityService Permissions Declaration Form with the §2 text.
- [ ] Complete the Data Safety form per §4.
- [ ] Have a screencast of onboarding → accessibility disclosure → enable, in case review asks.
- [ ] Set `targetSdk` to the current Play requirement (currently 36 — fine).
