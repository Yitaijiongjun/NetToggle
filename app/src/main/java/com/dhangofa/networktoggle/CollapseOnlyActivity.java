package com.dhangofa.networktoggle;

import android.app.Activity;
import android.os.Bundle;

/**
 * Zero-UI trampoline used only with TileService.startActivityAndCollapse().
 *
 * Theme.NoDisplay prevents this activity from creating a visible window or
 * changing status-bar appearance while SystemUI collapses Quick Settings.
 */
public final class CollapseOnlyActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        finish();
    }
}
