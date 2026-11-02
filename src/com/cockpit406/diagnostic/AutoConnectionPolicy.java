package com.cockpit406.diagnostic;

/** Decide when a visible page may request a read-only connection. */
public final class AutoConnectionPolicy {
    public static boolean mayStart(boolean resumed, boolean busy, boolean devicePresent,
                                   boolean vehicleReady, boolean readerAvailable,
                                   boolean resetRequired, boolean unresolvedGarage,
                                   boolean alreadyConnected) {
        return resumed && !busy && devicePresent && vehicleReady && readerAvailable
            && !resetRequired && !unresolvedGarage && !alreadyConnected;
    }
    private AutoConnectionPolicy() {}
}
