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

# ---------------------------------------------------------------- 发布签名
# 四个 Secrets 全部配置才启用正式签名，保证 CI 产物能与 Release 包互相覆盖安装。
#   KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
# 一个都没配 -> 回退 debug 签名；只配了一部分 -> 直接失败，避免产出签名不一致的包。
missing=""
present=0
for v in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
  if [ -z "${!v:-}" ]; then
    missing="$missing $v"
  else
    present=$((present + 1))
  fi
done

if [ "$present" -eq 4 ]; then
  echo "==> 使用 Secrets 中的发布签名"
  # 网页复制常带换行/空格/CRLF，全部剔除后再解码
  printf '%s' "$KEYSTORE_BASE64" | tr -d ' \t\r\n' | base64 -d > release-keystore.jks 2>/tmp/b64.err || true
  if [ ! -s release-keystore.jks ]; then
    echo "!! KEYSTORE_BASE64 解码失败：$(cat /tmp/b64.err 2>/dev/null | head -1)" >&2
    echo "   Secret 长度 ${#KEYSTORE_BASE64} 字符（正常约 3592），请确认完整复制了 keystore-secrets.txt 里那一整行。" >&2
    exit 1
  fi
  # 校验 keystore 能打开、别名存在，提前暴露口令/别名填错的问题
  if ! keytool -list -keystore release-keystore.jks -storepass "$KEYSTORE_PASSWORD" -alias "$KEY_ALIAS" >/dev/null 2>&1; then
    echo "!! keystore 校验失败：KEYSTORE_PASSWORD 或 KEY_ALIAS 不正确" >&2
    echo "   当前 keystore 内实际别名：" >&2
    keytool -list -keystore release-keystore.jks -storepass "$KEYSTORE_PASSWORD" 2>/dev/null \
      | grep -i "PrivateKeyEntry" | sed 's/^/     /' >&2 || echo "     （口令错误，无法列出别名）" >&2
    exit 1
  fi
  {
    echo "storeFile=release-keystore.jks"
    echo "storePassword=$KEYSTORE_PASSWORD"
    echo "keyAlias=$KEY_ALIAS"
    echo "keyPassword=$KEY_PASSWORD"
  } > keystore.properties
  echo "   签名就绪，别名 $KEY_ALIAS"
elif [ "$present" -gt 0 ]; then
  echo "!! 签名 Secrets 配置不完整，缺少:$missing" >&2
  echo "   四个必须同时配置：KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD" >&2
  echo "   若暂时不需要正式签名，请把已配置的也一并删除，脚本会自动回退 debug 签名。" >&2
  exit 1
else
  echo "==> 未配置签名 Secrets，使用 debug 签名"
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
# 版本号取自 build.gradle.kts，产物命名为 rainplayer-<版本>-<flavor>-<abi>.apk
VERSION="$(sed -n 's/.*versionName *= *"\([^"]*\)".*/\1/p' app/build.gradle.kts | head -1)"
[ -z "$VERSION" ] && VERSION="dev"

# 手机包与电视包分开输出，互不覆盖
for flavor in phone tv; do
  for f in app/build/outputs/apk/$flavor/release/*.apk; do
    base="$(basename "$f" .apk)"
    # app-phone-universal-release -> universal
    short="${base#app-$flavor-}"
    short="${short%-release}"
    # x86 系列只用于模拟器，不进发布产物
    case "$short" in
      x86|x86_64) continue ;;
    esac
    cp "$f" "artifacts/rainplayer-${VERSION}-${flavor}-${short}.apk"
    found=$((found + 1))
  done
done

if [ "$found" -eq 0 ]; then
  echo "!! 没有找到 APK 产物" >&2
  exit 1
fi

echo "==> 产物"
ls -lh artifacts

# 打印实际签名证书，便于确认 CI 产物能否覆盖安装 Release 包
echo "==> 签名证书"
SAMPLE="$(ls artifacts/*phone-universal*.apk 2>/dev/null | head -1)"
[ -z "$SAMPLE" ] && SAMPLE="$(ls artifacts/*.apk 2>/dev/null | head -1)"
keytool -printcert -jarfile "$SAMPLE" 2>/dev/null \
  | grep -E "^Owner:|^所有者:" | sed 's/^/   /' || echo "   （读取失败，请手动用 apksigner 校验）"
