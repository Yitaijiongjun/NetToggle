package com.dhangofa.networktoggle.command;

/**
 * Interface for executing shell commands.
 * Provides a common contract to execute privileged Shizuku commands.
 */

import com.dhangofa.networktoggle.model.CommandResult;

public interface CommandExecutor {
    CommandResult execute(String command);
}
