#!/usr/bin/env bash
# Build sqlite-vec as a 16 KB page-aligned loadable extension for every Android ABI and
# install it as app/src/main/jniLibs/<abi>/libvec0.so.
#
# The official sqlite-vec Android release builds are 4 KB-aligned, which 16 KB-page
# devices (Android 15+) may refuse to load and Google Play flags. This script compiles the
# same release (same source, same SQLite headers, same flags as upstream's release.yaml)
# with NDK r28+ and -Wl,-z,max-page-size=16384.
#
# Usage (Git Bash, from App Folder):  tools/sqlite-vec/build_android.sh
# Needs: an Android NDK r28+ (ANDROID_NDK_HOME, or the newest one under the SDK's ndk/),
#        curl, tar, unzip, python (alignment check).
set -euo pipefail

VEC_VERSION="0.1.9"                    # must match the sqlite-vec that built the stores (vec_version())
SQLITE_ZIP="https://www.sqlite.org/2024/sqlite-amalgamation-3450300.zip"   # headers upstream vendors
API=21                                  # upstream's minimum; the app's minSdk is 26

here="$(cd "$(dirname "$0")" && pwd)"
app="$(cd "$here/../.." && pwd)"
work="$here/build"
out="$app/app/src/main/jniLibs"

ndk="${ANDROID_NDK_HOME:-}"
if [[ -z "$ndk" ]]; then
  sdk="${ANDROID_HOME:-${LOCALAPPDATA:-$HOME/AppData/Local}/Android/Sdk}"
  ndk="$(ls -d "$sdk"/ndk/* 2>/dev/null | sort -V | tail -1 || true)"
fi
[[ -d "$ndk" ]] || { echo "No NDK found; set ANDROID_NDK_HOME" >&2; exit 1; }
rev="$(grep -m1 Pkg.Revision "$ndk/source.properties" | cut -d= -f2 | tr -d ' ')"
[[ "${rev%%.*}" -ge 28 ]] || { echo "NDK $rev is too old; r28+ is needed for 16 KB pages" >&2; exit 1; }
bin="$(ls -d "$ndk"/toolchains/llvm/prebuilt/*/bin | head -1)"
clang="$bin/clang"; [[ -x "$clang" ]] || clang="$bin/clang.exe"
strip="$bin/llvm-strip"; [[ -x "$strip" ]] || strip="$bin/llvm-strip.exe"
echo "NDK $rev: $clang"

rm -rf "$work" && mkdir -p "$work/vendor"
cd "$work"
curl -sSL -o amal.tgz "https://github.com/asg017/sqlite-vec/releases/download/v$VEC_VERSION/sqlite-vec-$VEC_VERSION-amalgamation.tar.gz"
tar -xzf amal.tgz
curl -sSL -o sqlite.zip "$SQLITE_ZIP"
unzip -q -j sqlite.zip '*/sqlite3.h' '*/sqlite3ext.h' -d vendor
grep -q "\"v$VEC_VERSION\"" sqlite-vec.h || { echo "unexpected sqlite-vec version" >&2; exit 1; }

# abi -> clang target
declare -A target=(
  [arm64-v8a]=aarch64-linux-android$API
  [armeabi-v7a]=armv7a-linux-androideabi$API
  [x86_64]=x86_64-linux-android$API
  [x86]=i686-linux-android$API
)
for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  mkdir -p "$work/$abi"
  "$clang" --target="${target[$abi]}" -fPIC -shared -O3 -Wall -Wextra -Wno-unused-parameter \
    -Ivendor/ sqlite-vec.c -o "$work/$abi/libvec0.so" -lm \
    -Wl,-z,max-page-size=16384 -Wl,-soname,libvec0.so
  "$strip" --strip-unneeded "$work/$abi/libvec0.so"
done

python - "$work" <<'EOF'
import struct, sys, os
bad = []
for abi in ("arm64-v8a", "armeabi-v7a", "x86_64", "x86"):
    b = open(os.path.join(sys.argv[1], abi, "libvec0.so"), "rb").read()
    is64 = b[4] == 2
    phoff = struct.unpack_from("<Q" if is64 else "<I", b, 0x20 if is64 else 0x1C)[0]
    entsize, num = struct.unpack_from("<HH", b, 0x36 if is64 else 0x2A)
    aligns = []
    for i in range(num):
        o = phoff + i * entsize
        if struct.unpack_from("<I", b, o)[0] == 1:  # PT_LOAD
            aligns.append(struct.unpack_from("<Q", b, o + 48)[0] if is64 else struct.unpack_from("<I", b, o + 28)[0])
    ok = min(aligns) >= 16384
    print(f"{abi:12} {len(b):7d} bytes, LOAD align {sorted(set(aligns))} {'ok' if ok else 'NOT 16 KB'}")
    if not ok: bad.append(abi)
    if b"sqlite3_vec_init" not in b: bad.append(abi + " (no sqlite3_vec_init)")
sys.exit(f"failed: {bad}" if bad else 0)
EOF

for abi in arm64-v8a armeabi-v7a x86_64 x86; do
  mkdir -p "$out/$abi" && cp "$work/$abi/libvec0.so" "$out/$abi/libvec0.so"
done
echo "installed sqlite-vec v$VEC_VERSION (16 KB aligned) into $out"
