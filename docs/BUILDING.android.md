# Building for Android

## Prerequisites

- Zig `0.16.0`
- JDK `21`
- Android Emulator
- Android SDK Build-Tools `36.1`
- Android SDK Build-Tools `37`
- Android SDK Platform `36.1`
- Android SDK Platform-Tools
- Android SDK Command-line Tools `latest`
- Android NDK `30.0.14904198`

Tools must be on `PATH`.

Accept licenses:

```bash
yes | sdkmanager --licenses
```

Example macOS layout:

```bash
export JAVA_HOME=<YOUR_JAVA_PATH>
export PATH=$JAVA_HOME/bin:$PATH

export ANDROID_HOME=$HOME/Library/Android/sdk
export NDK_HOME=$ANDROID_HOME/ndk/<YOUR_NDK_VERSION>
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools
export PATH=$PATH:$ANDROID_HOME/emulator
```

Sanity check:

```bash
zig version
java -version
adb version
sdkmanager --list_installed
```

## Build

```bash
cd android
./gradlew build
```

APK outputs:

- `android/app/build/outputs/apk/debug/app-debug.apk`
- `android/app/build/outputs/apk/release/app-release.apk`

## Notes

`zigCoreBuild` runs before `preBuild`.

It:

- builds `core/` with Zig
- passes NDK path and Android API level from Gradle
- writes generated `.so` files under
  `android/app/build/generated/zig/jniLibs`

We intentionally rebuild each time (cache is preserved).

