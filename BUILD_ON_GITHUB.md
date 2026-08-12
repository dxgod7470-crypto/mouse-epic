# Build the APK using only your phone

1. Create a GitHub account and a new empty repository.
2. Upload all files from this project to the repository (including `.github/workflows/build-apk.yml`).
3. Open the repository's **Actions** tab.
4. Select **Build APK**.
5. Tap **Run workflow** if it has not already started.
6. Wait for the green check.
7. Open the completed workflow run.
8. Scroll to **Artifacts**.
9. Download `MouseConfigurator-v4-debug`.
10. Extract it and install `app-debug.apk` on your phone.

No Android Studio or AndroidIDE is required on your phone.

If GitHub says Actions are disabled, open the repository's Actions settings and enable workflows, then run the workflow again.

The generated APK is a debug build intended for testing.
