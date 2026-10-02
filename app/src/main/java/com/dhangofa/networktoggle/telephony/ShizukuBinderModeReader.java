package com.dhangofa.networktoggle.telephony;

/**
 * Reads the current network mode using the Shizuku Binder.
 * Like the controller, it makes direct binder calls to ITelephony to read the state without root.
 */

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.IBinder;

import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

import java.lang.reflect.Method;

import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

final class ShizukuBinderModeReader {

    private static final String PACKAGE = "com.dhangofa.networktoggle";
    private final SimResolver simResolver;

    ShizukuBinderModeReader(SimResolver simResolver) {
        this.simResolver = simResolver;
    }

    NetworkMode readCurrentModeForSubId(int subId) {
        if (!simResolver.isValidSubId(subId)) {
            return NetworkMode.UNKNOWN;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Lcom/android/internal/telephony/");
            }

            IBinder raw = SystemServiceHelper.getSystemService("phone");
            if (raw == null || !raw.pingBinder()) {
                return NetworkMode.UNKNOWN;
            }

            IBinder binder = new ShizukuBinderWrapper(raw);
            if (!binder.pingBinder()) {
                return NetworkMode.UNKNOWN;
            }

            @SuppressLint("PrivateApi")
            Class<?> stub = Class.forName("com.android.internal.telephony.ITelephony$Stub");
            Object phone = stub.getDeclaredMethod("asInterface", IBinder.class).invoke(null, binder);

            Class<?> api = Class.forName("com.android.internal.telephony.ITelephony");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Method method = TelephonyMethodHelper.find(
                        api,
                        "getAllowedNetworkTypesForReason",
                        new Class<?>[] {int.class, int.class},
                        new Class<?>[] {int.class, int.class, String.class},
                        new Class<?>[] {int.class}
                );

                if (method == null) {
                    return NetworkMode.UNKNOWN;
                }

                Object value = method.getParameterCount() == 2
                        ? method.invoke(phone, subId, 0)
                        : method.getParameterCount() == 3
                                ? method.invoke(phone, subId, 0, PACKAGE)
                                : method.invoke(phone, subId);

                if (!(value instanceof Number)) {
                    return NetworkMode.UNKNOWN;
                }

                long bitmask = ((Number) value).longValue();
                return NetworkModeReadback.fromAllowedMask(bitmask);
            }

            Method method = TelephonyMethodHelper.find(
                    api,
                    "getPreferredNetworkType",
                    new Class<?>[] {int.class},
                    new Class<?>[] {int.class, String.class}
            );

            if (method == null) {
                return NetworkMode.UNKNOWN;
            }

            Object value = method.getParameterCount() == 1
                    ? method.invoke(phone, subId)
                    : method.invoke(phone, subId, PACKAGE);

            return value instanceof Number
                    ? NetworkMode.fromLegacyMode(((Number) value).intValue())
                    : NetworkMode.UNKNOWN;
        } catch (Throwable ignored) {
            return NetworkMode.UNKNOWN;
        }
    }

    NetworkMode readEffectiveModeForSubId(int subId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return readCurrentModeForSubId(subId);
        if (!simResolver.isValidSubId(subId)) return NetworkMode.UNKNOWN;
        try {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Lcom/android/internal/telephony/");
            IBinder raw = SystemServiceHelper.getSystemService("phone");
            Class<?> stub = Class.forName("com.android.internal.telephony.ITelephony$Stub");
            Object phone = stub.getDeclaredMethod("asInterface", IBinder.class).invoke(null, new ShizukuBinderWrapper(raw));
            Class<?> api = Class.forName("com.android.internal.telephony.ITelephony");
            Method method = TelephonyMethodHelper.find(api, "getAllowedNetworkTypesBitmask", new Class<?>[] {int.class});
            if (method == null) return NetworkMode.UNKNOWN;
            Object mask = method.invoke(phone, subId);
            return mask instanceof Number ? NetworkModeReadback.fromAllowedMask(((Number) mask).longValue()) : NetworkMode.UNKNOWN;
        } catch (Throwable ignored) { return NetworkMode.UNKNOWN; }
    }

    String describeState(ExecutionMode mode, com.dhangofa.networktoggle.model.TargetSim target) {
        SimResolver.SimInfo info = simResolver.resolveTargetSimInfo(mode, target);
        if (info == null) return "SIM unavailable";
        StringBuilder trace = new StringBuilder("slot=" + info.slotIndex + ", subId=" + info.subId);
        try {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Lcom/android/internal/telephony/");
            IBinder raw = SystemServiceHelper.getSystemService("phone");
            Class<?> stub = Class.forName("com.android.internal.telephony.ITelephony$Stub");
            Object phone = stub.getDeclaredMethod("asInterface", IBinder.class).invoke(null, new ShizukuBinderWrapper(raw));
            Class<?> api = Class.forName("com.android.internal.telephony.ITelephony");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Method reason = TelephonyMethodHelper.find(api, "getAllowedNetworkTypesForReason", new Class<?>[] {int.class, int.class});
                if (reason != null) {
                    for (int i = 0; i < 3; i++) {
                        Object value = reason.invoke(phone, info.subId, i);
                        trace.append(", ").append(new String[] {"USER", "POWER", "CARRIER"}[i]).append("Mask=").append(value);
                    }
                }
                Method effective = TelephonyMethodHelper.find(api, "getAllowedNetworkTypesBitmask", new Class<?>[] {int.class});
                if (effective != null) trace.append(", effectiveMask=").append(effective.invoke(phone, info.subId));
            }
            Method data = TelephonyMethodHelper.find(api, "getDataNetworkTypeForSubscriber",
                    new Class<?>[] {int.class, String.class, String.class}, new Class<?>[] {int.class, String.class});
            if (data != null) trace.append(", dataNetworkType=").append(data.getParameterCount() == 3
                    ? data.invoke(phone, info.subId, "com.android.shell", null)
                    : data.invoke(phone, info.subId, "com.android.shell"));
        } catch (Throwable error) {
            trace.append(", readError=").append(TelephonyMethodHelper.describe(error));
        }
        return trace.toString();
    }
}
