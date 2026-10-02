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

	public NetworkModeReader(Context context, AppPreferences appPreferences, SimResolver simResolver) {
		this.appPreferences = appPreferences;
		this.xiaomiFiveGController = new XiaomiFiveGModeController(simResolver);
		this.shizukuBinderReader = new ShizukuBinderModeReader(simResolver);
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
        NetworkMode mode = shizukuBinderReader.readCurrentMode(executionMode, targetSim);
        NetworkMode effective = shizukuBinderReader.readEffectiveMode(executionMode, targetSim);
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
