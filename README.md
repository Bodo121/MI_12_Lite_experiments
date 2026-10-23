# Mi 12 Lite Front LED Tester

Android Camera2 torch test for Xiaomi 12 Lite on HyperOS 1. Front camera IDs are discovered at runtime. Tests auto-stop after three seconds.

## Build
GitHub Actions -> Build Android APK -> Run workflow, or push to main. Download the debug APK from run artifacts. Debug builds are signed automatically by Android Gradle Plugin; no secrets needed.

## Optional release signing
Create a private Android signing keystore and add repository Actions secrets: KEYSTORE_BASE64 (base64 encoded keystore), KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD. Never commit your keystore or passwords.

This is experimental: reported flash support does not guarantee torch access, independent LED control, or success during video calls.
