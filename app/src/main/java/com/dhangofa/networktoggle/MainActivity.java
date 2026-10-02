package com.dhangofa.networktoggle;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.service.quicksettings.TileService;
import android.widget.ImageView;

import com.dhangofa.networktoggle.NetworkTileService;
import com.dhangofa.networktoggle.R;
import com.dhangofa.networktoggle.config.AppPreferences;
import com.dhangofa.networktoggle.cycle.TileCycleManager;
import com.dhangofa.networktoggle.model.NetworkMode;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.TargetSim;
import com.dhangofa.networktoggle.telephony.NetworkCapabilityResolver;
import com.dhangofa.networktoggle.telephony.NetworkModeReader;
import com.dhangofa.networktoggle.telephony.SimResolver;
import com.dhangofa.networktoggle.ui.ExecutionStateController;
import com.dhangofa.networktoggle.ui.PhoneStatePermissionManager;
import com.dhangofa.networktoggle.ui.ShortcutTabHelper;
import com.dhangofa.networktoggle.ui.TargetSimUiController;
import com.dhangofa.networktoggle.ui.TileCycleSyncController;
import com.dhangofa.networktoggle.ui.TileCycleUiController;
import com.dhangofa.networktoggle.util.AppExecutors;

import java.util.List;

public class MainActivity extends Activity implements SharedPreferences.OnSharedPreferenceChangeListener {
    private com.dhangofa.networktoggle.ui.DiagnosticUiController diagnosticUiController;
    private com.dhangofa.networktoggle.ui.ShizukuUiController executionModeUiController;

    private com.dhangofa.networktoggle.ui.ThemeController themeController;
    private PhoneStatePermissionManager permissionManager;
    private NetworkCapabilityResolver capabilityResolver;
    private static final int REQ_CODE_PHONE_STATE = 1001;
    private AppPreferences appPreferences;
    private TileCycleUiController tileCycleUiController;
    private volatile boolean activityDestroyed;
    private ExecutionStateController executionStateController;
    private TargetSimUiController targetSimUiController;
    private ShortcutTabHelper shortcutTabHelper;
    private SimResolver simResolver;
    private NetworkModeReader modeReader;
    private TargetSim lastTargetSim = TargetSim.AUTO;

    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("AppPrefs", 0);
        int mode = prefs.getInt("app_theme", 1);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        if (mode == 1) {
            config.uiMode = config.uiMode & 0xFFFFFFCF | 0x10;
        } else if (mode == 2 || mode == 3) {
            config.uiMode = config.uiMode & 0xFFFFFFCF | 0x20;
        }
        super.attachBaseContext(newBase.createConfigurationContext(config));
    }
    public boolean isAmoled() {
        return this.themeController != null && this.themeController.isAmoled();
    }

    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = this.getSharedPreferences("AppPrefs", 0);
        this.themeController = new com.dhangofa.networktoggle.ui.ThemeController(this, prefs);
        super.onCreate(savedInstanceState);
        this.activityDestroyed = false;
        this.themeController.configureStatusBar();
        this.setContentView(R.layout.activity_main);
        ImageView btnThemeToggle = this.findViewById(R.id.btnThemeToggle);
        this.themeController.bindViews(btnThemeToggle);
        this.appPreferences = new AppPreferences((Context)this);
        this.lastTargetSim = this.appPreferences.getTargetSim();
        this.appPreferences.registerListener(this);

        this.diagnosticUiController = new com.dhangofa.networktoggle.ui.DiagnosticUiController(this, this.appPreferences);
        this.diagnosticUiController.bindViews();

        this.simResolver = new SimResolver((Context)this, this.appPreferences);
        this.capabilityResolver = new NetworkCapabilityResolver(this.appPreferences, this.simResolver);
        TileCycleManager tileCycleManager = new TileCycleManager(this.appPreferences);
        this.tileCycleUiController = new TileCycleUiController(this, tileCycleManager, this.appPreferences);
        this.modeReader = new NetworkModeReader((Context)this, this.appPreferences, this.simResolver);
        this.permissionManager = new PhoneStatePermissionManager(this, REQ_CODE_PHONE_STATE, this.appPreferences, this::updateCapabilities);
        
        this.executionStateController = new ExecutionStateController(this, this.appPreferences, (text, color) -> {
            if (this.executionModeUiController != null) {
                this.executionModeUiController.setStatus(text, color);
            }
        });
        this.executionModeUiController = new com.dhangofa.networktoggle.ui.ShizukuUiController(this, this.executionStateController, this::updateAuthorizationUI);
        this.executionModeUiController.bindViews();

        TileCycleSyncController tileCycleSyncController = new TileCycleSyncController((Context)this, this.appPreferences, this.simResolver);
        this.tileCycleUiController.setOnCycleChangedListener(tileCycleSyncController);
        this.tileCycleUiController.initialize();
        this.executionStateController.registerListeners();
        this.targetSimUiController = new TargetSimUiController(this, this.appPreferences, this::onTargetSimSelectionChanged);
        this.targetSimUiController.initialize();
        this.shortcutTabHelper = ShortcutTabHelper.setupTab(this, this.appPreferences);
        this.updateAuthorizationUI(isExecutionAuthorized());
    }

    protected void onResume() {
        super.onResume();
        if (this.targetSimUiController != null) {
            this.targetSimUiController.updateAutoSimWarning();
        }
        if (this.permissionManager != null) {
            this.permissionManager.checkAndRequest();
        }
        this.executionStateController.checkShizukuPermission(false);
        this.updateAuthorizationUI(isExecutionAuthorized());
        if (this.shortcutTabHelper != null) {
            this.shortcutTabHelper.refreshCapabilities();
        }
        if (this.diagnosticUiController != null) this.diagnosticUiController.updateErrorBanner();
    }

    protected void onPause() {
        super.onPause();
    }

    private void onTargetSimSelectionChanged() {
        TargetSim newTarget = this.appPreferences.getTargetSim();
        if (this.lastTargetSim != TargetSim.BOTH && newTarget == TargetSim.BOTH) {
            AppExecutors.executeTelephony(() -> {
                NetworkMode mode1 = this.modeReader.readCurrentMode(TargetSim.SIM_1);
                NetworkMode mode2 = this.modeReader.readCurrentMode(TargetSim.SIM_2);
                if (mode1 != NetworkMode.UNKNOWN && mode2 != NetworkMode.UNKNOWN && mode1 != mode2) {
                    TileCycleManager tileCycleManager = new TileCycleManager(this.appPreferences);
                    AppPreferences.NetworkCapabilities combinedCaps = this.capabilityResolver.getCapabilities(
                            this.appPreferences.getExecutionMode(),
                            TargetSim.BOTH
                    );
                    tileCycleManager.forceRemoveUnsupportedAndAutoFill(combinedCaps);
                    List<NetworkMode> cycle = tileCycleManager.getCycle();
                    NetworkMode modeToApply = cycle.contains((Object)mode1) ? mode1 : (cycle.contains((Object)mode2) ? mode2 : cycle.get(0));

                    com.dhangofa.networktoggle.telephony.NetworkActionExecutor.apply(getApplicationContext(), modeToApply, TargetSim.BOTH, false, "TargetSimSync");
                }
                this.updateCapabilities();
            });
        } else {
            this.updateCapabilities();
        }
        this.lastTargetSim = newTarget;
    }

    private void updateCapabilities() {
        AppExecutors.executeTelephony(() -> {
            AppPreferences.NetworkCapabilities caps = this.capabilityResolver.getCapabilities(this.appPreferences.getExecutionMode());
            this.runOnUiThread(() -> {
                if (!this.activityDestroyed && this.tileCycleUiController != null) {
                    this.tileCycleUiController.applyCapabilities(caps);
                }
                if (!this.activityDestroyed && this.shortcutTabHelper != null) {
                    this.shortcutTabHelper.refreshCapabilities();
                }
                TileService.requestListeningState((Context)this, (ComponentName)new ComponentName((Context)this, NetworkTileService.class));
            });
        });
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (this.permissionManager != null) {
            this.permissionManager.handleRequestPermissionsResult(requestCode, grantResults);
        }
    }

    protected void onDestroy() {
        this.activityDestroyed = true;
        if (this.appPreferences != null) {
            this.appPreferences.unregisterListener(this);
        }
        if (this.executionStateController != null) {
            this.executionStateController.destroy();
        }
        if (this.permissionManager != null) {
            this.permissionManager.destroy();
        }
        if (this.shortcutTabHelper != null) {
            this.shortcutTabHelper.destroy();
        }
        if (this.diagnosticUiController != null) {
            this.diagnosticUiController.destroy();
        }
        super.onDestroy();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ("last_error_cmd".equals(key)) {
            this.runOnUiThread(() -> { if (this.diagnosticUiController != null) this.diagnosticUiController.updateErrorBanner(); });
        }
    }

    private void updateAuthorizationUI(boolean authorized) {
        if (this.tileCycleUiController != null) {
            this.tileCycleUiController.setAuthorized(authorized);
        }
        if (this.targetSimUiController != null) {
            this.targetSimUiController.setAuthorized(authorized);
        }
        if (this.shortcutTabHelper != null) {
            this.shortcutTabHelper.setAuthorized(authorized);
        }
        if (authorized && this.appPreferences != null) {
            if (this.appPreferences.getDeviceCapabilities() == null) {
                this.updateCapabilities();
            }
        }
    }

    public boolean isExecutionAuthorized() {
        return this.executionModeUiController != null && this.executionModeUiController.isExecutionAuthorized();
    }

}
