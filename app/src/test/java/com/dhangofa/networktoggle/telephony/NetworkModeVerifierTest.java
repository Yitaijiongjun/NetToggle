package com.dhangofa.networktoggle.telephony;

import com.dhangofa.networktoggle.model.CommandResult;
import com.dhangofa.networktoggle.model.NetworkMode;
import org.junit.Test;
import static org.junit.Assert.*;

public class NetworkModeVerifierTest {
    private static final CommandResult DISPATCH = CommandResult.completed("setter", 0, "sent", "");
    private NetworkModeReadback state(NetworkMode user, NetworkMode effective, Boolean vendor) {
        return new NetworkModeReadback(user, effective, vendor, true);
    }

    @Test public void vendorFlagCannotHideOldFourGMask() {
        NetworkModeReadback stale = state(NetworkMode.PREFERRED_4G, NetworkMode.PREFERRED_4G, true);
        assertFalse(NetworkModeVerifier.verify(NetworkMode.PREFERRED_5G, DISPATCH, () -> stale, 4, () -> {}).isSuccess());
        assertEquals(NetworkMode.PREFERRED_4G, stale.displayMode());
    }

    @Test public void powerOrCarrierRestrictionCannotReportFiveGSuccess() {
        NetworkModeReadback blocked = state(NetworkMode.PREFERRED_5G, NetworkMode.PREFERRED_4G, true);
        assertFalse(blocked.matches(NetworkMode.PREFERRED_5G));
        assertEquals(NetworkMode.PREFERRED_4G, blocked.displayMode());
    }

    @Test public void missingVendorReadbackFailsClosed() {
        assertFalse(state(NetworkMode.PREFERRED_5G, NetworkMode.PREFERRED_5G, null).matches(NetworkMode.PREFERRED_5G));
        assertEquals(NetworkMode.UNKNOWN, state(NetworkMode.PREFERRED_5G, NetworkMode.PREFERRED_5G, null).displayMode());
    }

    @Test public void missingUserReadbackCannotDisplayEffectiveStateAsConfirmed() {
        assertEquals(NetworkMode.UNKNOWN, state(NetworkMode.UNKNOWN, NetworkMode.PREFERRED_5G, true).displayMode());
    }

    @Test public void transientReadbackMustSettleTwice() {
        NetworkModeReadback okay = state(NetworkMode.PREFERRED_5G, NetworkMode.PREFERRED_5G, true);
        NetworkModeReadback wrong = state(NetworkMode.PREFERRED_4G, NetworkMode.PREFERRED_4G, false);
        NetworkModeReadback[] sequence = {okay, wrong, okay, okay};
        int[] index = {0};
        assertTrue(NetworkModeVerifier.verify(NetworkMode.PREFERRED_5G, DISPATCH,
                () -> sequence[index[0]++], sequence.length, () -> {}).isSuccess());
        assertEquals(4, index[0]);
    }

    @Test public void lastSingleMatchingReadCannotReportSuccess() {
        NetworkModeReadback okay = state(NetworkMode.PREFERRED_5G, NetworkMode.PREFERRED_5G, true);
        assertFalse(NetworkModeVerifier.verify(NetworkMode.PREFERRED_5G, DISPATCH, () -> okay, 1, () -> {}).isSuccess());
    }

    @Test public void onlyModesAlsoRequireVendorAgreement() {
        assertFalse(state(NetworkMode.FIVE_G_ONLY, NetworkMode.FIVE_G_ONLY, false).matches(NetworkMode.FIVE_G_ONLY));
        assertFalse(state(NetworkMode.FOUR_G_ONLY, NetworkMode.FOUR_G_ONLY, true).matches(NetworkMode.FOUR_G_ONLY));
        assertTrue(state(NetworkMode.FOUR_G_ONLY, NetworkMode.FOUR_G_ONLY, false).matches(NetworkMode.FOUR_G_ONLY));
    }

    @Test public void genericDevicesDoNotRequireXiaomiFlag() {
        assertTrue(new NetworkModeReadback(NetworkMode.PREFERRED_5G, NetworkMode.PREFERRED_5G, null, false)
                .matches(NetworkMode.PREFERRED_5G));
    }

    @Test public void lowerGenerationRestrictionsAreDisplayed() {
        assertEquals(NetworkMode.PREFERRED_3G,
                state(NetworkMode.PREFERRED_5G, NetworkMode.PREFERRED_3G, true).displayMode());
        assertFalse(state(NetworkMode.FIVE_G_ONLY, NetworkMode.PREFERRED_5G, true).matches(NetworkMode.FIVE_G_ONLY));
    }

    @Test public void decodeMasksHandlesCarrierSubsetsAndLteCa() {
        assertEquals(NetworkMode.UNKNOWN, NetworkModeReadback.fromAllowedMask(0));
        assertEquals(NetworkMode.UNKNOWN, NetworkModeReadback.fromAllowedMask(-1));
        assertEquals(NetworkMode.TWO_G_ONLY, NetworkModeReadback.fromAllowedMask(3));
        assertEquals(NetworkMode.PREFERRED_4G, NetworkModeReadback.fromAllowedMask((1L << 12) | (1L << 2)));
        assertEquals(NetworkMode.FOUR_G_ONLY, NetworkModeReadback.fromAllowedMask((1L << 12) | (1L << 18)));
        assertEquals(NetworkMode.FIVE_G_ONLY, NetworkModeReadback.fromAllowedMask(1L << 19));
        assertEquals(NetworkMode.PREFERRED_5G, NetworkModeReadback.fromAllowedMask((1L << 19) | (1L << 2)));
        for (NetworkMode mode : NetworkMode.values()) {
            if (mode.getBinaryMask() != null) assertEquals(mode, NetworkModeReadback.fromAllowedMask(Long.parseLong(mode.getBinaryMask(), 2)));
        }
    }

    @Test public void interruptionCannotReportSuccess() {
        assertFalse(NetworkModeVerifier.verify(NetworkMode.PREFERRED_5G, DISPATCH, () -> null, 4,
                () -> {throw new InterruptedException();}).isSuccess());
        assertTrue(Thread.interrupted());
    }
}
