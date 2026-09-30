#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
export DEVECO_SDK_HOME="${DEVECO_SDK_HOME:-/Applications/DevEco-Studio.app/Contents/sdk}"
ohpm_bin="${OHPM_BIN:-/Applications/DevEco-Studio.app/Contents/tools/ohpm/bin/ohpm}"
hvigor_bin="${HVIGOR_BIN:-/Applications/DevEco-Studio.app/Contents/tools/hvigor/bin/hvigorw}"
cd ohos
"$ohpm_bin" install --all
"$hvigor_bin" --mode module -p module=JVerificationNative@default -p product=default assembleHar --no-daemon
cd ../verification-ohos
"$ohpm_bin" install --all
"$hvigor_bin" --mode module -p module=JVerificationConsumer@default -p product=default assembleHar --no-daemon
