# Running & Debugging VeritasGuard

Due to Windows file path limits (260 characters), this project **MUST** be run from the `C:\VG` junction.

## Option 1: Command Line (CLI)

1. **Open PowerShell as Administrator** (required for `npx` permissions typically).
2. **Navigate to the short path**:
   ```powershell
   cd C:\VG
   ```
3. **Bypass Execution Policy** (if needed):
   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   ```
4. **Run the App**:
   ```powershell
   npx react-native run-android
   ```
   *This will start the Metro bundler in a new window and install the APK on your connected device/emulator.*

## Option 2: Android Studio

1. **Open Android Studio**.
2. **File -> Open...**
3. Navigate to **`C:\VG\android`**.
   *Do NOT open the original long path in `Documents`.*
4. Wait for Gradle Sync to finish.
5. Select `app` configuration and click **Run** (Green Play Button).

## Option 3: VS Code Debugging

1. Open the project in VS Code (you can open the original path `Documents\...` for editing).
2. **ensure the Metro Bundler is running** (Run `npx react-native start` from `C:\VG` in a terminal).
3. Go to **Run and Debug** tab.
4. Select **"Debug Android"** (I created this configuration for you).
5. Press **F5**.

## Troubleshooting

- **`ninja: error: Stat(...) Filename longer than 260 characters`**:
  You are trying to build from the long path. Close everything and switch to `C:\VG`.

- **`SDK location not found`**:
  Make sure `C:\VG\android\local.properties` exists and points to your SDK.
