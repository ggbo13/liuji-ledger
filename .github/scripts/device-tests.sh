#!/usr/bin/env bash
set -uo pipefail

# 模拟器只安装测试包；截图中的金额、分类和背景均来自合成夹具。
adb shell settings put secure show_ime_with_hard_keyboard 1
./gradlew :app:connectedDebugAndroidTest --no-daemon --max-workers=2
task_result=$?
mkdir -p app/build/reports/visual116
while IFS= read -r task_image; do
  task_image="${task_image//$'\r'/}"
  if [[ "$task_image" =~ ^(transaction|actions|preview)_[0-9]+_[0-9.]+_(true|false)\.png$ ]]; then
    adb exec-out run-as com.liujii.ledger cat "files/visual116/$task_image" > "app/build/reports/visual116/$task_image"
  fi
done < <(adb shell run-as com.liujii.ledger ls files/visual116 2>/dev/null)
exit "$task_result"
