package com.dhangofa.networktoggle.telephony;

import android.content.Context;
import android.content.pm.PackageManager;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import rikka.shizuku.Shizuku;

/** Shared in-app action for tiles and launcher shortcuts. No external automation API. */
public final class NetworkActionExecutor {
    private NetworkActionExecutor() {}

    public static CommandResult apply(Context context, NetworkMode mode, TargetSim target,
            boolean rememberPreferred, String source) {
        AppPreferences prefs = new AppPreferences(context);
        SimResolver resolver = new SimResolver(context, prefs);
        CommandResult result = CommandResult.failed(source, "Network operation not attempted");
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                prefs.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                result = CommandResult.failed(source, "Shizuku unavailable or unauthorized");
                return result;
            }
            if (mode == null || mode == NetworkMode.UNKNOWN || target == null) {
                result = CommandResult.failed(source, "Invalid network action");
                return result;
            }
            NetworkModeController controller = new NetworkModeController(resolver);
            NetworkModeReader reader = new NetworkModeReader(context, prefs, resolver);
            TargetSim[] targets = target == TargetSim.BOTH
                    ? new TargetSim[] {TargetSim.SIM_1, TargetSim.SIM_2} : new TargetSim[] {target};
            boolean success = true;
            StringBuilder trace = new StringBuilder();
            for (TargetSim single : targets) {
                resolver.setOverrideTargetSim(single);
                SimResolver.SimInfo info = SimIdentityResolver.resolve(resolver, ExecutionMode.SHIZUKU, single);
                CommandResult attempt;
                if (info == null) {
                    attempt = CommandResult.failed(source, single + " is unavailable");
                } else if (!supportsMode(prefs, info.slotIndex, mode)) {
                    attempt = CommandResult.failed(source, mode + " is not supported by " + single);
                } else {
                    TargetSim physical = info.slotIndex == 0 ? TargetSim.SIM_1 : TargetSim.SIM_2;
                    resolver.setOverrideTargetSim(physical);
                    attempt = controller.apply(mode, ExecutionMode.SHIZUKU, info);
                    if (attempt.isSuccess()) {
                        attempt = NetworkModeVerifier.verify(mode, attempt,
                                () -> SimIdentityResolver.stillMatches(resolver, info)
                                        ? reader.readBack(ExecutionMode.SHIZUKU, info) : null,
                                51, () -> Thread.sleep(100));
                    }
                }
                success &= attempt.isSuccess();
                trace.append(single).append(": ").append(attempt.getCommand()).append('\n')
                        .append(attempt.getStdout()).append(attempt.getStderr())
                        .append(attempt.getExceptionMessage()).append('\n');
            }
            result = success ? CommandResult.completed(source, 0, trace.toString(), "")
                    : CommandResult.failed(source, trace.toString());
            if (success) {
                prefs.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                prefs.setAutoSimError(false);
                if (rememberPreferred && target == prefs.getTargetSim()) prefs.setLastUserSelectedMode(mode);
            }
            return result;
        } catch (Throwable error) {
            result = CommandResult.failed(source, error.toString());
            return result;
        } finally {
            resolver.setOverrideTargetSim(null);
            if (!result.isSuccess()) {
                if (prefs.getTileErrorState() != AppPreferences.TILE_ERROR_SHIZUKU) {
                    prefs.setTileErrorState(AppPreferences.TILE_ERROR_CMD);
                }
                prefs.setLastError(result.getCommand(), result.getExitCode(), result.getStdout(),
                        result.getStderr(), result.getExceptionMessage());
            }
            new NetworkModeReader(context, prefs, resolver).refreshCache();
        }
    }

    private static boolean supportsMode(AppPreferences prefs, int slot, NetworkMode mode) {
        AppPreferences.NetworkCapabilities caps = prefs.getSlotCapabilities(slot);
        if (caps == null) caps = prefs.getDeviceCapabilities();
        if (caps == null) return true;
        switch (mode) {
            case FIVE_G_ONLY: case PREFERRED_5G: return caps.supports5g;
            case FOUR_G_ONLY: case PREFERRED_4G: return caps.supports4g;
            case PREFERRED_3G: return caps.supports3g;
            case TWO_G_ONLY: return caps.supports2g;
            default: return false;
        }
    }
}
