# Mouse Configurator v4

Mobile-oriented Android project with a PC-style mouse behavior panel.

Features:
- Pointer speed
- X/Y sensitivity
- Raw-input profile mode
- Acceleration toggle + strength
- Smoothing/filter
- Linear, Soft, Windows-like, Aggressive response-curve profiles
- Scroll speed
- Y inversion
- Mouse/keyboard device information
- Live raw mouse event tester and approximate event-rate monitor
- Shizuku status and permission request
- Minimal dependencies compared with the Compose build

Important:
The settings are a processing/profile model and diagnostics. A normal Android app cannot globally rewrite another app's raw mouse stream merely by having these settings. Shizuku also does not grant unrestricted input injection. Any future system/game routing must use a supported Android mechanism.

Build with Android Studio or a compatible mobile Android build environment.
