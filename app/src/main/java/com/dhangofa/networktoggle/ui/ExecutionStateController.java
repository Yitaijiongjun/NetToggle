package com.dhangofa.networktoggle.ui;

/** Shizuku Binder lifecycle and authorization state. */

import android.app.Activity;
import android.content.pm.PackageManager;

import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.model.ExecutionMode;

import rikka.shizuku.Shizuku;

public class ExecutionStateController {
    private final Activity activity;
    private final AppPreferences appPreferences;
    private final StatusCallback statusCallback;

    private boolean isDestroyed = false;

    public interface StatusCallback {
        void onStatusUpdate(String text, int color);
    }

    private Shizuku.OnBinderReceivedListener binderReceivedListener;
    private Shizuku.OnBinderDeadListener binderDeadListener;
    private Shizuku.OnRequestPermissionResultListener permissionResultListener;

    public ExecutionStateController(Activity activity, AppPreferences appPreferences, StatusCallback statusCallback) {
        this.activity = activity;
        this.appPreferences = appPreferences;
        this.statusCallback = statusCallback;

        binderReceivedListener = () ->
            activity.runOnUiThread(() -> {
                if (!isDestroyed && appPreferences != null
                        && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                    checkShizukuPermission(false);
                }
            });

        binderDeadListener = () ->
            activity.runOnUiThread(() -> {
                if (!isDestroyed && appPreferences != null
                        && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                    statusCallback.onStatusUpdate(activity.getString(R.string.status_shizuku_not_running), 2);
                }
            });

        permissionResultListener =
            (requestCode, grantResult) -> activity.runOnUiThread(() -> {
                if (!isDestroyed && appPreferences != null
                        && appPreferences.getExecutionMode() == ExecutionMode.SHIZUKU) {
                    checkShizukuPermission(false);
                }
            });
    }

    public void registerListeners() {
        Shizuku.addBinderReceivedListener(binderReceivedListener);
        Shizuku.addBinderDeadListener(binderDeadListener);
        Shizuku.addRequestPermissionResultListener(permissionResultListener);
    }

    public void unregisterListeners() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener);
        Shizuku.removeBinderDeadListener(binderDeadListener);
        Shizuku.removeRequestPermissionResultListener(permissionResultListener);
    }

    public void destroy() {
        isDestroyed = true;
        unregisterListeners();
    }

    public void checkShizukuPermission(boolean requestIfNeeded) {
        if (isDestroyed) return;
        try {
            if (!Shizuku.pingBinder()) {
                statusCallback.onStatusUpdate(activity.getString(R.string.status_shizuku_not_running), 2);
                if (appPreferences != null) {
                    appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
                }
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                statusCallback.onStatusUpdate(activity.getString(R.string.status_shizuku_authorized), 1);
                if (appPreferences != null) {
                    if (appPreferences.getTileErrorState() == AppPreferences.TILE_ERROR_SHIZUKU) {
                        appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_NONE);
                    }
                }
                return;
            }
            statusCallback.onStatusUpdate(activity.getString(R.string.status_shizuku_not_granted), 2);
            if (appPreferences != null) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
            }
            if (requestIfNeeded) {
                statusCallback.onStatusUpdate(activity.getString(R.string.status_shizuku_requesting), 3);
                Shizuku.requestPermission(0);
            }
        } catch (Exception e) {
            statusCallback.onStatusUpdate(activity.getString(R.string.status_shizuku_check_failed), 2);
            if (appPreferences != null) {
                appPreferences.setTileErrorState(AppPreferences.TILE_ERROR_SHIZUKU);
            }
        }
    }
}
