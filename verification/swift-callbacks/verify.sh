#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
output=build/swift-callbacks
mkdir -p "$output"
swiftc -emit-module -emit-library -module-name UIKit verification/swift-callbacks/MocksUIKit.swift -o "$output/libUIKit.dylib" -emit-module-path "$output/UIKit.swiftmodule"
swiftc -I "$output" -L "$output" -lUIKit -Xlinker -rpath -Xlinker "$(pwd)/$output" iosApp/Sources/GycJVerificationNative/JVerificationClient.swift verification/swift-callbacks/MocksSDK.swift verification/swift-callbacks/main.swift -o "$output/check"
"$output/check"
