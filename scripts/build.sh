#!/usr/bin/env bash
# =============================================================================
# bb-form build script — tạo package cho mọi nền tảng
# Usage: bash scripts/build.sh [VERSION]
# =============================================================================
set -euo pipefail

VERSION="${1:-0.1.0}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DIST_DIR="$ROOT_DIR/dist"
RELEASE_DIR="$ROOT_DIR/release"

echo "══════════════════════════════════════════════"
echo "  bb-form build — v$VERSION"
echo "══════════════════════════════════════════════"

mkdir -p "$RELEASE_DIR"

# ──────────────────────────────────────────────
# Bước 1: Build uberscript nếu chưa có
# ──────────────────────────────────────────────
if [ ! -f "$DIST_DIR/bb-form.clj" ]; then
  echo ""
  echo "📦 Building uberscript..."
  mkdir -p "$DIST_DIR"
  cd "$ROOT_DIR"
  bb uberscript dist/bb-form.clj -m com.drbinhthanh.bb-form
  echo "✅ Uberscript: $DIST_DIR/bb-form.clj"
fi

# ──────────────────────────────────────────────
# Template launcher Unix (bash)
# ──────────────────────────────────────────────
UNIX_LAUNCHER='#!/usr/bin/env bash
# bb-form launcher
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec bb "${SCRIPT_DIR}/bb-form.clj" "$@"'

# ──────────────────────────────────────────────
# Hàm helper tạo package Unix (tar.gz)
# ──────────────────────────────────────────────
package_unix() {
  local PLATFORM="$1"
  local OUTPUT="$RELEASE_DIR/bb-form-${PLATFORM}.tar.gz"
  local PKG_DIR
  PKG_DIR="$(mktemp -d)"

  echo ""
  echo "  📦 Đang đóng gói $PLATFORM..."
  cp "$DIST_DIR/bb-form.clj" "$PKG_DIR/"
  printf '%s\n' "$UNIX_LAUNCHER" > "$PKG_DIR/bb-form"
  chmod +x "$PKG_DIR/bb-form"

  tar -czf "$OUTPUT" -C "$PKG_DIR" bb-form bb-form.clj
  rm -rf "$PKG_DIR"

  local SIZE
  SIZE=$(du -sh "$OUTPUT" | cut -f1)
  echo "  ✅ $OUTPUT ($SIZE)"
}

# ──────────────────────────────────────────────
# Đóng gói Linux x86_64
# ──────────────────────────────────────────────
package_unix "linux-x86_64"

# ──────────────────────────────────────────────
# Đóng gói macOS x86_64
# ──────────────────────────────────────────────
package_unix "macos-x86_64"

# ──────────────────────────────────────────────
# Đóng gói macOS arm64 (Apple Silicon)
# ──────────────────────────────────────────────
package_unix "macos-arm64"

# ──────────────────────────────────────────────
# Đóng gói Windows x86_64 (.zip)
# ──────────────────────────────────────────────
echo ""
echo "  📦 Đang đóng gói windows-x86_64..."
WIN_PKG_DIR="$(mktemp -d)"
cp "$DIST_DIR/bb-form.clj" "$WIN_PKG_DIR/"

# Tạo .bat launcher (CRLF line endings)
printf '@echo off\r\nsetlocal EnableDelayedExpansion\r\nset "SCRIPT_DIR=%%~dp0"\r\nbb "%%SCRIPT_DIR%%bb-form.clj" %%*\r\n' \
  > "$WIN_PKG_DIR/bb-form.bat"

# Tạo PowerShell launcher
printf '#!/usr/bin/env pwsh\r\n$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path\r\n& bb "$ScriptDir\\bb-form.clj" @args\r\n' \
  > "$WIN_PKG_DIR/bb-form.ps1"

ZIP_OUTPUT="$RELEASE_DIR/bb-form-windows-x86_64.zip"
cd "$WIN_PKG_DIR"
zip -q "$ZIP_OUTPUT" bb-form.bat bb-form.ps1 bb-form.clj
cd -
rm -rf "$WIN_PKG_DIR"

WIN_SIZE=$(du -sh "$ZIP_OUTPUT" | cut -f1)
echo "  ✅ $ZIP_OUTPUT ($WIN_SIZE)"

# ──────────────────────────────────────────────
# Tổng kết
# ──────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════"
echo "  ✅ Hoàn thành! Các package đã tạo:"
echo "══════════════════════════════════════════════"
ls -lh "$RELEASE_DIR"
echo ""
echo "SHA256:"
(cd "$RELEASE_DIR" && sha256sum *.tar.gz *.zip 2>/dev/null || shasum -a 256 *.tar.gz *.zip)
