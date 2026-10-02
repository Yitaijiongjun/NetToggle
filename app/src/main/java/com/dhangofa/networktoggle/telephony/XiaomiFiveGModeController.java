package com.dhangofa.networktoggle.telephony;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
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

    CommandResult applyIfSupported(NetworkMode mode, ExecutionMode executionMode, SimResolver.SimInfo info) {
        if (!isSupported()) return null;
        if (info == null) return CommandResult.failed("Xiaomi 5G", "Target SIM unavailable");
        boolean enabled = mode == NetworkMode.PREFERRED_5G || mode == NetworkMode.FIVE_G_ONLY;
        String command = "IMiuiTelephony.setUserFiveGEnabled(" + enabled + ", slot=" + info.slotIndex + ")";
        try {
            getShizukuPhone().write(enabled, info.slotIndex, () -> defaultDataSlot(executionMode));
            return CommandResult.completed(command, 0, "Vendor Binder setter dispatched", "");
        } catch (Throwable error) {
            return CommandResult.failed(command, TelephonyMethodHelper.describe(error));
        }
    }

    Boolean readEnabled(ExecutionMode executionMode, SimResolver.SimInfo info) {
        if (!isSupported()) return null;
        if (info == null) return null;
        try {
            return getShizukuPhone().read(info.slotIndex, () -> defaultDataSlot(executionMode));
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
        SimResolver.SimInfo info = SimIdentityResolver.resolve(simResolver, mode, TargetSim.AUTO);
        return info == null ? -1 : info.slotIndex;
    }

    private static void exemptHiddenApis() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            org.lsposed.hiddenapibypass.HiddenApiBypass.addHiddenApiExemptions("Lmiui/telephony/", "Landroid/os/");
        }
    }
}
