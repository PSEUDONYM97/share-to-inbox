# Building the Android App

## Prerequisites

- Android Studio Arctic Fox (2020.3.1) or newer
- JDK 17
- Android SDK 34

## Setup

1. Open Android Studio
2. Select "Open an existing project"
3. Navigate to `share-to-inbox/android`
4. Wait for Gradle sync to complete

## Build Debug APK

```bash
cd android
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

## Build Release APK

1. Create a keystore (first time only):
   ```bash
   keytool -genkey -v -keystore release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias share-inbox
   ```

2. Create `android/keystore.properties`:
   ```
   storePassword=your_store_password
   keyPassword=your_key_password
   keyAlias=share-inbox
   storeFile=../release-key.jks
   ```

3. Build:
   ```bash
   ./gradlew assembleRelease
   ```

Output: `app/build/outputs/apk/release/app-release.apk`

## Install on Device

```bash
# Debug
adb install app/build/outputs/apk/debug/app-debug.apk

# Release
adb install app/build/outputs/apk/release/app-release.apk
```

## Testing

### Unit Tests
```bash
./gradlew test
```

### Verify TOTP Implementation
The app includes test vectors that must match the JavaScript implementation:
- secret: `0123456789abcdef...` (repeated), window: 0 -> `acbc9dd34781c8264d36e5754f663a64`
- secret: `0123456789abcdef...` (repeated), window: 1000000 -> `d298ee8d38cd98a093dbf71b8950d095`
- secret: `ffffffff...` (all F), window: 12345 -> `e3c1b82ce9caccc56cb932877b100cb9`

## Security Notes

- Release builds have ProGuard enabled (code obfuscation)
- All logging is stripped in release builds
- Backup is disabled - secrets are not backed up to cloud
- Uses Android Keystore for secure storage

## Troubleshooting

### "Could not find com.google.mlkit:barcode-scanning"
Run: `./gradlew --refresh-dependencies`

### Camera not working
Ensure camera permission is granted in device settings.

### QR scan not detecting
- Ensure good lighting
- Hold camera steady
- QR code should fill ~50% of the screen
