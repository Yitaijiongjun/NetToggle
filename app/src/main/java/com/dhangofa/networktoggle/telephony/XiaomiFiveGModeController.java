package com.dhangofa.networktoggle.telephony;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

import java.lang.reflect.Method;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * HyperOS/MIUI preferred-5G backend modeled after FiveGSwitcher.
 *
 * Flow:
 * 1) Try Xiaomi's internal TelephonyManager API.
 * 2) If it did not take effect, grant this app WRITE_SECURE_SETTINGS through
 *    the selected privileged backend and write fiveg_user_enable via the app's
 *    own ContentResolver.
 * 3) Wait two seconds, re-check the Xiaomi getter, and if needed invoke the
 *    internal setter once more.
 *
 * Preferred 5G / Preferred 4G never fall back to Android's full RAT bitmask.
 */
final class XiaomiFiveGModeController {
    private static final String MIUI_TELEPHONY_MANAGER =
            "miui.telephony.TelephonyManager";
    private static final String FIVE_G_USER_ENABLE = "fiveg_user_enable";
    private static final long DOUBLE_CHECK_DELAY_MS = 2_000L;

    private final Context context;

    XiaomiFiveGModeController(SimResolver simResolver) {
        this.context = simResolver.getContext().getApplicationContext();
    }

    CommandResult applyIfSupported(
            NetworkMode networkMode,
            ExecutionMode executionMode) {
        if (networkMode != NetworkMode.PREFERRED_5G
                && networkMode != NetworkMode.PREFERRED_4G) {
            return null;
        }

        boolean enabled = networkMode == NetworkMode.PREFERRED_5G;
        return applyFiveGUserPreference(enabled, executionMode);
    }

    NetworkMode refinePreferredRead(
            NetworkMode genericMode,
            ExecutionMode executionMode) {
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

    private CommandResult applyFiveGUserPreference(
            boolean enabled,
            ExecutionMode executionMode) {
        StringBuilder trace = new StringBuilder();

        // FiveGSwitcher auto-mode first tries the Xiaomi internal API.
        if (tryMiuiSetterAndVerify(enabled, trace, "initial")) {
            return CommandResult.completed(
                    "MIUI setUserFiveGEnabled",
                    0,
                    trace.toString(),
                    "");
        }

        // Its Shizuku path grants WRITE_SECURE_SETTINGS to the app itself,
        // then calls Settings.Global.putInt through the app ContentResolver.
        CommandResult grantResult = ensureWriteSecureSettings(executionMode);
        if (!grantResult.isSuccess()) {
            return CommandResult.failed(
                    "grant WRITE_SECURE_SETTINGS",
                    "Unable to grant WRITE_SECURE_SETTINGS. "
                            + describeResult(grantResult)
                            + " | trace=" + trace);
        }

        boolean putOk;
        try {
            putOk = Settings.Global.putInt(
                    context.getContentResolver(),
                    FIVE_G_USER_ENABLE,
                    enabled ? 1 : 0);
        } catch (Throwable throwable) {
            return CommandResult.failed(
                    "Settings.Global.putInt(" + FIVE_G_USER_ENABLE + ")",
                    describe(throwable) + " | trace=" + trace);
        }

        trace.append("contentResolverPut=").append(putOk).append(';');
        if (!putOk) {
            return CommandResult.failed(
                    "Settings.Global.putInt(" + FIVE_G_USER_ENABLE + ")",
                    "SettingsProvider returned false. trace=" + trace);
        }

        try {
            Thread.sleep(DOUBLE_CHECK_DELAY_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }

        Boolean afterSetting = readUserFiveGEnabled();
        trace.append("after2s=").append(afterSetting).append(';');
        if (afterSetting != null && afterSetting == enabled) {
            return CommandResult.completed(
                    "Settings.Global.putInt(" + FIVE_G_USER_ENABLE + ")",
                    0,
                    trace.toString(),
                    "");
        }

        // FiveGSwitcher performs this delayed second internal-API attempt when
        // the privileged setting write alone has not reached telephony state.
        if (tryMiuiSetterAndVerify(enabled, trace, "delayed")) {
            return CommandResult.completed(
                    "MIUI setUserFiveGEnabled (delayed)",
                    0,
                    trace.toString(),
                    "");
        }

        return CommandResult.failed(
                "HyperOS 5G switch",
                "FiveGSwitcher-compatible flow completed but Xiaomi's getter "
                        + "still does not report the requested state. trace=" + trace);
    }

    private boolean tryMiuiSetterAndVerify(
            boolean enabled,
            StringBuilder trace,
            String stage) {
        try {
            Object manager = getMiuiManager();
            if (manager == null) {
                trace.append(stage).append(":manager=null;");
                return false;
            }

            Class<?> clazz = Class.forName(MIUI_TELEPHONY_MANAGER);
            Method setter =
                    clazz.getDeclaredMethod("setUserFiveGEnabled", boolean.class);
            setter.setAccessible(true);
            setter.invoke(manager, enabled);

            Boolean actual = readUserFiveGEnabled();
            trace.append(stage)
                    .append(":setterOk,getter=")
                    .append(actual)
                    .append(';');
            return actual != null && actual == enabled;
        } catch (Throwable throwable) {
            trace.append(stage)
                    .append(":")
                    .append(describe(throwable))
                    .append(';');
            return false;
        }
    }

    /**
     * Mirrors FiveGSwitcher's state read: prefer Xiaomi's getter and only fall
     * back to fiveg_user_enable when the Xiaomi API cannot be read at all.
     */
    private Boolean readUserFiveGEnabled() {
        try {
            Object manager = getMiuiManager();
            if (manager != null) {
                Class<?> clazz = Class.forName(MIUI_TELEPHONY_MANAGER);
                Method getter =
                        clazz.getDeclaredMethod("isUserFiveGEnabled");
                getter.setAccessible(true);
                Object value = getter.invoke(manager);
                if (value instanceof Boolean) {
                    return (Boolean) value;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to Settings.Global, matching FiveGSwitcher.
        }

        try {
            return Settings.Global.getInt(
                    context.getContentResolver(),
                    FIVE_G_USER_ENABLE) == 1;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private Object getMiuiManager() throws Exception {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions(
                    "Lmiui/telephony/");
        }

        Class<?> clazz = Class.forName(MIUI_TELEPHONY_MANAGER);
        Method getDefault = clazz.getDeclaredMethod("getDefault");
        getDefault.setAccessible(true);
        return getDefault.invoke(null);
    }

    private CommandResult ensureWriteSecureSettings(
            ExecutionMode executionMode) {
        if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                == PackageManager.PERMISSION_GRANTED) {
            return CommandResult.completed(
                    "check WRITE_SECURE_SETTINGS",
                    0,
                    "already granted",
                    "");
        }

        if (executionMode == ExecutionMode.SHIZUKU) {
            return grantWriteSecureSettingsViaShizuku();
        }

        if (executionMode == ExecutionMode.ROOT) {
            CommandExecutor executor =
                    CommandExecutorFactory.forMode(ExecutionMode.ROOT);
            if (executor == null) {
                return CommandResult.failed(
                        "pm grant",
                        "Root executor unavailable.");
            }

            CommandResult result = executor.execute(
                    "pm grant "
                            + context.getPackageName()
                            + " "
                            + Manifest.permission.WRITE_SECURE_SETTINGS);
            if (!result.isSuccess()) {
                return result;
            }

            return context.checkSelfPermission(
                    Manifest.permission.WRITE_SECURE_SETTINGS)
                    == PackageManager.PERMISSION_GRANTED
                    ? CommandResult.completed(
                            result.getCommand(),
                            0,
                            "WRITE_SECURE_SETTINGS granted via root.",
                            "")
                    : CommandResult.failed(
                            result.getCommand(),
                            "pm grant returned success but permission is still absent.");
        }

        return CommandResult.failed(
                "grant WRITE_SECURE_SETTINGS",
                "Unsupported execution mode.");
    }

    private CommandResult grantWriteSecureSettingsViaShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                return CommandResult.failed(
                        "Shizuku package grant",
                        "Shizuku binder is unavailable.");
            }
            if (Shizuku.checkSelfPermission()
                    != PackageManager.PERMISSION_GRANTED) {
                return CommandResult.failed(
                        "Shizuku package grant",
                        "Shizuku permission is not granted.");
            }

            IBinder raw = SystemServiceHelper.getSystemService("package");
            if (raw == null || !raw.pingBinder()) {
                return CommandResult.failed(
                        "Shizuku package grant",
                        "PackageManager binder is unavailable.");
            }

            IBinder wrapped = new ShizukuBinderWrapper(raw);
            Class<?> stub =
                    Class.forName("android.content.pm.IPackageManager$Stub");
            Object packageManager =
                    stub.getMethod("asInterface", IBinder.class)
                            .invoke(null, wrapped);
            if (packageManager == null) {
                return CommandResult.failed(
                        "Shizuku package grant",
                        "Unable to obtain IPackageManager.");
            }

            Class<?> api =
                    Class.forName("android.content.pm.IPackageManager");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                org.lsposed.hiddenapibypass.HiddenApiBypass.invoke(
                        api,
                        packageManager,
                        "grantRuntimePermission",
                        context.getPackageName(),
                        Manifest.permission.WRITE_SECURE_SETTINGS,
                        0);
            } else {
                Method grant = api.getMethod(
                        "grantRuntimePermission",
                        String.class,
                        String.class,
                        int.class);
                grant.invoke(
                        packageManager,
                        context.getPackageName(),
                        Manifest.permission.WRITE_SECURE_SETTINGS,
                        0);
            }

            if (context.checkSelfPermission(
                    Manifest.permission.WRITE_SECURE_SETTINGS)
                    != PackageManager.PERMISSION_GRANTED) {
                return CommandResult.failed(
                        "Shizuku package grant",
                        "IPackageManager call returned but permission is not granted.");
            }

            return CommandResult.completed(
                    "Shizuku IPackageManager.grantRuntimePermission",
                    0,
                    "WRITE_SECURE_SETTINGS granted.",
                    "");
        } catch (Throwable throwable) {
            return CommandResult.failed(
                    "Shizuku IPackageManager.grantRuntimePermission",
                    describe(throwable));
        }
    }

    private static String describeResult(CommandResult result) {
        if (result == null) return "no result";
        String exception = result.getExceptionMessage();
        if (exception != null && !exception.trim().isEmpty()) {
            return exception;
        }
        String stderr = result.getStderr();
        if (stderr != null && !stderr.trim().isEmpty()) {
            return stderr;
        }
        return "exit=" + result.getExitCode();
    }

    private static String describe(Throwable throwable) {
        Throwable cause =
                throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        return message == null || message.trim().isEmpty()
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + message;
    }
}
