package com.dhangofa.networktoggle.command;

/** Shared Shizuku command executor. */

import com.dhangofa.networktoggle.model.ExecutionMode;

public final class CommandExecutorFactory {
    private static final CommandExecutor SHIZUKU = new ShizukuCommandExecutor();

    private CommandExecutorFactory() {}

    public static CommandExecutor forMode(ExecutionMode mode) {
        if (mode == ExecutionMode.SHIZUKU) return SHIZUKU;
        return null;
    }
}
