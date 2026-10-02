package com.dhangofa.networktoggle.telephony;

import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

/** Dispatches the vendor preference and USER RAT change through Shizuku. */
public final class NetworkModeController {
    private final XiaomiFiveGModeController xiaomiFiveGController;
    private final ShizukuBinderModeController shizukuBinderController;

    public NetworkModeController(SimResolver simResolver) {
        xiaomiFiveGController = new XiaomiFiveGModeController(simResolver);
        shizukuBinderController = new ShizukuBinderModeController(simResolver);
    }

    public CommandResult apply(NetworkMode networkMode, ExecutionMode executionMode) {
        if (executionMode != ExecutionMode.SHIZUKU) return CommandResult.failed("", "Shizuku required");
        if (networkMode == null || networkMode == NetworkMode.UNKNOWN) return CommandResult.failed("", "Invalid network mode");
        CommandResult vendor = xiaomiFiveGController.applyIfSupported(networkMode, executionMode);
        if (vendor != null && !vendor.isSuccess()) return vendor;
        return shizukuBinderController.apply(networkMode, executionMode);
    }
}
