#!/usr/bin/env bash
set -euo pipefail
source_dir="$(dirname "$0")"
build_tools="$ANDROID_HOME/build-tools/35.0.0"
platform="$ANDROID_HOME/platforms/android-35/android.jar"
temp="$(mktemp -d)"
trap 'rm -rf "$temp"' EXIT
mkdir "$temp/classes" "$temp/dex"
"$build_tools/aapt2" link --manifest "$source_dir/AndroidManifest.xml" -I "$platform" -o "$temp/unsigned.apk"
javac --release 8 -classpath "$platform" -d "$temp/classes" "$source_dir/StopActionTest.java"
jar cf "$temp/classes.jar" -C "$temp/classes" .
"$build_tools/d8" --min-api 26 --lib "$platform" --output "$temp/dex" "$temp/classes.jar"
zip -qj "$temp/unsigned.apk" "$temp/dex/classes.dex"
keytool -genkeypair -keystore "$temp/ci.p12" -storepass android -keypass android -alias ci -keyalg RSA -keysize 2048 -validity 2 -dname 'CN=IQBot Emulator Test'
"$build_tools/apksigner" sign --ks "$temp/ci.p12" --ks-pass pass:android --out "$temp/signed.apk" "$temp/unsigned.apk"
adb install "$temp/signed.apk"
adb shell "cmd notification allow_listener 'com.iqbot.replaycheck/.StopActionTest\$Listener'"
