# SharkOS

A light, iOS-style launcher for the BYD Shark 6 head unit (Desay SV, Android 11, 1920×1080).

- **Home**: clock, Now Playing with controls, battery / electric range / fuel / total range, tyre pressures, live pitch and roll.
- **Bottom bar on every screen**: passenger and driver seat heat and cooling, both temperatures, Home, your pinned apps, all apps, A/C, and the climate page (which also shows the fan speed).
- **Climate**: power, both temperatures, fan 1–7, airflow, Auto, A/C, recirculation, front and rear demist, sync zones, seat heat and cooling per seat.
- **Apps**: everything installed, A to Z. Hold an app to pin it to the bar (up to 5).
- **Off-road**: pitch and roll dials from the head unit's motion sensor, with a zero for how the screen is mounted.
- **Full screen**: hides BYD's status bar and bottom bar while SharkOS is in front (swipe from an edge to see them).

Release APK is about 2 MB. No internet permission, no background services; car polling, the clock and
the motion sensor all stop when SharkOS isn't on screen.

## Install on the car

1. Turn on Wi-Fi ADB on the head unit: **Settings → System → Version**, tap **Factory Reset** about 10 times
   (this unlocks developer access on BYD units; don't confirm any reset dialog). Put the car and your
   computer on the same Wi-Fi (a phone hotspot works).
2. Find the car's IP address (Settings → Wi-Fi → the connected network), then on your computer:
   ```
   adb connect <car-ip>:5555
   adb install -r SharkOS-0.1.1.apk
   ```
3. Open SharkOS from BYD's app list. Allow the one car-data permission it asks for.
4. **Make it the Home screen:** SharkOS → Settings → Use SharkOS as the Home screen → Choose. If the car
   doesn't offer a choice:
   ```
   adb shell cmd package set-home-activity com.sharkos.launcher/.MainActivity
   ```
5. **Now Playing:** SharkOS → Settings → Media access → Allow. If that settings page doesn't open on the car:
   ```
   adb shell cmd notification allow_listener com.sharkos.launcher/com.sharkos.launcher.media.MediaListener
   ```
6. On level ground, open Off-road and tap **Zero on level ground**.

Updates install the same way (`adb install -r`), and **must be signed with the same key** (`sharkos-release.jks`).

## What's verified, and what still needs the car

Verified here: builds, every screen rendered at 1920×1080, and the real app started and navigated on a
simulated Android with no car (all controls respond).

Needs checking on the car:
- Climate and seat commands. They use Shark Hub's mapping for the Shark 6; Shark Hub confirmed driver
  temperature on a real car, the rest is wired the same way but untested.
- Whether BYD's bottom climate bar hides. The top status bar is known to hide; the bottom bar may be
  drawn by BYD outside Android's navigation bar.
- Whether the head unit lets a third-party app be the Home screen.

## Build

JDK 17 or 21, Android SDK 34. `local.properties` with `sdk.dir=...`, and `keystore.properties` next to the
key for signed builds:
```
storeFile=sharkos-release.jks
storePassword=...
keyAlias=sharkos
keyPassword=...
```
```
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew recordPaparazziDebug     # screen renders -> app/src/test/snapshots/images
./gradlew testDebugUnitTest        # renders + start-and-navigate test
```

## Credits

The car connection (`car/`), the Shark 6 climate mapping and the inclinometer are adapted from
[Shark Hub](https://github.com/Peacemaker105/SharkHub) by Chris Murray, MIT licence (see `LICENSE-SharkHub`).
Figtree typeface by Erik Kennedy, SIL Open Font Licence.
