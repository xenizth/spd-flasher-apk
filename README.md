# SPD Flasher (Android app around spd_dump)

Android app with buttons for the common Unisoc/Spreadtrum download-mode jobs:
flash an `.img`, erase `persist`, disable/enable verity (+ verification), back up
partitions, reboot modes, plus a custom-command box and a live log.

It bundles the vendored TomKing `spd_dump` from
https://github.com/Seuj09/Spd_dump_termux (`no-root/spd_dump`, pin f2fc779) and
libusb 1.0.27, compiled into the app. Android's `UsbManager` opens the phone
(OTG), and the file descriptor is handed to a forked `spd_dump` process
(`--usb-fd`). No root and no Termux needed.

## Build the APK (no Android Studio needed)

1. Create a new GitHub repository and push this folder to it (the
   `.github/workflows/build-apk.yml` file must be included).
2. Open the repo's **Actions** tab -> **Build APK** -> wait for the green check.
3. Open the run, download the artifact **SpdFlasher-debug-apk**, unzip it and
   install `app-debug.apk` (allow "install unknown apps").

Two workflows are included (folder `.github/workflows/`, a hidden dot-folder):
`build-apk.yml` builds on every push; `release.yml` publishes an APK to the
Releases page when you push a tag such as `v1.0`.

Or locally with Android SDK 34 + NDK 26.3.11579264 + CMake 3.22.1:
`./gradlew assembleDebug` -> `app/build/outputs/apk/debug/app-debug.apk`.

## Use

1. Pick FDL1/FDL2 for your exact chip and enter their load addresses
   (or tap the Infinix UMS9230 example). `exec_addr` is only needed for the
   BROM signature-bypass route.
2. Tap an action. The app waits for the device (default 60 s): hold the
   download-mode key combo and plug in the OTG cable, then tap OK on the
   Android USB permission dialog.
3. Keep the screen on until it says finished.

What the buttons run (spd_dump syntax):

| Button | Commands after `fdl ... exec` |
| --- | --- |
| Flash image | `w <partition> <file>` |
| Erase persist | `[path <backups> r persist] e persist` |
| Disable verity | `verity 0` (vbmeta flags byte = 1, hashtree only) |
| Disable verity + verification | `wov vbmeta 0x78 0x03000000` (vbmeta flags = 3) |
| Re-enable verity | `verity 1` |
| Back up partition | `path <backups> r <partition>` |
| Reboot buttons | `reset`, `reboot-recovery`, `reboot-fastboot`, `poweroff` |

Backups are written to the app's external files folder (path shown in the log);
use **Export backups** to copy them somewhere you can reach.

## Honest limitations

* Not tested on a phone. The native layer was compile-checked and linked, and the
  fork/pipe/exit and descriptor hand-off paths were run on a PC; the Android UI
  and the real USB path have not been run.
* **Reconnect (new).** If the phone drops off USB and comes back after FDL1 starts,
  the app now reconnects by itself: spd_dump notices the dead descriptor during
  the FDL1 CHECK_BAUD step, asks the app for a new one, the app waits for the
  Unisoc device to return (up to 90 s), asks for USB permission again if needed
  (tap OK, or tick "use by default" on the Android dialog so it is automatic),
  and hands the new descriptor to spd_dump, which carries on. It only reconnects
  during that FDL1 start window; if the cable is pulled mid-flash the session
  fails instead of resuming a half-written partition. The descriptor hand-off was
  tested on a PC; the USB side has not been tested on a real phone.
* The source repo says FDL2 / full flash success on the no-root path is **not
  proven yet**, so treat the first runs as experiments.
* A wrong loader/address or partition can brick the phone. Erasing `persist`
  loses calibration and MAC data; back it up first.
* No foreground service: do not leave the app during a flash.

## Licences

`app/src/main/cpp/spd_dump/` is vendored from TomKing062/spreadtrum_flash (see
NOTICE; check its upstream licence before distributing a built APK).
libusb is LGPL-2.1. `LICENSE-spdhost-repo.txt` is the MIT licence of the
Spd_dump_termux repo.
