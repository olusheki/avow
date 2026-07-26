# aVow — FAQ

Common questions about using aVow. Still stuck? Email **avowtheapp@gmail.com**.

### What is aVow?

A focus app that blocks distracting apps and websites you choose — and, unlike most blockers, is built
so you can't just switch it off the moment you feel the urge. You set a **vow** (a timer), and your
limits hold until it runs out.

### How does a vow work? Can I cancel it early?

You pick what to block and for how long, then start the vow. While it's running you can make it
**stricter or add time, but not shorten or cancel it**. That's by design — the commitment is the whole
point. When the timer ends, everything unlocks on its own.

### Help — aVow isn't blocking anything!

Almost always one of these, not a bug:

1. **The accessibility service is off.** Open aVow — if you see a red "ENFORCEMENT OFFLINE" banner, tap
   it and re-enable aVow in **Settings → Accessibility**. (Some phones turn it off via battery
   optimization; exclude aVow from battery optimization to keep it on.)
2. **No vow is running and you're in Passive mode.** Passive rules only enforce during a vow. Start a
   vow, or switch to Active mode.
3. **You're outside the block's window.** Scheduled blocks and the doomscroll shield only apply during
   the hours you set.

### How do I block a website in every browser?

Add the domain to your ban list, then turn on **"Block in all browsers."** This runs a local,
on-device DNS filter (shown as a VPN) that blocks the site everywhere, not just in Chrome. No traffic
goes to us — see the [Privacy Policy](PRIVACY_POLICY.md).

### What's the Doomscroll Shield?

Pick apps and an allowance (say, 15 minutes). If you spend past it in one of those apps — with a
stricter late-night window if you want — the app locks for a cooldown. Sneaking it into a pop-out
(picture-in-picture) or split-screen doesn't help; that's caught too.

### What's the difference between the two editions?

- **aVow** (Google Play) enforces with Android's accessibility service.
- **aVow Plus** (from GitHub) adds optional **Device Owner** locks that make a vow much harder to
  escape — blocking uninstalls, factory reset, and Safe Mode.

### I installed aVow Plus with Device Owner. How do I remove it or uninstall?

Use the app's **deactivation flow**: in Settings, start deactivation — this begins a **24-hour
cooling-off period** (so you can't undo a vow on impulse). After 24 hours, confirm, and aVow cleanly
removes its Device Owner role so you can uninstall it normally. Note that while a vow is active, some
escape routes (including factory reset) may be blocked on purpose.

### Does aVow collect my data?

No. No servers, no accounts, no analytics — everything stays on your device. The only thing that ever
leaves is ordinary DNS lookups when you enable the optional website filter, and those go to a public
resolver, never to us. Full details in the [Privacy Policy](PRIVACY_POLICY.md).

### Why does aVow need Accessibility and Usage Access?

- **Accessibility** is how it sees which app (or website) is in front so it can block the ones you
  chose. It reads only the foreground app and your browser's address bar — nothing else.
- **Usage access** is optional and only powers the screen-time figures you see (like the onboarding
  comparison).

### I'm stuck on a full-screen lockout — how do I get out?

That's the doomscroll cooldown or a block screen. **Double-tap anywhere** to return to aVow. This
doesn't end the cooldown — the blocked app stays blocked until it lifts — it just frees you from the
lockout screen.

### How do I get support?

Email **avowtheapp@gmail.com** with your device, Android version, and what happened. Found a security
issue? Please report it privately — see [SECURITY.md](SECURITY.md).
