package com.dhangofa.networktoggle.telephony;

/** Main entry point for reading the current active network mode. */
import android.content.Context;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;

public final class NetworkModeReader {
	private final AppPreferences appPreferences;
	private final XiaomiFiveGModeController xiaomiFiveGController;
	private final ShizukuBinderModeReader shizukuBinderReader;
	private final PrivilegedModeReader privilegedModeReader;

	public NetworkModeReader(Context context, AppPreferences appPreferences, SimResolver simResolver) {
		this.appPreferences = appPreferences;
		this.xiaomiFiveGController = new XiaomiFiveGModeController(simResolver);
		this.shizukuBinderReader = new ShizukuBinderModeReader(simResolver);
		this.privilegedModeReader = new PrivilegedModeReader(context, simResolver);
	}

	public NetworkMode readCurrentMode() {
		return readCurrentMode(appPreferences.getTargetSim());
	}

	public NetworkMode readCurrentMode(TargetSim targetSim) {
		ExecutionMode executionMode = appPreferences.getExecutionMode();
		if (executionMode == ExecutionMode.NONE) {
			return NetworkMode.UNKNOWN;
		}

		if (targetSim == TargetSim.BOTH) {
			NetworkMode mode1 = readCurrentMode(TargetSim.SIM_1);
			NetworkMode mode2 = readCurrentMode(TargetSim.SIM_2);

			return mode1 != NetworkMode.UNKNOWN && mode1 == mode2
					? mode1
					: NetworkMode.UNKNOWN;
		}

		return readSingleMode(executionMode, targetSim);
	}

	private NetworkMode readSingleMode(ExecutionMode executionMode, TargetSim targetSim) {
		return readBack(executionMode, targetSim).displayMode();
	}

	NetworkModeReadback readBack(ExecutionMode executionMode, TargetSim targetSim) {
		NetworkMode mode = NetworkMode.UNKNOWN;
		// 1. Shizuku Fast-Path (Binder IPC)
		if (executionMode == ExecutionMode.SHIZUKU) {
			mode = shizukuBinderReader.readCurrentMode(executionMode, targetSim);
		}

		// 2. Root/Shizuku privileged shell fallback path
		if (mode == NetworkMode.UNKNOWN) {
			mode = privilegedModeReader.readCurrentMode(executionMode, targetSim);
		}
		NetworkMode effective = executionMode == ExecutionMode.SHIZUKU
				? shizukuBinderReader.readEffectiveMode(executionMode, targetSim)
				: privilegedModeReader.readEffectiveMode(executionMode, targetSim);
		boolean vendor = xiaomiFiveGController.isSupported();
		return new NetworkModeReadback(mode, effective,
				vendor ? xiaomiFiveGController.readEnabled(executionMode, targetSim) : null, vendor);
	}

	public String describeCurrentState(TargetSim targetSim) {
		ExecutionMode mode = appPreferences.getExecutionMode();
		return readBack(mode, targetSim).toString() + (mode == ExecutionMode.SHIZUKU
				? "\n" + shizukuBinderReader.describeState(mode, targetSim) : "");
	}

	public NetworkMode refreshCache() {
		TargetSim target = appPreferences.getTargetSim();
		ExecutionMode execution = appPreferences.getExecutionMode();
		NetworkMode mode = readCurrentMode(target);
		if (appPreferences.getTargetSim() == target && appPreferences.getExecutionMode() == execution) {
			appPreferences.setCachedNetworkMode(mode);
			appPreferences.setLastNetworkCheckTimestamp(System.currentTimeMillis());
		}
		return mode;
	}
}
