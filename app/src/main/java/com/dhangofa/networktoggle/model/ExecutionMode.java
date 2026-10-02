package com.dhangofa.networktoggle.model;

/**
 * Execution state values retained for existing Shizuku call sites.
 */

public enum ExecutionMode {
    NONE(0),
    SHIZUKU(2);

    private final int value;

    ExecutionMode(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static ExecutionMode fromValue(int value) {
        for (ExecutionMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        return NONE;
    }
}
