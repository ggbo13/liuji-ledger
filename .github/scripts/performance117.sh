#!/usr/bin/env bash
set -euo pipefail

# 仅在全新CI模拟器上执行；开启真实动画倍率，夹具拒绝非模拟器和非合成账目。
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
task_root="$PWD"
baseline117/gradlew -p baseline117 -I "$task_root/.github/scripts/performance117.gradle" "-Dliuji.performance.sources=$task_root/app/src/performanceTest/java" :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.liujii.ledger.Performance117Test -Pandroid.testInstrumentationRunnerArguments.allowSyntheticPerformanceData=true --no-daemon --max-workers=2
./gradlew -I "$task_root/.github/scripts/performance117.gradle" "-Dliuji.performance.sources=$task_root/app/src/performanceTest/java" :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.liujii.ledger.Performance117Test -Pandroid.testInstrumentationRunnerArguments.allowSyntheticPerformanceData=true --no-daemon --max-workers=2
