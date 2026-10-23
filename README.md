# Mi 12 Lite Front LED Controller v2.0

Experimental Android app for Xiaomi 12 Lite (taoyao) / HyperOS, controlling both selfie LEDs via the standard Camera2 torch API.

## Features
- ON / OFF for front LEDs, with camera ID 1 preferred when available.
- Brightness strength slider **only if** the front Camera2 characteristics report `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL > 1`. Unsupported devices get fixed-brightness torch, not a fake dimmer.
- Auto-off safety options: 30 seconds, 2 minutes (default), 5 minutes.
- Torch state/unavailable callback for diagnostics.
- Automatic torch shutoff when the app goes into the background, for safety. Thus this version is **not** a background torch service or a WhatsApp call light.
- Shizuku connection and read-only LED diagnostics retained.

## Build
Push to `main` or start **Build Android APK** in GitHub Actions. Download `Mi12LiteLEDTester-debug-APK` from the completed run's Artifacts. Debug APK signing requires no secrets.

## Important limitations
Shizuku through Wireless Debugging runs as UID `shell`, not root. On the tested Xiaomi phone, `/sys/class/leds` is inaccessible and camera V4L2 devices are owned by `system:camera`. This version **does not** bypass `CAMERA_IN_USE` during WhatsApp video calls. Changing brightness may also be unsupported by the device; the app checks reported support instead of assuming it.

## Optional release signing
Use repository Actions secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Never commit private keystores.
