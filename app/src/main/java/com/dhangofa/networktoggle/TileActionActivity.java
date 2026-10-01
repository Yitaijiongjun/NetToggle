package com.dhangofa.networktoggle;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.service.quicksettings.TileService;

import com.dhangofa.networktoggle.automation.AutomationExecutor;
import com.dhangofa.networktoggle.automation.AutomationRequest;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.cycle.TileCycleManager;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.telephony.NetworkModeReader;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.util.AppExecutors;

/**
 * FIClash-style transparent action trampoline for QS tile taps.
 *
 * TileService.startActivityAndCollapse() launches this activity; this activity
 * starts the actual network operation on the app's background executor and then
 * finishes immediately. The network operation therefore does not depend on the
 * TileService remaining in the listening lifecycle while SystemUI collapses.
 */
public final class TileActionActivity extends Activity {
    public static final String ACTION_TOGGLE =
            "com.dhangofa.networktoggle.action.TOGGLE_TILE";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();
        if (intent != null && ACTION_TOGGLE.equals(intent.getAction())) {
            AppPreferences prefs = new AppPreferences(this);
            if (prefs.getExecutionMode() != ExecutionMode.NONE
                    && NetworkTileService.IS_SWITCHING.compareAndSet(false, true)) {
                TargetSim target = prefs.getTargetSim();

                AppExecutors.executeTelephony(() -> {
                    try {
                        NetworkMode currentMode = new NetworkModeReader(getApplicationContext(), prefs,
                                new SimResolver(getApplicationContext(), prefs)).readCurrentMode(target);
                        NetworkMode nextMode = new TileCycleManager(prefs).getNextMode(currentMode);
                        AutomationRequest request = new AutomationRequest(nextMode, target, false, "QS Tile", true);
                        AutomationExecutor.execute(getApplicationContext(), request);
                    } catch (Throwable error) {
                        prefs.setLastError("QS Tile", -1, "", "", error.toString());
                        prefs.setTileErrorState(AppPreferences.TILE_ERROR_CMD);
                    } finally {
                        NetworkTileService.IS_SWITCHING.set(false);
                        TileService.requestListeningState(getApplicationContext(),
                                new ComponentName(getApplicationContext(), NetworkTileService.class));
                    }
                });
            } else {
                TileService.requestListeningState(
                        this,
                        new ComponentName(this, NetworkTileService.class));
            }
        }

        finish();
    }
}
