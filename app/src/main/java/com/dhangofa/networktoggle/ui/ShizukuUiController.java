package com.dhangofa.networktoggle.ui;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import com.dhangofa.networktoggle.R;

/** A single Shizuku status/authorization card, without execution-mode navigation. */
public final class ShizukuUiController {
    public interface AuthStateCallback { void onAuthStateChanged(boolean authorized); }
    private final Activity activity;
    private final ExecutionStateController state;
    private final AuthStateCallback callback;
    private TextView status;
    private View authorize;
    private Boolean authorized;

    public ShizukuUiController(Activity activity, ExecutionStateController state, AuthStateCallback callback) {
        this.activity = activity;
        this.state = state;
        this.callback = callback;
    }

    public void bindViews() {
        status = activity.findViewById(R.id.shizukuStatusText);
        authorize = activity.findViewById(R.id.btnSetupAuthorize);
        authorize.setOnClickListener(view -> state.checkShizukuPermission(true));
        state.checkShizukuPermission(false);
    }

    public void setStatus(String text, int colorCode) {
        boolean next = colorCode == 1;
        status.setText(text);
        status.setTextColor(activity.getColor(next ? R.color.status_success_text : R.color.status_warning_text));
        status.setBackgroundResource(next ? R.drawable.shape_status_badge_success : R.drawable.shape_status_badge_warning);
        authorize.setVisibility(next ? View.GONE : View.VISIBLE);
        if (authorized == null || authorized != next) {
            authorized = next;
            callback.onAuthStateChanged(next);
        }
    }

    public boolean isExecutionAuthorized() { return Boolean.TRUE.equals(authorized); }
}
