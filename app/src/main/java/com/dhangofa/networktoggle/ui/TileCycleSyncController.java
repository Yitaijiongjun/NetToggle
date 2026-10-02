package com.dhangofa.networktoggle.ui;

import android.content.Context;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.telephony.NetworkModeReader;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.util.AppExecutors;
import java.util.ArrayList;
import java.util.List;

/** Cycle changes use the same verified apply and cache path as shortcuts and tiles. */
public final class TileCycleSyncController implements TileCycleUiController.OnCycleChangedListener {
    private final Context context;
    private final AppPreferences prefs;
    private final SimResolver simResolver;

    public TileCycleSyncController(Context context, AppPreferences prefs, SimResolver simResolver) {
        this.context = context.getApplicationContext();
        this.prefs = prefs;
        this.simResolver = simResolver;
    }

    @Override public void onCycleChanged(List<NetworkMode> newCycle) {
        List<NetworkMode> cycle = new ArrayList<>(newCycle);
        TargetSim target = prefs.getTargetSim();
        AppExecutors.executeTelephony(() -> {
            if (cycle.isEmpty() || prefs.getExecutionMode() == ExecutionMode.NONE || prefs.getTargetSim() != target) return;
            NetworkMode mode = new NetworkModeReader(context, prefs, simResolver).refreshCache();
            if (mode != NetworkMode.UNKNOWN && !cycle.contains(mode)) {
                com.dhangofa.networktoggle.telephony.NetworkActionExecutor.apply(context, cycle.get(0), target, false, "TileCycle Sync");
            }
        });
    }
}
