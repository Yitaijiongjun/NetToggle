package com.dhangofa.networktoggle.telephony;

import android.os.Build;

import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

import java.lang.reflect.Method;

/**
 * Direct HyperOS/MIUI user-5G backend.
 *
 * This build is intentionally device-specific: Preferred 5G / Preferred 4G
 * always use Xiaomi's internal miui.telephony.TelephonyManager API and never
 * fall back to rewriting Android's full allowed-network-types bitmask.
 */
final class XiaomiFiveGModeController {
    private static final String MIUI_TELEPHONY_MANAGER = "miui.telephony.TelephonyManager";

    XiaomiFiveGModeController(SimResolver ignored) {
        // Kept in the constructor signature so NetworkModeController wiring stays simple.
    }

    CommandResult applyIfSupported(NetworkMode networkMode, ExecutionMode executionMode) {
        if (networkMode != NetworkMode.PREFERRED_5G
                && networkMode != NetworkMode.PREFERRED_4G) {
            return null;
        }

        boolean enabled = networkMode == NetworkMode.PREFERRED_5G;
        return setUserFiveGEnabled(enabled);
    }

    NetworkMode refinePreferredRead(NetworkMode genericMode) {
        if (genericMode != NetworkMode.PREFERRED_5G
                && genericMode != NetworkMode.PREFERRED_4G) {
            return genericMode;
        }

        Boolean enabled = readUserFiveGEnabled();
        if (enabled == null) {
            return genericMode;
        }
        return enabled ? NetworkMode.PREFERRED_5G : NetworkMode.PREFERRED_4G;
    }

    private CommandResult setUserFiveGEnabled(boolean enabled) {
        try {
            Object manager = getManager();
            if (manager == null) {
                return CommandResult.failed(
                        "MIUI setUserFiveGEnabled",
                        "miui.telephony.TelephonyManager.getDefault() returned null.");
            }

            Class<?> clazz = Class.forName(MIUI_TELEPHONY_MANAGER);
            Method setter = clazz.getDeclaredMethod("setUserFiveGEnabled", boolean.class);
            setter.setAccessible(true);
            setter.invoke(manager, enabled);

            // Match the behavior of dedicated Xiaomi 5G switchers: the setter is
            // authoritative. Verify briefly when the getter exists, but do not
            // replace this path with the generic Android RAT-mask API.
            try {
                Method getter = clazz.getDeclaredMethod("isUserFiveGEnabled");
                getter.setAccessible(true);

                for (int i = 0; i < 6; i++) {
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
                        "MIUI API did not report the requested state.");
            } catch (NoSuchMethodException ignored) {
                return CommandResult.completed(
                        "MIUI setUserFiveGEnabled",
                        0,
                        "Applied through miui.telephony.TelephonyManager.",
                        "");
            }
        } catch (Throwable throwable) {
            return CommandResult.failed(
                    "MIUI setUserFiveGEnabled",
                    describe(throwable));
        }
    }

    private Boolean readUserFiveGEnabled() {
        try {
            Object manager = getManager();
            if (manager == null) return null;

            Class<?> clazz = Class.forName(MIUI_TELEPHONY_MANAGER);
            Method getter = clazz.getDeclaredMethod("isUserFiveGEnabled");
            getter.setAccessible(true);
            Object value = getter.invoke(manager);
            return value instanceof Boolean ? (Boolean) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object getManager() throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions(
                    "Lmiui/telephony/");
        }

        Class<?> clazz = Class.forName(MIUI_TELEPHONY_MANAGER);
        Method getDefault = clazz.getDeclaredMethod("getDefault");
        getDefault.setAccessible(true);
        return getDefault.invoke(null);
    }

    private static String describe(Throwable throwable) {
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        return message == null || message.trim().isEmpty()
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + message;
    }
}
