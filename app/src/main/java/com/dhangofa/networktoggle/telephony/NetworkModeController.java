package com.dhangofa.networktoggle.telephony;

/**
 * Main entry point for changing network modes.
 * It figures out whether to use Root or Shizuku, and whether to use the Modern or Legacy root methods
 * based on the device API level.
 */

import android.os.Build;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

public final class NetworkModeController {
    private final XiaomiFiveGModeController xiaomiFiveGController;
    private final ShizukuBinderModeController shizukuBinderController;
    private final LegacyRootModeController legacyController;
    private final ModernRootModeController modernRootController;

    public NetworkModeController(SimResolver simResolver) {
        this.xiaomiFiveGController = new XiaomiFiveGModeController(simResolver);
        this.shizukuBinderController = new ShizukuBinderModeController(simResolver);
        this.legacyController = new LegacyRootModeController(
                simResolver.getContext(), simResolver);
        this.modernRootController = new ModernRootModeController(simResolver);
    }

    public CommandResult apply(NetworkMode networkMode, ExecutionMode executionMode) {
        if (executionMode == null || executionMode == ExecutionMode.NONE) {
            return CommandResult.failed("", "No execution mode selected.");
        }

        // 1. Preferred 5G / Preferred 4G are intentionally device-specific in
        // this build: always use Xiaomi's internal user-5G API and return its
        // result directly. Never fall back to the full Android RAT bitmask.
        CommandResult xiaomiResult =
                xiaomiFiveGController.applyIfSupported(networkMode, executionMode);
        if (xiaomiResult != null) {
            return xiaomiResult;
        }

        // 2. Shizuku Fast-Path (Binder IPC)
        if (executionMode == ExecutionMode.SHIZUKU) {
            return shizukuBinderController.apply(networkMode, executionMode);
        }

        // 3. Root Legacy Fallback (Android 11 and below)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return legacyController.apply(networkMode, executionMode);
        }

        // 4. Root Modern Fallback (Android 12+)
        return modernRootController.apply(networkMode, executionMode);
    }
}
