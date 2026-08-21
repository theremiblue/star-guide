#!/usr/bin/env bash
# Copyright 2026 Dmitry Vasyliev
# SPDX-License-Identifier: GPL-3.0-or-later


PACKAGES=(
    "platform-tools"
    "emulator"
    "cmdline-tools;latest"
    "platforms;android-36"
    "build-tools;36.0.0"
    "ndk;30.0.14904198"
    "system-images;android-36;google_apis;arm64-v8a"
)

echo "Installing Android SDK packages..."
for pkg in "${PACKAGES[@]}"; do
    echo "  -> $pkg"
    yes | sdkmanager "$pkg"
done

echo "Accepting licenses..."
yes | sdkmanager --licenses

echo "Creating AVD..."
avdmanager create avd \
    --name StarGuide_AVD \
    --package "system-images;android-36;google_apis;arm64-v8a" \
    --device pixel_6 \
    --force

echo "Done."
