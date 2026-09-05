package com.matissjurevics.icyou.overhaul;

/** Demand-driven lifetime of one logical camera feed. */
public enum FeedLifecycleState {
    /** No demand and no retained resources. */
    INACTIVE,
    /** Demand exists and resources or a render assignment are being acquired. */
    ACTIVATING,
    /** Demand exists and current media is available. */
    AVAILABLE,
    /** Demand exists, but media cannot currently be produced; show a placeholder. */
    UNAVAILABLE,
    /** Demand ended and resources are held during the reassignment/reuse grace period. */
    RETAINING;

    /**
     * Returns whether the control plane may make this transition. Re-entering
     * a state is allowed so event handling can be idempotent.
     */
    public boolean canTransitionTo(FeedLifecycleState next) {
        if (this == next) {
            return true;
        }
        return switch (this) {
            case INACTIVE -> next == ACTIVATING;
            case ACTIVATING -> next == AVAILABLE || next == UNAVAILABLE || next == RETAINING;
            case AVAILABLE -> next == UNAVAILABLE || next == RETAINING;
            case UNAVAILABLE -> next == ACTIVATING || next == RETAINING;
            case RETAINING -> next == ACTIVATING || next == INACTIVE;
        };
    }
}
