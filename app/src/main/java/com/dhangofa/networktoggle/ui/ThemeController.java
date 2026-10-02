package com.dhangofa.networktoggle.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

import com.dhangofa.networktoggle.R;

public class ThemeController {
    private final Activity activity;
    private final SharedPreferences prefs;
    private int currentThemeMode = -1;
    private ImageView btnThemeToggle;

    public ThemeController(Activity activity, SharedPreferences prefs) {
        this.activity = activity;
        this.prefs = prefs;
        this.currentThemeMode = prefs.getInt("app_theme", 1);
        activity.setTheme(R.style.Theme_NetToggle);
    }

    public int getCurrentThemeMode() {
        return currentThemeMode;
    }

    public boolean isAmoled() {
        return currentThemeMode == 3;
    }

    public void bindViews(ImageView btnThemeToggle) {
        this.btnThemeToggle = btnThemeToggle;
        if (this.btnThemeToggle != null) {
            this.btnThemeToggle.setOnClickListener(v -> cycleThemeMode());
        }
        updateThemeIcon();
        applyThemeOverrides();
    }

    private void cycleThemeMode() {
        currentThemeMode = (currentThemeMode + 1) % 4;
        prefs.edit().putInt("app_theme", currentThemeMode).apply();
        activity.recreate();
    }

    private void updateThemeIcon() {
        if (btnThemeToggle == null) return;
        if (currentThemeMode == 0) {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_auto);
        } else if (currentThemeMode == 1) {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_light);
        } else if (currentThemeMode == 2) {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_dark);
        } else {
            btnThemeToggle.setImageResource(R.drawable.ic_theme_amoled);
        }
    }

    
    public void configureStatusBar() {
        int flags;
        if (android.os.Build.VERSION.SDK_INT < 23) {
            return;
        }
        boolean isNight = (activity.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        Window window = activity.getWindow();
        if (this.currentThemeMode == 3) {
            window.setStatusBarColor(-16777216);
            window.setNavigationBarColor(-16777216);
        } else {
            window.setStatusBarColor(activity.getColor(R.color.surface_background));
            window.setNavigationBarColor(activity.getColor(R.color.surface_background));
        }
        int n = flags = isNight ? 0 : 8192; // SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        if (!isNight && android.os.Build.VERSION.SDK_INT >= 26) {
            flags |= 16; // SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    public void applyThemeOverrides() {
        if (currentThemeMode == 3) {
            int black = -16777216;
            View root = activity.findViewById(R.id.mainRoot);
            if (root != null) root.setBackgroundColor(black);
            
            Window window = activity.getWindow();
            if (window != null) {
                window.setStatusBarColor(black);
                window.setNavigationBarColor(black);
            }
            
        }
    }
}
