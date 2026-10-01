package com.dhangofa.networktoggle.telephony;

import com.dhangofa.networktoggle.command.CommandExecutor;
import com.dhangofa.networktoggle.command.CommandExecutorFactory;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;

/**
 * Direct HyperOS/MIUI user-5G backend.
 *
 * Preferred 5G / Preferred 4G use HyperOS' fiveg_user_enable global setting,
 * matching the privileged path used by dedicated Xiaomi 5G switchers.
 * No generic Android RAT-mask fallback is used for these two modes.
 */
final class XiaomiFiveGModeController {
    private static final String FIVE_G_USER_ENABLE = "fiveg_user_enable";

    XiaomiFiveGModeController(SimResolver ignored) {
        // Kept in the constructor signature so NetworkModeController wiring stays simple.
    }

    CommandResult applyIfSupported(NetworkMode networkMode, ExecutionMode executionMode) {
        if (networkMode != NetworkMode.PREFERRED_5G
                && networkMode != NetworkMode.PREFERRED_4G) {
            return null;
        }

        boolean enabled = networkMode == NetworkMode.PREFERRED_5G;
        return writeUserFiveGEnabled(enabled, executionMode);
    }

    NetworkMode refinePreferredRead(NetworkMode genericMode, ExecutionMode executionMode) {
        if (genericMode != NetworkMode.PREFERRED_5G
                && genericMode != NetworkMode.PREFERRED_4G) {
            return genericMode;
        }

        Boolean enabled = readUserFiveGEnabled(executionMode);
        if (enabled == null) {
            return genericMode;
        }
        return enabled ? NetworkMode.PREFERRED_5G : NetworkMode.PREFERRED_4G;
    }

    private CommandResult writeUserFiveGEnabled(
            boolean enabled,
            ExecutionMode executionMode) {
        CommandExecutor executor = CommandExecutorFactory.forMode(executionMode);
        if (executor == null) {
            return CommandResult.failed(
                    "settings put global " + FIVE_G_USER_ENABLE,
                    "No privileged executor is available.");
        }

        String expected = enabled ? "1" : "0";
        String command =
                "settings put global " + FIVE_G_USER_ENABLE + " " + expected
                        + " && settings get global " + FIVE_G_USER_ENABLE;

        CommandResult result = executor.execute(command);
        if (!result.isSuccess()) {
            return result;
        }

        String actual = result.getStdout() == null
                ? ""
                : result.getStdout().trim();

        if (expected.equals(actual)) {
            return CommandResult.completed(
                    command,
                    0,
                    "Applied HyperOS " + FIVE_G_USER_ENABLE + "=" + expected + ".",
                    "");
        }

        return CommandResult.failed(
                command,
                "HyperOS setting readback mismatch. Expected "
                        + expected + " but got '" + actual + "'.");
    }

    private Boolean readUserFiveGEnabled(ExecutionMode executionMode) {
        CommandExecutor executor = CommandExecutorFactory.forMode(executionMode);
        if (executor == null) return null;

        CommandResult result =
                executor.execute("settings get global " + FIVE_G_USER_ENABLE);
        if (!result.isSuccess()) return null;

        String value = result.getStdout() == null
                ? ""
                : result.getStdout().trim();

        if ("1".equals(value)) return true;
        if ("0".equals(value)) return false;
        return null;
    }
}
