package com.dhangofa.networktoggle.ui;

import org.junit.Test;
import static org.junit.Assert.*;

public class TileUpdateGateTest {
    @Test public void repeatedReadbacksPublishAnUnchangedTileOnlyOnce() {
        TileUpdateGate gate = new TileUpdateGate();
        gate.startListening();
        assertTrue(gate.accept("P4G:active:sim1"));
        for (int i = 0; i < 100; i++) assertFalse(gate.accept("P4G:active:sim1"));
        assertTrue(gate.accept("P5G:active:sim1"));
        assertFalse(gate.accept("P5G:active:sim1"));
    }

    @Test public void collapseDoesNotPublishCompletionEventsAndReopenUsesLatestCache() {
        TileUpdateGate gate = new TileUpdateGate();
        gate.startListening();
        assertTrue(gate.accept("P4G"));
        gate.beginCollapse();
        assertFalse(gate.accept("P5G"));
        gate.stopListening();
        assertFalse(gate.accept("P5G"));
        gate.startListening();
        assertTrue(gate.accept("P5G"));
        assertFalse(gate.accept("P5G"));
    }

    @Test public void rejectedActivityLaunchDoesNotFreezeTileUpdates() {
        TileUpdateGate gate = new TileUpdateGate();
        gate.startListening();
        assertTrue(gate.accept("P4G"));
        gate.beginCollapse();
        gate.cancelCollapse();
        assertTrue(gate.accept("P5G"));
    }

    @Test public void aNewListeningSessionPublishesEvenWhenCacheDidNotChange() {
        TileUpdateGate gate = new TileUpdateGate();
        assertFalse(gate.accept("P4G"));
        gate.startListening();
        assertTrue(gate.accept("P4G"));
        gate.stopListening();
        gate.startListening();
        assertTrue(gate.accept("P4G"));
    }
}
