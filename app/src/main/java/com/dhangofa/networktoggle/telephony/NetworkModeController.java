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
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.config.AppPreferences;

public final class NetworkModeController {
    private final XiaomiFiveGModeController xiaomiFiveGController;
    private final ShizukuBinderModeController shizukuBinderController;
    private final LegacyRootModeController legacyController;
    private final ModernRootModeController modernRootController;
    private final SimResolver simResolver;
    private final NetworkModeReader reader;

    public NetworkModeController(SimResolver simResolver) {
        this.simResolver = simResolver;
        this.reader = new NetworkModeReader(simResolver.getContext(), new AppPreferences(simResolver.getContext()), simResolver);
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

        if (networkMode == null || networkMode == NetworkMode.UNKNOWN) {
            return CommandResult.failed("", "Invalid network mode selected");
        }
        SimResolver.SimInfo info = simResolver.resolveTargetSimInfo(executionMode);
        if (info == null) return CommandResult.failed("", "Target SIM unavailable");
        TargetSim target = info.slotIndex == 0 ? TargetSim.SIM_1 : TargetSim.SIM_2;
        TargetSim previousOverride = simResolver.getOverrideTargetSim();
        simResolver.setOverrideTargetSim(target);
        try {

            // Xiaomi preference and Android USER RAT restrictions must agree. Older
            // Only-mode switches may have left NR excluded even when the OEM toggle is on.
            CommandResult xiaomiResult =
                    xiaomiFiveGController.applyIfSupported(networkMode, executionMode);
            if (xiaomiResult != null && !xiaomiResult.isSuccess()) {
                return xiaomiResult;
            }

            CommandResult result;
            if (executionMode == ExecutionMode.SHIZUKU) {
                result = shizukuBinderController.apply(networkMode, executionMode);
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                result = legacyController.apply(networkMode, executionMode);
            } else {
                result = modernRootController.apply(networkMode, executionMode);
            }
            if (!result.isSuccess()) return result;
            return NetworkModeVerifier.verify(networkMode, result,
                    () -> simResolver.resolveTargetSubId(executionMode, target) == info.subId
                            ? reader.readBack(executionMode, target) : null,
                    16, () -> Thread.sleep(350));
        } finally {
            simResolver.setOverrideTargetSim(previousOverride);
        }
    }
}
