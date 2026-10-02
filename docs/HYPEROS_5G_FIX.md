# HyperOS 3 / Android 16 5G switching repair

Target reported by the user: Xiaomi 15 Pro, HyperOS 3, Android 16, Shizuku.

## Findings from the October 1 customization history

- `a2a87ca` / `8740f36` introduced the Xiaomi preference backend; `352efe4` made it authoritative and stopped applying the Android USER network mask for preferred modes.
- `59f2f38`, `3dded96` and `63ba1c0` let Xiaomi/global preference state overwrite the independently read Android network mode.
- `2bc63ca` attempted an ordinary-app `miui.telephony.TelephonyManager` setter, then granted WRITE_SECURE_SETTINGS and wrote `fiveg_user_enable`. The fallback getter could read the exact setting just written. This can prove persistence of the preference, but cannot prove the privileged telephone operation happened.
- The preceding Android Only-mode backend could have left USER restrictions without NR. Enabling the Xiaomi preference alone does not establish that this separate restriction has been removed.
- Cache-only cycling and the five-minute tile refresh interval allowed stale state to drive later clicks. Separate tile and automation code duplicated error/cache handling.

## Reference comparison

[FiveGSwitcher-Shizuku, FiveGUtilsBase.kt at 550ac456](https://github.com/dadaewq/FiveGSwitcher-Shizuku/blob/550ac4562d310fd2b9b218e43dd5ff174b117e5c/app/src/main/java/com/ysy/switcherfiveg/FiveGUtilsBase.kt) obtains `miui.radio.extphone`, creates an IMiuiTelephony interface from a ShizukuBinderWrapper, and invokes the vendor setter with a SIM slot. Its default branch is `master-3`; looking only at earlier activity-based implementations misses this path.

[HyperCeiler FiveGTile](https://github.com/ReChronoRain/HyperCeiler/blob/main/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/rules/systemui/controlcenter/tiles/FiveGTile.java) runs inside SystemUI. Its [TelephonyManager wrapper](https://github.com/ReChronoRain/HyperCeiler/blob/main/library/libhook/src/main/java/com/sevtinge/hyperceiler/libhook/utils/api/TelephonyManager.java) can call the native manager in that privileged process. Copying that reflection call into an ordinary application does not copy SystemUI's identity. The tile observes `fiveg_user_enable` and `dual_nr_enabled` as refresh notifications, not as evidence that an ordinary application's setter was authorized.

Android separately stores allowed RATs by reason. See [ITelephony.aidl](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/telephony/java/com/android/internal/telephony/ITelephony.aidl) and [TelephonyShellCommand](https://android.googlesource.com/platform/packages/services/Telephony/+/refs/heads/main/src/com/android/phone/TelephonyShellCommand.java). AOSP's shell setter can print `failed` with exit code zero; the old root path did not inspect that failure.

## Changes in custom14

1. Use the installed ROM's IMiuiTelephony stub and a Shizuku-wrapped vendor Binder. Prefer `(boolean, slot)` / `(slot)` signatures, and restrict old global-only signatures to the default data SIM. No copied transaction numbers or foreign AIDL implementation are included.
2. Pin the physical SIM for the operation. Apply the Xiaomi 5G preference and the Android USER RAT mask together. 5G Only and 4G Only also reconcile the vendor preference.
3. Require two consecutive matching readbacks, up to 16 checks spaced 350 ms apart. Check USER configuration, effective RAT restrictions and the per-slot Xiaomi preference. Permission errors, unreadable state, subscription replacement, unstable state and restrictive policy are failures, not optimistic success.
4. Never write `fiveg_user_enable` or grant WRITE_SECURE_SETTINGS. Preserve carrier/power policy restrictions. Root vendor calls run under root through a kept app_process entry point; modern root reads telephone services rather than cached settings.
5. Share the verified automation path across tiles, shortcuts and cycle synchronization. Refresh the selected SIM's cache even after a partial failure; an action on another SIM cannot replace the selected SIM's state with its requested mode.
6. Read current state before choosing the next tile mode. Refresh whenever the tile becomes visible, observe relevant settings while listening, and share an in-flight guard with the transparent collapse activity. Authorization checks no longer erase command failures.
7. Diagnostics include USER/effective/vendor state, per-slot subscription IDs, raw USER/POWER/CARRIER masks and the reported data network type where available.

## Validation

Regression tests run in GitHub Actions before release/debug builds. They cover stale USER restrictions, carrier/power blocks, unknown vendor/USER reads, transient matches, interrupted verification, generic devices, Only-mode mismatches and RAT-mask classification.

The user requested GitHub Actions builds only and no phone connection. No claim of Xiaomi 15 Pro radio attachment verification is made. Preferred 5G permits NR; actual attachment still depends on coverage, carrier provisioning and policy. Android's data network type may remain LTE for NSA even when a 5G display indicator is shown. Inspect the live diagnostic fields if the status-bar icon does not change.

## Scope after custom15

The preceding repair notes describe custom14. custom15 removes all Root payloads, external broadcast automation and guide/navigation pages. The Shizuku Binder and verification repair is retained; see [SHIZUKU_ONLY.md](SHIZUKU_ONLY.md).
