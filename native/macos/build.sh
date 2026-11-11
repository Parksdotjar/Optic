#!/bin/sh
set -eu
source_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
output_dir=${1:?Pass an output directory}
mkdir -p "$output_dir"
for arch in arm64 x86_64; do
    xcrun swiftc -swift-version 5 -parse-as-library -O -target "$arch-apple-macos13.0" \
        -framework ScreenCaptureKit -framework CoreMedia -framework CoreGraphics -framework AVFoundation \
        -Xlinker -sectcreate -Xlinker __TEXT -Xlinker __info_plist -Xlinker "$source_dir/Info.plist" \
        "$source_dir/ProcessAudio.swift" -o "$output_dir/optic-process-audio-$arch"
done
xcrun lipo -create "$output_dir/optic-process-audio-arm64" "$output_dir/optic-process-audio-x86_64" -output "$output_dir/optic-process-audio"
codesign --force --sign - --identifier dev.optic.process-audio "$output_dir/optic-process-audio"
codesign --verify --strict "$output_dir/optic-process-audio"
xcrun lipo -verify_arch arm64 x86_64 "$output_dir/optic-process-audio"
rm -f "$output_dir/optic-process-audio-arm64" "$output_dir/optic-process-audio-x86_64"
