#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
bash gradlew :jverification-core:jvmTest :jverification-core:assembleRelease :jverification-core:compileKotlinIosArm64 :jverification-core:compileKotlinIosX64 :jverification-core:iosSimulatorArm64Test :jverification-core:compileKotlinOhosArm64 :jverification-kuikly:compileKotlinOhosArm64 publishAllPublicationsToStagingRepository --max-workers=1
node verification/ohos-behavior.cjs
bash gradlew -p verification-consumer -PlocalArtifacts=true compileDebugKotlinAndroid compileKotlinIosArm64 compileKotlinIosX64 linkDebugFrameworkIosSimulatorArm64 compileKotlinOhosArm64 --max-workers=1
pod lib lint GycJVerificationNative.podspec --allow-warnings --skip-tests --use-libraries
