#!/usr/bin/env bash
set -euo pipefail

VERSION="0.12.1"
REPO_URL="https://github.com/sbooth/SFBAudioEngine"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BUILD_DIR="${SCRIPT_DIR}/../internal/infra/audio/sfb_build"
TMP_SRC="${BUILD_DIR}/sfb-src-${VERSION}"
TMP_FW="${BUILD_DIR}/sfb-fw-${VERSION}"
OUT_DIR="${SCRIPT_DIR}/../internal/infra/audio/sfb_libs"

# Binary xcframeworks that remain after stripping. Only these get linked and copied.
NEEDED_XCFS=("wavpack" "ogg" "FLAC" "opus" "vorbis" "mpg123")

echo "[sfb] Building SFBAudioEngine ${VERSION}"

# 1. Clone/Update source
if [ ! -d "${TMP_SRC}" ]; then
    echo "[sfb] Cloning ${REPO_URL}..."
    git clone --depth 1 --branch "${VERSION}" "${REPO_URL}" "${TMP_SRC}"
fi

# 1b. Strip deps/formats not needed by this app
strip_unused_deps() {
    local SRC="${TMP_SRC}"
    local PKG="${SRC}/Package.swift"

    echo "[sfb] Stripping unused deps from Package.swift..."

    # Remove unneeded package declarations
    for pkg in CDUMB CXXTagLib CSpeex \
                lame-binary-xcframework mpc-binary-xcframework \
                sndfile-binary-xcframework tta-cpp-binary-xcframework; do
        sed -i '' "/\"${pkg}\"/d" "${PKG}"
    done

    # Remove unneeded product deps from CSFBAudioEngine target
    for prod in dumb taglib speex lame mpc sndfile tta-cpp; do
        sed -i '' "/\.product(name: \"${prod}\",/d" "${PKG}"
    done

    echo "[sfb] Removing unneeded decoder source files..."
    local DECODERS="${SRC}/Sources/CSFBAudioEngine/Decoders"
    rm -f "${DECODERS}/SFBModuleDecoder."{h,m}       # dumb/tracker formats
    rm -f "${DECODERS}/SFBOggSpeexDecoder."{h,m}     # speex
    rm -f "${DECODERS}/SFBMusepackDecoder."{h,m}     # mpc
    rm -f "${DECODERS}/SFBLibsndfileDecoder."{h,m}   # sndfile (WAV/AIFF covered by CoreAudio)
    rm -f "${DECODERS}/SFBTrueAudioDecoder."{h,mm}   # TrueAudio
    rm -f "${DECODERS}/SFBShortenDecoder."{h,mm}     # Shorten

    echo "[sfb] Removing unneeded encoder source files..."
    local ENCODERS="${SRC}/Sources/CSFBAudioEngine/Encoders"
    rm -f "${ENCODERS}/SFBMP3Encoder."{h,mm}         # lame
    rm -f "${ENCODERS}/SFBMusepackEncoder."{h,m}     # mpc
    rm -f "${ENCODERS}/SFBLibsndfileEncoder."{h,m}   # sndfile
    rm -f "${ENCODERS}/SFBTrueAudioEncoder."{h,mm}   # tta-cpp
    rm -f "${ENCODERS}/SFBOggSpeexEncoder."{h,m}     # speex

    echo "[sfb] Removing TagLib metadata source files..."
    # All format-specific metadata files import taglib directly.
    # The Go layer uses go-taglib for metadata — SFBAudioEngine's metadata API is unused.
    # SFBAudioFile.m / SFBAudioMetadata.m / SFBAudioProperties.m have no taglib deps and are kept.
    local META="${SRC}/Sources/CSFBAudioEngine/Metadata"
    # TagLib category implementations
    rm -f "${META}/SFBAudioMetadata+TagLib"*.h
    rm -f "${META}/SFBAudioMetadata+TagLib"*.mm
    # TagLib helpers used only by format-specific metadata files
    rm -f "${META}/AddAudioPropertiesToDictionary.h"
    rm -f "${META}/AddAudioPropertiesToDictionary.mm"
    rm -f "${META}/TagLibStringUtilities.h"
    # Format-specific metadata files (all import taglib)
    rm -f "${META}/SFBAIFFFile."{h,mm}
    rm -f "${META}/SFBDSDIFFFile."{h,mm}
    rm -f "${META}/SFBDSFFile."{h,mm}
    rm -f "${META}/SFBFLACFile."{h,mm}
    rm -f "${META}/SFBMonkeysAudioFile."{h,mm}
    rm -f "${META}/SFBMP3File."{h,mm}
    rm -f "${META}/SFBMP4File."{h,mm}
    rm -f "${META}/SFBOggFLACFile."{h,mm}
    rm -f "${META}/SFBOggOpusFile."{h,mm}
    rm -f "${META}/SFBOggVorbisFile."{h,mm}
    rm -f "${META}/SFBWAVEFile."{h,mm}
    rm -f "${META}/SFBWavPackFile."{h,mm}
    # Removed-format metadata files
    rm -f "${META}/SFBOggSpeexFile."{h,mm}
    rm -f "${META}/SFBShortenFile."{h,mm}
    rm -f "${META}/SFBMusepackFile."{h,mm}
    rm -f "${META}/SFBTrueAudioFile."{h,mm}
    rm -f "${META}/SFBExtendedModuleFile."{h,mm}
    rm -f "${META}/SFBImpulseTrackerModuleFile."{h,mm}
    rm -f "${META}/SFBScreamTracker3ModuleFile."{h,mm}
    rm -f "${META}/SFBProTrackerModuleFile."{h,mm}

    echo "[sfb] Removing sndfile utility..."
    local UTILS="${SRC}/Sources/CSFBAudioEngine/Utilities"
    rm -f "${UTILS}/SFBLibsndfileUtilities."{h,m}    # only used by removed sndfile decoder/encoder

    echo "[sfb] Done stripping."
}
strip_unused_deps

# 2. Build for an architecture
build_arch() {
    local ARCH="$1"
    local FW_DIR="${TMP_FW}/${ARCH}/SFBAudioEngine.framework"

    echo "[sfb] swift build -c release --arch ${ARCH}..."
    # Explicitly build dependencies because sometimes SPM skips them for Clang targets in Swift 6.0+
    local DEPS=("AVFAudioExtensions" "CXXAudioRingBuffer" "CXXDispatchSemaphore" "CXXRingBuffer" "CXXUnfairLock" "MAC")
    for dep in "${DEPS[@]}"; do
        echo "[sfb] Building dependency: ${dep}..."
        (cd "${TMP_SRC}" && swift build -c release --arch "${ARCH}" --target "${dep}" > /dev/null 2>&1 || true)
    done

    # Build main targets
    (cd "${TMP_SRC}" && swift build -c release --arch "${ARCH}" --target CSFBAudioEngine 2>&1 | grep -E "error:|Build complete" | tail -5 || true)
    (cd "${TMP_SRC}" && swift build -c release --arch "${ARCH}" --target SFBAudioEngine 2>&1 | grep -E "error:|Build complete" | tail -5 || true)

    # Collect ALL object files generated by SPM (including dependencies like TagLib)
    local OBJ_FILES
    OBJ_FILES=$(find "${TMP_SRC}/.build/${ARCH}-apple-macosx/release" -name "*.o" | tr '\n' ' ')

    if [ -z "${OBJ_FILES}" ]; then
        echo "[sfb] Error: No object files found for ${ARCH}"
        exit 1
    fi

    # Find binary xcframework dependencies SPM downloaded (whitelist only — skip orphaned artifacts)
    local FW_SEARCH=""
    local FW_LINK=""
    while IFS= read -r xcfw; do
        [ -z "${xcfw}" ] && continue
        local xcfw_name needed
        xcfw_name=$(basename "${xcfw}" .xcframework)
        needed=false
        for n in "${NEEDED_XCFS[@]}"; do
            [[ "${xcfw_name}" == "${n}" ]] && needed=true && break
        done
        "${needed}" || continue
        local slice
        slice=$(ls "${xcfw}" 2>/dev/null | grep "^macos" | head -1 || true)
        if [ -n "${slice}" ]; then
            FW_SEARCH="${FW_SEARCH} -F${xcfw}/${slice}"
            FW_LINK="${FW_LINK} -framework ${xcfw_name}"
        fi
    done < <(find "${TMP_SRC}/.build/artifacts" -name "*.xcframework" -type d 2>/dev/null || true)

    # Create framework structure
    rm -rf "${FW_DIR}"
    mkdir -p "${FW_DIR}/Versions/A/Headers"
    mkdir -p "${FW_DIR}/Versions/A/Modules/SFBAudioEngine.swiftmodule"
    mkdir -p "${FW_DIR}/Versions/A/Resources"

    local XCODE_PATH
    XCODE_PATH=$(xcode-select -p)
    local SWIFT_LIBS="${XCODE_PATH}/Toolchains/XcodeDefault.xctoolchain/usr/lib/swift/macosx"

    echo "[sfb] Linking ${ARCH} dylib..."
    # shellcheck disable=SC2086
    clang -dynamiclib \
        -target "${ARCH}-apple-macos14.0" \
        -install_name "@rpath/SFBAudioEngine.framework/SFBAudioEngine" \
        ${FW_SEARCH} \
        -L"${SWIFT_LIBS}" \
        -framework Foundation -framework CoreFoundation -framework AudioToolbox \
        -framework CoreAudio -framework AVFoundation -framework AVFAudio \
        -framework Accelerate -framework ImageIO -framework UniformTypeIdentifiers \
        ${FW_LINK} \
        -fobjc-link-runtime -lc++ \
        ${OBJ_FILES} \
        -o "${FW_DIR}/Versions/A/SFBAudioEngine"

    # Copy Swift metadata and public headers
    # Handle both directory and file cases for .swiftmodule (changed in Swift 6.0+)
    if [ -d "${TMP_SRC}/.build/${ARCH}-apple-macosx/release/Modules/SFBAudioEngine.swiftmodule" ]; then
        cp -R "${TMP_SRC}/.build/${ARCH}-apple-macosx/release/Modules/SFBAudioEngine.swiftmodule/"* "${FW_DIR}/Versions/A/Modules/SFBAudioEngine.swiftmodule/"
    else
        # If it's a file, copy the relevant files into the directory and rename to architecture
        cp "${TMP_SRC}/.build/${ARCH}-apple-macosx/release/Modules/SFBAudioEngine.swiftmodule" "${FW_DIR}/Versions/A/Modules/SFBAudioEngine.swiftmodule/${ARCH}.swiftmodule"
        [ -f "${TMP_SRC}/.build/${ARCH}-apple-macosx/release/Modules/SFBAudioEngine.swiftdoc" ] && cp "${TMP_SRC}/.build/${ARCH}-apple-macosx/release/Modules/SFBAudioEngine.swiftdoc" "${FW_DIR}/Versions/A/Modules/SFBAudioEngine.swiftmodule/${ARCH}.swiftdoc"
        [ -f "${TMP_SRC}/.build/${ARCH}-apple-macosx/release/Modules/SFBAudioEngine.swiftsourceinfo" ] && cp "${TMP_SRC}/.build/${ARCH}-apple-macosx/release/Modules/SFBAudioEngine.swiftsourceinfo" "${FW_DIR}/Versions/A/Modules/SFBAudioEngine.swiftmodule/${ARCH}.swiftsourceinfo"
        [ -f "${TMP_SRC}/.build/${ARCH}-apple-macosx/release/Modules/SFBAudioEngine.abi.json" ] && cp "${TMP_SRC}/.build/${ARCH}-apple-macosx/release/Modules/SFBAudioEngine.abi.json" "${FW_DIR}/Versions/A/Modules/SFBAudioEngine.swiftmodule/${ARCH}.abi.json"
    fi

    # Copy generated Swift header and C headers
    cp "${TMP_SRC}/.build/${ARCH}-apple-macosx/release/SFBAudioEngine.build/include/SFBAudioEngine-Swift.h" "${FW_DIR}/Versions/A/Headers/"
    cp "${TMP_SRC}/Sources/CSFBAudioEngine/include/SFBAudioEngine/"*.h "${FW_DIR}/Versions/A/Headers/"

    # Create module map
    cat > "${FW_DIR}/Versions/A/Modules/module.modulemap" <<EOF
framework module SFBAudioEngine {
    umbrella "Headers"
    export *
    module * { export * }
}
EOF

    # Create Info.plist for the framework
    cat > "${FW_DIR}/Versions/A/Resources/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleDevelopmentRegion</key>
	<string>en</string>
	<key>CFBundleExecutable</key>
	<string>SFBAudioEngine</string>
	<key>CFBundleIdentifier</key>
	<string>dev.sbooth.SFBAudioEngine</string>
	<key>CFBundleInfoDictionaryVersion</key>
	<string>6.0</string>
	<key>CFBundleName</key>
	<string>SFBAudioEngine</string>
	<key>CFBundlePackageType</key>
	<string>FMWK</string>
	<key>CFBundleShortVersionString</key>
	<string>${VERSION}</string>
	<key>CFBundleSignature</key>
	<string>????</string>
	<key>CFBundleVersion</key>
	<string>1</string>
	<key>NSPrincipalClass</key>
	<string></string>
</dict>
</plist>
EOF

    # Versioned symlinks
    (cd "${FW_DIR}/Versions" && ln -sf A Current)
    ln -sf Versions/Current/SFBAudioEngine "${FW_DIR}/SFBAudioEngine"
    ln -sf Versions/Current/Headers "${FW_DIR}/Headers"
    ln -sf Versions/Current/Modules "${FW_DIR}/Modules"
    ln -sf Versions/Current/Resources "${FW_DIR}/Resources"
    cp "${TMP_SRC}/LICENSE.txt" "${FW_DIR}/Versions/A/Resources/LICENSE"
}

trim_xcframework_for_macos() {
    local XCF="$1"
    [ -d "${XCF}" ] || return 0

    # Keep only macOS slices (supports names like macos-arm64, macos-x86_64, macos-arm64_x86_64).
    find "${XCF}" -mindepth 1 -maxdepth 1 -type d | while read -r dir; do
        local base
        base=$(basename "${dir}")
        [[ "${base}" == macos* ]] || rm -rf "${dir}"
    done

    # Remove debug symbol bundles if present.
    find "${XCF}" -name "*.dSYM" -type d -prune -exec rm -rf {} +

    # Strip local symbols from embedded framework binaries.
    find "${XCF}" -type f | while read -r f; do
        if file "${f}" | grep -q "Mach-O"; then
            strip -x "${f}" 2>/dev/null || true
        fi
    done
}

# 3. Resolve packages once (sequential) so parallel builds don't race on downloads
echo "[sfb] Resolving Swift packages..."
(cd "${TMP_SRC}" && swift package resolve)

# 4. Build arm64 and x86_64 in parallel
rm -rf "${TMP_FW}"
build_arch arm64 &
PID_ARM=$!
build_arch x86_64 &
PID_X86=$!
wait $PID_ARM $PID_X86

# 5. Create final XCFramework (manually to match project structure and avoid xcodebuild issues)
echo "[sfb] Assembling XCFramework..."
rm -rf "${OUT_DIR}/SFBAudioEngine.xcframework"
mkdir -p "${OUT_DIR}/SFBAudioEngine.xcframework/macos-arm64"
mkdir -p "${OUT_DIR}/SFBAudioEngine.xcframework/macos-x86_64"

cp -R "${TMP_FW}/arm64/SFBAudioEngine.framework" "${OUT_DIR}/SFBAudioEngine.xcframework/macos-arm64/"
cp -R "${TMP_FW}/x86_64/SFBAudioEngine.framework" "${OUT_DIR}/SFBAudioEngine.xcframework/macos-x86_64/"
trim_xcframework_for_macos "${OUT_DIR}/SFBAudioEngine.xcframework"

# ... (Info.plist generation)

# 6. Collect dependency XCFrameworks (whitelist only — remove any stale ones first)
echo "[sfb] Collecting dependency XCFrameworks..."
# Remove any xcframeworks that are no longer needed (e.g. from a previous build with more deps)
find "${OUT_DIR}" -maxdepth 1 -name "*.xcframework" -not -name "SFBAudioEngine.xcframework" -type d | while read -r stale; do
    stale_name=$(basename "${stale}" .xcframework)
    keep=false
    for n in "${NEEDED_XCFS[@]}"; do
        [[ "${stale_name}" == "${n}" ]] && keep=true && break
    done
    "${keep}" || { echo "[sfb] Removing stale ${stale_name}.xcframework..."; rm -rf "${stale}"; }
done
# Copy only the needed binary xcframeworks
while IFS= read -r xcfw; do
    [ -z "${xcfw}" ] && continue
    xcfw_name=$(basename "${xcfw}" .xcframework)
    needed=false
    for n in "${NEEDED_XCFS[@]}"; do
        [[ "${xcfw_name}" == "${n}" ]] && needed=true && break
    done
    "${needed}" || continue
    echo "[sfb] Copying ${xcfw_name}.xcframework..."
    cp -R "${xcfw}" "${OUT_DIR}/"
    trim_xcframework_for_macos "${OUT_DIR}/${xcfw_name}.xcframework"
done < <(find "${TMP_SRC}/.build/artifacts" -name "*.xcframework" -type d 2>/dev/null || true)

echo "[sfb] Done: ${OUT_DIR}"
