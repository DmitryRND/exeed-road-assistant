package com.example.instrumentawdprobe;

public final class InstrumentOverlayLifecyclePolicyTest {
    public static void main(String[] args) {
        assertTrue(InstrumentOverlayLifecyclePolicy.shouldRestore(true, false, false, false),
                "cold enabled start must restore the widget");
        assertFalse(InstrumentOverlayLifecyclePolicy.shouldRestore(true, false, true, false),
                "opening settings must keep an attached widget intact");
        assertFalse(InstrumentOverlayLifecyclePolicy.shouldRestore(true, true, false, false),
                "full-screen navigation must not be covered by the widget");
        assertFalse(InstrumentOverlayLifecyclePolicy.shouldRestore(false, false, false, false),
                "disabled feature must not restore the widget");
        assertFalse(InstrumentOverlayLifecyclePolicy.shouldRestore(true, false, false, true),
                "a second launch must not duplicate an already scheduled restore");

        assertFalse(InstrumentOverlayLifecyclePolicy.isModeChange(3, 3),
                "initial Spinner callback is not a user mode change");
        assertTrue(InstrumentOverlayLifecyclePolicy.isModeChange(3, 4),
                "a different selected mode must be applied");

        System.out.println("InstrumentOverlayLifecyclePolicyTest passed");
    }

    private static void assertTrue(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void assertFalse(boolean value, String message) {
        if (value) throw new AssertionError(message);
    }
}
