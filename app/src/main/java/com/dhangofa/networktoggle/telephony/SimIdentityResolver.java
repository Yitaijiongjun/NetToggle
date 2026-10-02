package com.dhangofa.networktoggle.telephony;

import android.os.Build;
import android.telephony.SubscriptionManager;
import com.dhangofa.networktoggle.model.ExecutionMode;
import com.dhangofa.networktoggle.model.TargetSim;

/** Fast identity-only lookup; carrier metadata remains in the unchanged SimResolver. */
final class SimIdentityResolver {
    private SimIdentityResolver() {}

    static SimResolver.SimInfo resolve(SimResolver resolver, ExecutionMode mode, TargetSim target) {
        if (target == TargetSim.BOTH || target == null) return null;
        try {
            int sub = -1;
            int slot = target.getManualSlotIndex();
            if (target == TargetSim.AUTO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                sub = SubscriptionManager.getDefaultDataSubscriptionId();
                slot = SubscriptionManager.getSlotIndex(sub);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                sub = SubscriptionManager.getSubscriptionId(slot);
            } else {
                return resolver.resolveTargetSimInfo(mode, target);
            }
            return sub >= 0 && resolver.isValidSlotIndex(slot) ? new SimResolver.SimInfo(sub, slot, "") : null;
        } catch (RuntimeException ignored) {
            // Preserve the established fallback for older or unusual ROMs.
        }
        return resolver.resolveTargetSimInfo(mode, target);
    }

    static boolean stillMatches(SimResolver resolver, SimResolver.SimInfo info) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                return SubscriptionManager.getSubscriptionId(info.slotIndex) == info.subId;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return SubscriptionManager.getSlotIndex(info.subId) == info.slotIndex;
            }
            TargetSim target = info.slotIndex == 0 ? TargetSim.SIM_1 : TargetSim.SIM_2;
            return resolver.resolveTargetSubId(ExecutionMode.SHIZUKU, target) == info.subId;
        } catch (RuntimeException ignored) { return false; }
    }
}
