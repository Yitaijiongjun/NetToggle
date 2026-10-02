package com.dhangofa.networktoggle.telephony;

import com.dhangofa.networktoggle.model.TargetSim;
import org.junit.Test;
import static org.junit.Assert.*;

public class SimTargetSelectionTest {
    @Test public void everySingleSelectorSurvivesTheActionPlan() {
        for (TargetSim target : new TargetSim[] {TargetSim.AUTO, TargetSim.SIM_1, TargetSim.SIM_2}) {
            assertSame(target, NetworkActionExecutor.targetForStep(target, 0));
        }
    }

    @Test public void bothExpandsInPhysicalSlotOrder() {
        assertSame(TargetSim.SIM_1, NetworkActionExecutor.targetForStep(TargetSim.BOTH, 0));
        assertSame(TargetSim.SIM_2, NetworkActionExecutor.targetForStep(TargetSim.BOTH, 1));
    }

    @Test(expected = IllegalArgumentException.class) public void nullCannotBecomeAnUnavailableSim() {
        NetworkActionExecutor.targetForStep(null, 0);
    }

    @Test(expected = IndexOutOfBoundsException.class) public void singleDoesNotVisitAnExtraNullSlot() {
        NetworkActionExecutor.targetForStep(TargetSim.AUTO, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class) public void bothCannotVisitAThirdSlot() {
        NetworkActionExecutor.targetForStep(TargetSim.BOTH, 2);
    }
}
