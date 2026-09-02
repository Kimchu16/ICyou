package com.matissjurevics.icyou.overhaul;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CameraOverhaulContractsTest {

    @Test
    void defaultLimitsAreInternallyConsistent() {
        assertDoesNotThrow(CameraOverhaulContracts::validateLimits);
    }

    @Test
    void lifecycleAllowsDemandAndGracePaths() {
        assertTrue(FeedLifecycleState.INACTIVE.canTransitionTo(FeedLifecycleState.ACTIVATING));
        assertTrue(FeedLifecycleState.ACTIVATING.canTransitionTo(FeedLifecycleState.AVAILABLE));
        assertTrue(FeedLifecycleState.AVAILABLE.canTransitionTo(FeedLifecycleState.UNAVAILABLE));
        assertTrue(FeedLifecycleState.UNAVAILABLE.canTransitionTo(FeedLifecycleState.RETAINING));
        assertTrue(FeedLifecycleState.RETAINING.canTransitionTo(FeedLifecycleState.INACTIVE));
        assertTrue(FeedLifecycleState.RETAINING.canTransitionTo(FeedLifecycleState.ACTIVATING));
    }

    @Test
    void lifecycleRejectsBypassingActivationAndGrace() {
        assertFalse(FeedLifecycleState.INACTIVE.canTransitionTo(FeedLifecycleState.AVAILABLE));
        assertFalse(FeedLifecycleState.AVAILABLE.canTransitionTo(FeedLifecycleState.INACTIVE));
        assertFalse(FeedLifecycleState.UNAVAILABLE.canTransitionTo(FeedLifecycleState.INACTIVE));
    }
}
