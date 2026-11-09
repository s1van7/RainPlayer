#!/usr/bin/env bash
# CI 构建脚本：准备 Android SDK 并产出 release APK 到 artifacts/
#
# 之所以不用 android-actions/setup-android，是因为它内部会执行
# `sdkmanager tools`，而 cmdline-tools 16.0 起该包已被移除，
# 会报 "Failed to find package 'tools'" 直接失败。
# GitHub 的 ubuntu-latest 镜像本身已预装 Android SDK，这里直接复用并补齐组件。

set -uo pipefail

SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/usr/local/lib/android/sdk}}"
export ANDROID_HOME="$SDK_ROOT"
export ANDROID_SDK_ROOT="$SDK_ROOT"

echo "==> Android SDK: $SDK_ROOT"
if [ ! -d "$SDK_ROOT" ]; then
  echo "!! 未找到 Android SDK 目录" >&2
  exit 1
fi

SDKMGR="$(find "$SDK_ROOT" -type f -name sdkmanager 2>/dev/null | head -1 || true)"
if [ -n "$SDKMGR" ]; then
  echo "==> sdkmanager: $SDKMGR"
  chmod +x "$SDKMGR" 2>/dev/null || true
  # 接受许可（管道在 pipefail 下可能返回 141，忽略即可）
  (yes | "$SDKMGR" --licenses >/dev/null 2>&1) || true
  echo "==> 补齐 platform-tools / android-35 / build-tools 35.0.0"
  "$SDKMGR" "platform-tools" "platforms;android-35" "build-tools;35.0.0" 2>&1 | tail -8 || true
else
  echo "!! 未找到 sdkmanager，沿用镜像自带组件"
fi

echo "==> 开始编译"

# 若仓库配置了签名 Secrets，就用正式密钥签名，保证 CI 产物能与 Release 包互相覆盖安装；
# 没有配置则回退到 debug 签名（能装，但签名与 Release 包不同）。
if [ -n "${KEYSTORE_BASE64:-}" ]; then
  echo "==> 使用 Secrets 中的发布签名"
  printf '%s' "$KEYSTORE_BASE64" | tr -d '\n' | base64 -d > release-keystore.jks 2>/dev/null || true
  if [ ! -s release-keystore.jks ]; then
    echo "!! KEYSTORE_BASE64 解码失败，检查 Secret 是否为完整的 base64" >&2
    exit 1
  fi
  {
    echo "storeFile=release-keystore.jks"
    echo "storePassword=${KEYSTORE_PASSWORD:-}"
    echo "keyAlias=${KEY_ALIAS:-}"
    echo "keyPassword=${KEY_PASSWORD:-}"
  } > keystore.properties
else
  echo "==> 未配置 KEYSTORE_BASE64，使用 debug 签名"
  echo "   （此产物与 Release 页的包签名不同，不能互相覆盖安装）"
fi

chmod +x ./gradlew
./gradlew :app:assembleRelease --no-daemon --stacktrace
BUILD_STATUS=$?

if [ $BUILD_STATUS -ne 0 ]; then
  echo "!! 编译失败，退出码 $BUILD_STATUS" >&2
  exit $BUILD_STATUS
fi

mkdir -p artifacts
shopt -s nullglob
found=0
for f in app/build/outputs/apk/release/*.apk; do
  base="$(basename "$f" .apk)"
  # app-universal-release -> universal
  short="${base#app-}"
  short="${short%-release}"
  # x86 系列只用于模拟器，不进发布产物
  case "$short" in
    x86|x86_64) continue ;;
  esac
  cp "$f" "artifacts/rain-${short}.apk"
  found=$((found + 1))
done

if [ "$found" -eq 0 ]; then
  echo "!! 没有找到 APK 产物" >&2
  exit 1
fi

echo "==> 产物"
ls -lh artifacts
