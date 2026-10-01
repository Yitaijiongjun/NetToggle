package com.dhangofa.networktoggle.telephony;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/** Xiaomi's per-slot 5G preference, called with the selected privileged identity. */
final class XiaomiFiveGModeController {
    private final SimResolver simResolver;

    XiaomiFiveGModeController(SimResolver simResolver) { this.simResolver = simResolver; }

    boolean isSupported() {
        try {
            exemptHiddenApis();
            Class.forName("miui.telephony.IMiuiTelephony");
            return true;
        } catch (ClassNotFoundException ignored) { return false; }
    }

    CommandResult applyIfSupported(NetworkMode mode, ExecutionMode executionMode) {
        if (!isSupported()) return null;
        SimResolver.SimInfo info = simResolver.resolveTargetSimInfo(executionMode);
        if (info == null) return CommandResult.failed("Xiaomi 5G", "Target SIM unavailable");
        boolean enabled = mode == NetworkMode.PREFERRED_5G || mode == NetworkMode.FIVE_G_ONLY;
        String command = "IMiuiTelephony.setUserFiveGEnabled(" + enabled + ", slot=" + info.slotIndex + ")";
        try {
            if (executionMode == ExecutionMode.ROOT) return runRoot("set", info.slotIndex, enabled);
            getShizukuPhone().write(enabled, info.slotIndex, defaultDataSlot(executionMode));
            return CommandResult.completed(command, 0, "Vendor Binder setter dispatched", "");
        } catch (Throwable error) {
            return CommandResult.failed(command, TelephonyMethodHelper.describe(error));
        }
    }

    Boolean readEnabled(ExecutionMode executionMode, TargetSim target) {
        if (!isSupported()) return null;
        SimResolver.SimInfo info = simResolver.resolveTargetSimInfo(executionMode, target);
        if (info == null) return null;
        try {
            if (executionMode == ExecutionMode.ROOT) {
                CommandResult result = runRoot("get", info.slotIndex, false);
                if (!result.isSuccess()) return null;
                for (String line : result.getStdout().split("\\n")) {
                    if ("fiveg=true".equals(line.trim())) return true;
                    if ("fiveg=false".equals(line.trim())) return false;
                }
                return null;
            }
            return getShizukuPhone().read(info.slotIndex, defaultDataSlot(executionMode));
        } catch (Throwable ignored) { return null; }
    }

    private MiuiTelephonyAccess getShizukuPhone() throws Exception {
        if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Shizuku unavailable or unauthorized");
        }
        exemptHiddenApis();
        IBinder raw = SystemServiceHelper.getSystemService("miui.radio.extphone");
        if (raw == null) raw = MiuiTelephonyAccess.findBinder();
        return new MiuiTelephonyAccess(new ShizukuBinderWrapper(raw));
    }

    private int defaultDataSlot(ExecutionMode mode) {
        return simResolver.resolveTargetSlotIndex(mode, TargetSim.AUTO);
    }

    private CommandResult runRoot(String operation, int slot, boolean enabled) {
        String apk = simResolver.getContext().getApplicationInfo().sourceDir;
        String command = "CLASSPATH='" + apk.replace("'", "'\\''")
                + "' app_process /system/bin " + XiaomiFiveGRootPayload.class.getName()
                + " " + operation + " " + slot + " " + defaultDataSlot(ExecutionMode.ROOT) + " " + enabled;
        CommandExecutor executor = CommandExecutorFactory.forMode(ExecutionMode.ROOT);
        return executor.execute(command);
    }

    private static void exemptHiddenApis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Lmiui/telephony/", "Landroid/os/");
        }
    }
}
