package com.dhangofa.networktoggle;

/**
 * Quick Settings (QS) Tile Service.
 * This handles the actual toggle button that sits in the Android notification shade.
 * When tapped, it reads the current network mode, figures out the next mode based on the configured cycle,
 * and executes the change using the Shizuku backend.
 * It also dynamically draws the tile icon to reflect the currently active mode.
 */
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.provider.Settings;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import rikka.shizuku.Shizuku;
import android.content.pm.PackageManager;

import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.telephony.NetworkModeReader;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.cycle.TileCycleManager;
import com.dhangofa.networktoggle.util.AppExecutors;

import java.util.concurrent.atomic.AtomicBoolean;

import com.dhangofa.networktoggle.ui.TileIconManager;
import com.dhangofa.networktoggle.ui.TileUpdateGate;

public class NetworkTileService extends TileService {
    static final AtomicBoolean IS_SWITCHING =
            new AtomicBoolean(false);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private AppPreferences appPreferences;
    private final TileUpdateGate tileUpdateGate = new TileUpdateGate();
    private String tileIconKey;

    private boolean listening;
    private final AtomicBoolean refreshQueued = new AtomicBoolean(false);
    private final Runnable refreshRunnable = this::requestStateRefresh;
    private final Runnable updateCachedTileRunnable = () -> {
        if (listening) updateTileUI(appPreferences.getCachedNetworkMode());
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener cacheListener = (prefs, key) -> {
        if (listening && ("net_state".equals(key) || "tile_error_state".equals(key)
                || "resolved_auto_slot".equals(key) || "target_sim".equals(key)
                || "tile_active_modes".equals(key) || "tile_cycle_modes".equals(key))) {
            mainHandler.removeCallbacks(updateCachedTileRunnable);
            mainHandler.post(updateCachedTileRunnable);
        }
    };
    private final ContentObserver stateObserver = new ContentObserver(mainHandler) {
        @Override public void onChange(boolean selfChange) {
            mainHandler.removeCallbacks(refreshRunnable);
            mainHandler.postDelayed(refreshRunnable, 200);
        }
    };

    private NetworkModeReader networkModeReader;
    private TileCycleManager tileCycleManager;
    private com.dhangofa.networktoggle.telephony.SimResolver simResolver;

    private final Runnable shizukuGraceCheckRunnable = () -> {
        if (appPreferences != null && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
            boolean isShizukuOk = false;
            try {
                isShizukuOk = Shizuku.pingBinder()
                        && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable t) {
                isShizukuOk = false;
            }

            int currentError = appPreferences.getTileErrorState();
            if (!isShizukuOk && currentError != AppPreferences.TILE_ERROR_SHIZUKU) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                updateTileUI(appPreferences.getCachedNetworkMode());
            } else if (isShizukuOk && currentError == AppPreferences.TILE_ERROR_SHIZUKU) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                updateTileUI(appPreferences.getCachedNetworkMode());
            }
        }
    };

    private final Shizuku.OnBinderReceivedListener binderReceivedListener = () ->
        mainHandler.post(() -> {
            mainHandler.removeCallbacks(shizukuGraceCheckRunnable);
            if (appPreferences != null && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                boolean isShizukuOk = false;
                try {
                    isShizukuOk = Shizuku.pingBinder()
                            && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
                } catch (Throwable ignored) {}

                if (isShizukuOk) {
                    if (appPreferences.getTileErrorState() == AppPreferences.TILE_ERROR_SHIZUKU) {
                        appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                    }
                    updateTileUI(appPreferences.getCachedNetworkMode());
                    if (listening) requestStateRefresh();
                }
            }
        });

    private final Shizuku.OnBinderDeadListener binderDeadListener = () ->
        mainHandler.post(() -> {
            mainHandler.removeCallbacks(shizukuGraceCheckRunnable);
            if (appPreferences != null && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                updateTileUI(appPreferences.getCachedNetworkMode());
            }
        });

    @Override
    public void onCreate() {
        super.onCreate();

        // Init dependencies
        appPreferences = new AppPreferences(this);
        tileCycleManager = new TileCycleManager(appPreferences);
        simResolver = new com.dhangofa.networktoggle.telephony.SimResolver(this, appPreferences);
        networkModeReader = new com.dhangofa.networktoggle.telephony.NetworkModeReader(this, appPreferences, simResolver);

        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener);
            Shizuku.addBinderDeadListener(binderDeadListener);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDestroy() {
        tileUpdateGate.stopListening();
        listening = false;
        super.onDestroy();
        mainHandler.removeCallbacksAndMessages(null);
        appPreferences.unregisterListener(cacheListener);
        getContentResolver().unregisterContentObserver(stateObserver);
        try {
            Shizuku.removeBinderReceivedListener(binderReceivedListener);
            Shizuku.removeBinderDeadListener(binderDeadListener);
        } catch (Throwable ignored) {}
    }

    @Override
    public void onStopListening() {
        tileUpdateGate.stopListening();
        super.onStopListening();
        listening = false;
        appPreferences.unregisterListener(cacheListener);
        mainHandler.removeCallbacks(updateCachedTileRunnable);
        getContentResolver().unregisterContentObserver(stateObserver);
        mainHandler.removeCallbacks(refreshRunnable);
        mainHandler.removeCallbacks(shizukuGraceCheckRunnable);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (!listening) {
            tileUpdateGate.startListening();
            listening = true;
            appPreferences.registerListener(cacheListener);
            for (String key : new String[] {"fiveg_user_enable", "dual_nr_enabled", "preferred_network_mode", "multi_sim_data_call"}) {
                getContentResolver().registerContentObserver(Settings.Global.getUriFor(key), true, stateObserver);
            }
        }

        // Passive Shizuku Check
        if (appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
            boolean isShizukuOk = false;
            try {
                isShizukuOk = Shizuku.pingBinder()
                        && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable t) {
                isShizukuOk = false;
            }

            int currentError = appPreferences.getTileErrorState();
            if (isShizukuOk) {
                if (currentError == AppPreferences.TILE_ERROR_SHIZUKU) {
                    appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                }
            } else {
                // On cold start, Shizuku IPC handshake takes ~20-50ms to bind.
                // Do not prematurely mark tile as dead; give it a 500ms grace check.
                mainHandler.removeCallbacks(shizukuGraceCheckRunnable);
                mainHandler.postDelayed(shizukuGraceCheckRunnable, 500);
            }
        }

        NetworkMode cachedMode = appPreferences.getCachedNetworkMode();
        updateTileUI(cachedMode);

        if (appPreferences.getExecutionMode() == ExecutionMode.NONE) {
            return;
        }

        requestStateRefresh();
    }

    private void requestStateRefresh() {
        if (!listening || appPreferences.getExecutionMode() == ExecutionMode.NONE
                || !refreshQueued.compareAndSet(false, true)) return;
        AppExecutors.executeTelephony(() -> {
            try {
                long lastCheck = appPreferences.getLastNetworkCheckTimestamp();
                NetworkMode realMode = networkModeReader.refreshCache();
                NetworkMode preferredMode = appPreferences.getLastUserSelectedMode();
                // Keep auto-restore bounded even though visible tiles now always refresh.
                if (appPreferences.isAutoRestorePreferredModeEnabled()
                        && realMode != NetworkMode.UNKNOWN && preferredMode != NetworkMode.UNKNOWN
                        && realMode != preferredMode && System.currentTimeMillis() - lastCheck > 30_000L
                        && IS_SWITCHING.compareAndSet(false, true)) {
                    applyModeInternal(preferredMode, appPreferences.getExecutionMode(), true);
                }
            } finally {
                refreshQueued.set(false);
            }
        });
    }

    @Override
    public void onClick() {
        super.onClick();

        ExecutionMode executionMode = appPreferences.getExecutionMode();
        if (executionMode == ExecutionMode.NONE) {
            updateTileUI(NetworkMode.UNKNOWN);
            return;
        }

        // Match FIClash's lifecycle: when auto-collapse is enabled the tile does
        // not perform the action itself. It only launches a transparent action
        // activity through startActivityAndCollapse(); that activity starts the
        // network operation and immediately finishes.
        if (appPreferences.isAutoCollapseQuickSettingsEnabled()) {
            if (IS_SWITCHING.get()) return;
            startTileActionAndCollapse();
            return;
        }

        if (!IS_SWITCHING.compareAndSet(false, true)) {
            return;
        }

        AppExecutors.executeTelephony(() -> {
            try {
                NetworkMode currentMode = networkModeReader.readCurrentMode();
                applyModeInternal(tileCycleManager.getNextMode(currentMode), executionMode, false);
            } finally {
                IS_SWITCHING.set(false);
            }
        });
    }

    private void applyModeInternal(NetworkMode targetMode, ExecutionMode executionMode, boolean isAutoRestore) {
        try {
            com.dhangofa.networktoggle.telephony.NetworkActionExecutor.apply(getApplicationContext(),
                    targetMode, appPreferences.getTargetSim(), !isAutoRestore,
                    isAutoRestore ? "Auto Restore" : "QS Tile");
        } catch (Throwable error) {
            appPreferences.setLastError("QS Tile", -1, "", "", error.toString());
            appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_CMD);
            networkModeReader.refreshCache();
        } finally {
            IS_SWITCHING.set(false);
        }
    }

    private void updateTileUI(NetworkMode mode) {
        Tile tile = getQsTile();

        if (tile == null) {
            return;
        }

        int errorState = appPreferences.getTileErrorState();
        if (errorState == AppPreferences.TILE_ERROR_SHIZUKU) {
            boolean isActuallyOk = false;
            try {
                isActuallyOk = Shizuku.pingBinder()
                        && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
            } catch (Throwable ignored) {}

            if (isActuallyOk) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                errorState = AppPreferences.TILE_ERROR_NONE;
            } else {
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.setLabel(getString(R.string.tile_shizuku_unavailable));
                setTileIcon(tile, "?", "", false);
                updateTileSilently(tile);
                return;
            }
        } else if (errorState == AppPreferences.TILE_ERROR_CMD) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel(getString(R.string.tile_error_check_app));
            setTileIcon(tile, "?", "", false);
            updateTileSilently(tile);
            return;
        }

        if (mode == NetworkMode.UNKNOWN) {
            if (appPreferences.getExecutionMode() == ExecutionMode.NONE) {
                tile.setState(Tile.STATE_UNAVAILABLE);
                tile.setLabel(getString(R.string.tile_setup_required));
            } else {
                NetworkMode firstMode = tileCycleManager.getFirstMode();

                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel(getString(R.string.tile_tap_to_set, firstMode.getTileLabel()));
            }

            setTileIcon(tile, "?", "", false);
        } else {
            tile.setState(appPreferences.isTileModeActive(mode)
                    ? Tile.STATE_ACTIVE
                    : Tile.STATE_INACTIVE);
            tile.setLabel(mode.getTileLabel());

            // Determine badge and auto state
            com.dhangofa.networktoggle.model.TargetSim targetSim = appPreferences.getTargetSim();
            String badge = "";
            boolean isAuto = targetSim == com.dhangofa.networktoggle.model.TargetSim.AUTO;

            if (isAuto) {
                int activeSlot = appPreferences.getResolvedAutoSlot();
                if (simResolver.isValidSlotIndex(activeSlot)) {
                    badge = String.valueOf(activeSlot + 1);
                } else {
                    badge = "";
                }
            } else if (targetSim == com.dhangofa.networktoggle.model.TargetSim.SIM_1) {
                badge = "1";
            } else if (targetSim == com.dhangofa.networktoggle.model.TargetSim.SIM_2) {
                badge = "2";
            } else if (targetSim == com.dhangofa.networktoggle.model.TargetSim.BOTH) { // Future proofing for BOTH
                badge = "+";
            }

            setTileIcon(tile, mode.getIconText(), badge, isAuto);
        }

        updateTileSilently(tile);
    }

    @SuppressWarnings("deprecation")
    private void startTileActionAndCollapse() {
        tileUpdateGate.beginCollapse();
        mainHandler.removeCallbacks(updateCachedTileRunnable);
        try {
            Intent intent = new Intent(this, TileActionActivity.class);
            intent.setAction(TileActionActivity.ACTION_TOGGLE);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PendingIntent pendingIntent = PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                startActivityAndCollapse(pendingIntent);
            } else {
                startActivityAndCollapse(intent);
            }
        } catch (Throwable ignored) {
            tileUpdateGate.cancelCollapse();
            // If SystemUI refuses the activity handoff, leave the tile unchanged.
        }
    }

    private void setTileIcon(Tile tile, String text, String badge, boolean isAuto) {
        tileIconKey = text + "_" + badge + "_" + isAuto;
        tile.setIcon(TileIconManager.getCachedIcon(text, badge, isAuto));
    }

    private void updateTileSilently(Tile tile) {
        String presentation = tile.getState() + "\n" + tile.getLabel() + "\n" + tileIconKey;
        if (!tileUpdateGate.accept(presentation)) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Prevent SystemUI/OEM accessibility feedback such as "PREF 5G, on/off".
            tile.setStateDescription("\u200B");
        }
        CharSequence label = tile.getLabel();
        if (label != null) {
            tile.setContentDescription(label);
        }
        tile.updateTile();
    }
}
