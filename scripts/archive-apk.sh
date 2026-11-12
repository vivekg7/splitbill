#!/usr/bin/env bash
#
# Build the signed release APK and app bundle (AAB) and archive them into their release's
# own folder under local/releases/, under a name that says exactly what it is. Run at
# will; nothing in the build calls this.
#
#   ./scripts/archive-apk.sh              build, verify, archive
#   ./scripts/archive-apk.sh --force      allow replacing an existing archive
#   ./scripts/archive-apk.sh --no-build   use the APK and AAB already on disk
#
# Each archive is files sharing one prefix: the APK (sideloading), the AAB (Google Play,
# signed with the same key, which Play knows as the upload key), a .sha256 for each, and
# the R8 mapping.txt that turns an obfuscated stack trace from that build back into names.
# The bundle carries its own copy for Play; the archived one is for the APK, and for any
# trace that arrives outside Play. The mapping lives in app/build/ and a clean wipes it,
# so it is worth nothing unless it is kept with the build it belongs to.
#
# A clean checkout produces "local/releases/v1.0/splitbill-v1.0.apk". A dirty working
# tree produces "local/releases/v1.0-dirty-g1a2b3c4/splitbill-v1.0-dirty-g1a2b3c4.apk",
# so work in progress can never take the name of a release, and an existing archive is
# never replaced without --force: same version, different commit, is the case that bites.

set -euo pipefail

# Print the header comment block, so the usage text cannot drift from the docs above it.
usage() { awk 'NR>1 && /^#/ { sub(/^# ?/, ""); print; next } NR>1 { exit }' "$0"; }

FORCE=0
BUILD=1
for arg in "$@"; do
    case "$arg" in
        --force) FORCE=1 ;;
        --no-build) BUILD=0 ;;
        -h | --help) usage; exit 0 ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

cd "$(dirname "${BASH_SOURCE[0]}")/.."

APK=app/build/outputs/apk/release/app-release.apk
AAB=app/build/outputs/bundle/release/app-release.aab
MAP=app/build/outputs/mapping/release/mapping.txt

# Gradle and apksigner need a JDK; a Mac with only Android Studio has one inside the app.
STUDIO_JBR="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$STUDIO_JBR" ]; then export JAVA_HOME="$STUDIO_JBR"; fi

SDK=$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)
SDK="${SDK:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
TOOLS=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1 || true)
[ -n "$TOOLS" ] || { echo "error: no Android build-tools under $SDK" >&2; exit 1; }

if [ "$BUILD" = 1 ]; then ./gradlew -q :app:assembleRelease :app:bundleRelease; fi
for f in "$APK" "$AAB"; do
    [ -f "$f" ] || { echo "error: $f not found (drop --no-build?)" >&2; exit 1; }
done

# The version comes from the built APK, never from build.gradle.kts, so the name cannot
# disagree with the manifest that shipped.
BADGING=$("$TOOLS/aapt" dump badging "$APK" | head -1)
VNAME=$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<<"$BADGING")
VCODE=$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<<"$BADGING")
[ -n "$VNAME" ] && [ -n "$VCODE" ] || { echo "error: no version in $APK" >&2; exit 1; }

# Release comes out unsigned when keystore.properties is absent, and an unsigned
# APK under a release name is the mislabelled file this script exists to prevent.
SIGNER=$("$TOOLS/apksigner" verify --print-certs "$APK" 2>/dev/null |
    sed -n 's/^Signer #1 certificate SHA-256 digest: //p' || true)
[ -n "$SIGNER" ] || {
    echo "error: $APK is not signed. Add keystore.properties (README) and rebuild." >&2
    exit 1
}
# The bundle is jar-signed; keytool prints its certificate. Same key as the APK, or Play
# refuses the upload.
AAB_SIGNER=$("$JAVA_HOME/bin/keytool" -printcert -jarfile "$AAB" 2>/dev/null |
    sed -n 's/^[[:space:]]*SHA256: //p' | head -1 | tr -d ':' | tr 'A-F' 'a-f' || true)
[ "$AAB_SIGNER" = "$SIGNER" ] || {
    echo "error: $AAB is not signed with the APK's key ($SIGNER), got '${AAB_SIGNER:-none}'." >&2
    exit 1
}
[ -f "$MAP" ] || { echo "error: $MAP not found; it is written by R8 (drop --no-build)." >&2; exit 1; }

SUFFIX=""
if [ -n "$(git status --porcelain)" ]; then
    SUFFIX="-dirty-g$(git rev-parse --short HEAD)"
    echo "warning: working tree is dirty; archiving as a work-in-progress build"
fi

# The files keep the full name too, so one copied out of its folder, onto a phone,
# still says which build it is.
DEST="local/releases/v$VNAME$SUFFIX"
TARGET="$DEST/splitbill-v$VNAME$SUFFIX.apk"
if [ -e "$TARGET" ] && [ "$FORCE" != 1 ]; then
    echo "error: $TARGET exists. Bump versionCode/versionName, or pass --force." >&2
    exit 1
fi

mkdir -p "$DEST"
cp "$APK" "$TARGET"
shasum -a 256 "$TARGET" | awk '{print $1}' >"$TARGET.sha256"
cp "$MAP" "$TARGET.mapping.txt"
BUNDLE="${TARGET%.apk}.aab"
cp "$AAB" "$BUNDLE"
shasum -a 256 "$BUNDLE" | awk '{print $1}' >"$BUNDLE.sha256"

echo "archived: $TARGET  ($(wc -c <"$TARGET" | tr -d ' ') bytes)"
echo "bundle:   $BUNDLE  ($(wc -c <"$BUNDLE" | tr -d ' ') bytes)"
echo "version:  $VNAME ($VCODE)"
echo "sha256:   $(cat "$TARGET.sha256")"
echo "signer:   $SIGNER"
