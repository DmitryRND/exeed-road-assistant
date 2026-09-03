package com.example.instrumentawdprobe;

/** Pure lifecycle rules for the persistent instrument-cluster overlay. */
final class InstrumentOverlayLifecyclePolicy {
    private InstrumentOverlayLifecyclePolicy() { }

    static boolean shouldRestore(boolean featureEnabled, boolean fullScreenMapActive,
                                 boolean overlayAttached, boolean restorePending) {
        return featureEnabled && !fullScreenMapActive && !overlayAttached && !restorePending;
    }

    static boolean isModeChange(int storedMode, int selectedMode) {
        return storedMode != selectedMode;
    }
}
