package com.dhangofa.networktoggle.telephony;

import android.os.Build;
import android.telephony.SubscriptionManager;

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Xiaomi/Redmi/POCO fast path for the normal "5G enabled" preference.
 *
 * HyperOS/MIUI exposes a dedicated user 5G switch through
 * miui.telephony.TelephonyManager. Using that switch is much lighter than
 * rewriting the complete allowed-network-types bitmask, and matches the path
 * used by Xiaomi's own "Enable 5G network" setting.
 *
 * This backend only handles Preferred 5G <-> Preferred 4G on the current
 * default-data subscription. All other modes intentionally fall back to the
 * generic Android telephony implementation.
 */
final class XiaomiFiveGModeController {
    private static final String MIUI_TELEPHONY_MANAGER = "miui.telephony.TelephonyManager";
    private static final String FIVE_G_USER_ENABLE = "fiveg_user_enable";

    private final SimResolver simResolver;

    XiaomiFiveGModeController(SimResolver simResolver) {
        this.simResolver = simResolver;
    }

    /**
     * @return null when this backend is not applicable; otherwise the Xiaomi-path result.
     */
    CommandResult applyIfSupported(NetworkMode networkMode, ExecutionMode executionMode) {
        if (!isXiaomiFamily()) {
            return null;
        }

        if (networkMode != NetworkMode.PREFERRED_5G
                && networkMode != NetworkMode.PREFERRED_4G) {
            return null;
        }

        // "Both SIMs" is implemented by temporarily overriding the target twice.
        // Xiaomi's user-5G preference is device/default-data oriented, so do not
        // pretend that it is a per-SIM primitive in that case.
        if (simResolver.getOverrideTargetSim() != null) {
            return null;
        }

        SimResolver.SimInfo simInfo = simResolver.resolveTargetSimInfo(executionMode);
        if (simInfo == null || !simResolver.isValidSubId(simInfo.subId)) {
            return null;
        }

        int defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId();
        if (simResolver.isValidSubId(defaultDataSubId) && simInfo.subId != defaultDataSubId) {
            return null;
        }

        boolean enabled = networkMode == NetworkMode.PREFERRED_5G;

        CommandResult reflected = tryMiuiTelephonyApi(enabled);
        if (reflected != null && reflected.isSuccess()) {
            return reflected;
        }

        return tryPrivilegedSetting(enabled, executionMode);
    }

    private CommandResult tryMiuiTelephonyApi(boolean enabled) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions(
                        "Lmiui/telephony/");
            }

            Class<?> clazz = Class.forName(MIUI_TELEPHONY_MANAGER);
            Method getDefault = clazz.getDeclaredMethod("getDefault");
            getDefault.setAccessible(true);
            Object manager = getDefault.invoke(null);
            if (manager == null) {
                return CommandResult.failed("MIUI setUserFiveGEnabled",
                        "miui.telephony.TelephonyManager.getDefault() returned null.");
            }

            Method setter = clazz.getDeclaredMethod("setUserFiveGEnabled", boolean.class);
            setter.setAccessible(true);
            setter.invoke(manager, enabled);

            // Verify when the getter is exposed. Some HyperOS builds update the
            // backing state asynchronously, so allow a very short settling window.
            try {
                Method getter = clazz.getDeclaredMethod("isUserFiveGEnabled");
                getter.setAccessible(true);
                for (int i = 0; i < 4; i++) {
                    Object value = getter.invoke(manager);
                    if (value instanceof Boolean && ((Boolean) value) == enabled) {
                        return CommandResult.completed(
                                "MIUI setUserFiveGEnabled",
                                0,
                                "Applied through miui.telephony.TelephonyManager.",
                                "");
                    }
                    Thread.sleep(50L);
                }

                return CommandResult.failed(
                        "MIUI setUserFiveGEnabled",
                        "MIUI API returned without reaching the requested state.");
            } catch (NoSuchMethodException ignored) {
                // Setter completed without throwing; older/newer builds may omit
                // the public getter while still applying the preference.
                return CommandResult.completed(
                        "MIUI setUserFiveGEnabled",
                        0,
                        "Applied through miui.telephony.TelephonyManager.",
                        "");
            }
        } catch (Throwable throwable) {
            String message = throwable.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = throwable.getClass().getSimpleName();
            }
            return CommandResult.failed(
                    "MIUI setUserFiveGEnabled",
                    message);
        }
    }

    private CommandResult tryPrivilegedSetting(boolean enabled, ExecutionMode executionMode) {
        CommandExecutor executor = CommandExecutorFactory.forMode(executionMode);
        if (executor == null) {
            return CommandResult.failed(
                    "settings put global " + FIVE_G_USER_ENABLE,
                    "No privileged command executor is available.");
        }

        String expected = enabled ? "1" : "0";
        String command =
                "settings put global " + FIVE_G_USER_ENABLE + " " + expected
                        + " && value=$(settings get global " + FIVE_G_USER_ENABLE + ")"
                        + " && [ \"$value\" = \"" + expected + "\" ]";

        CommandResult result = executor.execute(command);
        if (result.isSuccess()) {
            return CommandResult.completed(
                    command,
                    0,
                    "Applied HyperOS fiveg_user_enable=" + expected + ".",
                    "");
        }

        return result;
    }

    private static boolean isXiaomiFamily() {
        String manufacturer = Build.MANUFACTURER == null
                ? ""
                : Build.MANUFACTURER.toLowerCase(Locale.ROOT);
        String brand = Build.BRAND == null
                ? ""
                : Build.BRAND.toLowerCase(Locale.ROOT);

        return manufacturer.contains("xiaomi")
                || brand.contains("xiaomi")
                || brand.contains("redmi")
                || brand.contains("poco");
    }
}
