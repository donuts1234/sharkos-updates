# SharkOS — handover (current as of 0.1.30, 2026-10-15)

Custom Android launcher for the BYD Shark 6 head unit (Desay SV, Android 11, 1920×1080 @ 240dpi, RHD).
Kotlin + Jetpack Compose. Deliberately lightweight (Compose foundation only, no Material).
iOS-like, clean, modern design.

## How to pick up
- Source: `/home/claude/sharkos` (local git, **no remote** — back this up to GitHub).
- Releases: GitHub `donuts1234/sharkos-updates` (APK + latest.json). `tools/release.sh "notes"` builds, signs, pushes.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` before each release.
- Tests: Paparazzi snapshots + Robolectric. `./gradlew :app:testDebugUnitTest`. Record snapshots with `recordPaparazziDebug`.
- Design board (mockups): Design artifact, canvas in `scratchpad/canvas/project`.

## Working rules (from the user)
- Discuss what each new version will change BEFORE publishing. Only publish on an explicit "publish".
- Supply a mockup on the design board for each feature that needs one; let the user pick before building.
- adb/PowerShell commands as single lines. Prefer "one line → one file → paste back" for pulling car data.
- Don't mix in other BYD models' code. Shark 6 only.
- CarPlay HUD directions must keep working; BYD's CarPlay app can't be disabled.

## Published: 0.1.30
Big features shipped over 0.1.x: HVAC bar, home cards, climate page, trips, tyre alerts,
ambient LED control (album-art + wallpaper match, exact-RGB fades, night dimming),
CarPlay-with-HVAC-bar, full-screen music page, **instrument cluster** (8 designs + picker pill +
bubble, starts on boot/wake, grey background 21,21,21), **night mode** (moon pill, 2 looks),
4px top edge. 0.1.30 = cluster reliability (view-setting fix, watchdog, service reconnect) +
CarPlay-map-in-cluster test (off by default, layouts A/B).

## Instrument cluster — how it works
- Draws on Android display 1 via Desay cluster service `com.ts.appservice.cluster` (ClusterLink.kt).
- Map slot **1** (BYD's nav) = the one that works. Slot 3 wakes BYD's Android Auto app which blacks it out.
- Cluster's own nav view setting must be 3 (small map) or 4 (full map); on 2 (simple) it shows
  "no navigation running" and SharkOS is invisible. Watchdog re-asserts this every 10s.
- CarPlay map test: during a route SharkOS keeps the cluster, pulls the iPhone's cluster map onto its
  own surface (CarPlayLink.clusterSurface/clusterStream, screen type 1, needs the "picture id" from
  onSuggestUi), draws next-turn + arrival from the route-guidance callback. UNTESTED in car — watch the HUD.

## Next up: 0.2.0 ideas + the camera investigation
- **Blind-spot camera in cluster** (user's main 0.2.0 goal): camera pixels on the Shark 6 are only
  reachable via Qualcomm QCarCam / `libais_client.so` (how the Overdrive dashcam app does it), which
  needs privileged self-escalating shell access. **Claude declined to build that mechanism** (it's the
  shape of malware tooling, regardless of the legitimate goal). BYD's panorama API is control/state
  only — no video surface. Clean alternative: trigger the car's OWN blind-spot view on indicate via
  BYD's panorama API + a user-granted BYDAUTO_PANORAMA permission (appears as BYD's view, not in cluster).
  - NEXT: user to send the **SharkSight** (Sharkware) dashcam APK. Analyse how it records 4 cameras —
    possibly the car's built-in DVR system (`com.byd.dvr`, `dvr@1.0-service`, `hyp_dvr*`), which could
    be a cleaner door than Overdrive's. Decide from there.
- Indicator detection is clean and easy (BYD light service, already used for night dimming).
- Parked 0.2.0 list: drive history/costs, doors/windows card, speed pill, service reminders,
  routines, driver profiles, up-next queue, standby clock, settings backup, "what's new" card.
  Also: auto-switching cluster designs (Night on headlights, Climate pop-up, Charging, Off-road, Welcome).

## Known untested-in-car
- 0.1.30 cluster fixes; CarPlay-map-in-cluster test; CarPlay route-end hand-back; wake-from-sleep auto-launch.
- "Keep as launcher" doesn't stick (BYD resets it). User to run from PC:
  `adb shell "cmd role add-role-holder android.app.role.HOME com.sharkos.launcher; cmd package set-home-activity com.sharkos.launcher/.MainActivity"`
