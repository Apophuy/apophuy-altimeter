#!/usr/bin/env bash

# Source this file from repository scripts that need the Android SDK.
if [[ -n "${ANDROID_HOME:-}" && -d "${ANDROID_HOME}" ]]; then
    altimeter_android_sdk="${ANDROID_HOME}"
elif [[ -n "${ANDROID_SDK_ROOT:-}" && -d "${ANDROID_SDK_ROOT}" ]]; then
    altimeter_android_sdk="${ANDROID_SDK_ROOT}"
else
    altimeter_project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
    altimeter_local_sdk=""
    if [[ -f "${altimeter_project_root}/local.properties" ]]; then
        altimeter_local_sdk="$(sed -n 's/^sdk\.dir=//p' "${altimeter_project_root}/local.properties" | tail -n 1)"
    fi
    if [[ -n "${altimeter_local_sdk}" && -d "${altimeter_local_sdk}" ]]; then
        altimeter_android_sdk="${altimeter_local_sdk}"
    elif [[ -d "${HOME}/Android/Sdk" ]]; then
        altimeter_android_sdk="${HOME}/Android/Sdk"
    elif [[ -d "/opt/android-sdk" ]]; then
        altimeter_android_sdk="/opt/android-sdk"
    elif [[ -d "/usr/lib/android-sdk" ]]; then
        altimeter_android_sdk="/usr/lib/android-sdk"
    else
        printf '%s\n' \
            'Android SDK not found. Set ANDROID_HOME/ANDROID_SDK_ROOT or sdk.dir in local.properties.' >&2
        return 1 2>/dev/null || exit 1
    fi
fi

export ANDROID_HOME="${altimeter_android_sdk}"
export ANDROID_SDK_ROOT="${altimeter_android_sdk}"
export PATH="${altimeter_android_sdk}/platform-tools:${altimeter_android_sdk}/emulator:${altimeter_android_sdk}/cmdline-tools/latest/bin:${PATH}"

unset altimeter_android_sdk altimeter_project_root altimeter_local_sdk
