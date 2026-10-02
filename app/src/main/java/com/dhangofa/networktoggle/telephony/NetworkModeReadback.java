package com.dhangofa.networktoggle.telephony;

import com.dhangofa.networktoggle.model.NetworkMode;

/** Configuration and effective restrictions are separate from the currently attached radio RAT. */
final class NetworkModeReadback {
    final NetworkMode userMode;
    final NetworkMode effectiveMode;
    final Boolean vendorEnabled;
    final boolean vendorRequired;

    NetworkModeReadback(NetworkMode userMode, NetworkMode effectiveMode,
            Boolean vendorEnabled, boolean vendorRequired) {
        this.userMode = userMode;
        this.effectiveMode = effectiveMode;
        this.vendorEnabled = vendorEnabled;
        this.vendorRequired = vendorRequired;
    }

    boolean matches(NetworkMode requested) {
        if (requested == NetworkMode.UNKNOWN || userMode != requested || effectiveMode == NetworkMode.UNKNOWN) return false;
        boolean nr = requested == NetworkMode.PREFERRED_5G || requested == NetworkMode.FIVE_G_ONLY;
        if (vendorRequired && (vendorEnabled == null || vendorEnabled != nr)) return false;
        if (requested == NetworkMode.FIVE_G_ONLY) return effectiveMode == NetworkMode.FIVE_G_ONLY;
        if (nr) return effectiveMode == NetworkMode.PREFERRED_5G || effectiveMode == NetworkMode.FIVE_G_ONLY;
        if (requested == NetworkMode.PREFERRED_4G) return effectiveMode == NetworkMode.PREFERRED_4G || effectiveMode == NetworkMode.FOUR_G_ONLY;
        if (requested == NetworkMode.PREFERRED_3G) return effectiveMode == NetworkMode.PREFERRED_3G || effectiveMode == NetworkMode.TWO_G_ONLY;
        return effectiveMode == requested;
    }

    NetworkMode displayMode() {
        if (userMode == NetworkMode.UNKNOWN) return NetworkMode.UNKNOWN;
        if (vendorRequired && vendorEnabled == null) return NetworkMode.UNKNOWN;
        if (effectiveMode == NetworkMode.UNKNOWN) return NetworkMode.UNKNOWN;
        if (userMode == NetworkMode.PREFERRED_5G && Boolean.FALSE.equals(vendorEnabled)) {
            return effectiveMode == NetworkMode.PREFERRED_3G || effectiveMode == NetworkMode.TWO_G_ONLY
                    ? effectiveMode : NetworkMode.PREFERRED_4G;
        }
        if (userMode == NetworkMode.FIVE_G_ONLY && Boolean.FALSE.equals(vendorEnabled)) return NetworkMode.UNKNOWN;
        if ((userMode == NetworkMode.PREFERRED_5G || userMode == NetworkMode.PREFERRED_4G)
                && effectiveMode == NetworkMode.FOUR_G_ONLY) return NetworkMode.PREFERRED_4G;
        return effectiveMode == userMode ? userMode : effectiveMode;
    }

    public static NetworkMode fromAllowedMask(long mask) {
        if (mask <= 0) return NetworkMode.UNKNOWN;
        long nr = 1L << 19;
        long lte = (1L << 12) | (1L << 18); // LTE and LTE_CA
        long gsm = (1L << 0) | (1L << 1) | (1L << 15); // GPRS, EDGE, GSM
        if ((mask & nr) != 0) return (mask & ~nr) == 0 ? NetworkMode.FIVE_G_ONLY : NetworkMode.PREFERRED_5G;
        if ((mask & lte) != 0) return (mask & ~lte) == 0 ? NetworkMode.FOUR_G_ONLY : NetworkMode.PREFERRED_4G;
        return (mask & ~gsm) == 0 ? NetworkMode.TWO_G_ONLY : NetworkMode.PREFERRED_3G;
    }

    @Override public String toString() {
        return "USER=" + userMode + ", effective=" + effectiveMode + ", Xiaomi5G=" + vendorEnabled;
    }
}
