# Domain-filter VPN — on-device test plan

The packet logic (DNS parsing, sinkhole responses, IP/UDP checksums) is unit-tested in
`DnsPacketTest`. Everything below is the runtime plumbing that unit tests can't cover, so it needs
real on-device testing. **The most dangerous failure mode is a checksum/forwarding bug that breaks
ALL DNS while the VPN is on (device loses internet)** — test #3 and #4 catch that first.

## Setup
1. Add a domain to the **BAN DOMAIN SET** (e.g. `instagram.com`).
2. Settings → **BLOCK IN ALL BROWSERS** → toggle on → accept the system VPN consent dialog.
3. Confirm the VPN key icon appears in the status bar and the "domain filter active" notification shows.

## Core behavior
1. **Block works, any browser.** Open `instagram.com` in Chrome, then Firefox/Brave/DuckDuckGo/Edge
   and the Samsung browser. All should fail to load (site can't be reached / server not found).
   The point of this feature is browser-agnostic blocking — verify a non-Chrome browser specifically.
2. **Subdomains blocked, look-alikes not.** `www.instagram.com` and `m.instagram.com` should be
   blocked; `notinstagram.com` should load normally.
3. **⚠️ Non-blocked domains still resolve.** Open several unrelated sites (google.com, wikipedia.org,
   a banking app, an email app). Everything not on the ban list must work normally. If *any* unrelated
   site fails, the DNS forwarding path is broken — stop and report.
4. **⚠️ Toggle off restores everything.** Turn the toggle off. The VPN icon/notification should
   disappear and all domains (including the previously-blocked one) should resolve again immediately.

## Lifecycle / persistence
5. **Add a domain while on.** With the VPN on, add another domain to the ban set — it should start
   being blocked within a few seconds (the service watches the ban set live).
6. **App killed / backgrounded.** Swipe the app away, wait, reopen. The VPN should still be running
   and still blocking (foreground service should keep it alive).
7. **Reboot.** Restart the phone, open aVow. The VPN should re-establish on launch (we restart it if
   the toggle was on and consent is still valid). _Known gap: it re-establishes on app open, not
   automatically at boot — note if that matters to you._
8. **Consent revoked.** Settings → Network → VPN → revoke aVow's VPN permission. The filter should
   stop cleanly (no crash). Re-toggling should re-prompt for consent.

## Known limitations / edge cases to check (expected, but confirm behavior)
9. **Private DNS (DoT).** If you have Settings → Private DNS set to a hostname (e.g. `dns.google`),
   DNS goes encrypted straight to that resolver and **bypasses our filter** — blocking won't work.
   Confirm this is the case on your device; the workaround is Private DNS = "Automatic"/off.
10. **Browser Secure DNS / DoH.** Chrome's "Use secure DNS" (Settings → Privacy) also bypasses the
    filter. Confirm; document for users that DoH must be off for domain blocking.
11. **IPv6.** We answer `A` (IPv4) with a sinkhole and return NODATA for `AAAA`, so IPv6-only paths
    shouldn't leak — but verify on an IPv6 network that a blocked site still fails.
12. **Cached DNS.** A domain resolved just before enabling the VPN may stay reachable briefly (OS/app
    DNS cache). Blocking is reliable after the TTL expires or the app is reopened.
13. **Coexistence with accessibility URL blocking.** In Chrome/Samsung Internet, both the VPN and the
    accessibility URL check may fire — that's fine (both block), just confirm no double-overlay flicker.
14. **Battery / performance.** DNS forwarding is synchronous (one query at a time). Heavy simultaneous
    DNS load (many tabs opening at once) could feel slightly slower. Confirm it's acceptable.
15. **Another VPN active.** Android allows only one VPN at a time. If the user already has a VPN,
    enabling this replaces it (Android handles the hand-off). Confirm the consent dialog explains that.

## If something's wrong
- **All internet dies with VPN on** → forwarding/checksum bug. Capture `adb logcat -s DomainVpnService`
  and report; the sinkhole vs forward branch or `buildUdpResponse` is the suspect.
- **Blocked site still loads** → check Private DNS / browser DoH (#9, #10) first; then confirm the
  domain is actually in the ban set and the toggle/consent are on.
- **Service dies when backgrounded** → foreground-service/notification issue; report the Android
  version.
