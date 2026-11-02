package com.cockpit406.diagnostic;

import java.nio.charset.StandardCharsets;

/** Identité déclarée par le firmware PP406 de la Lexia. */
final class FirmwareIdentity {
    static final String ERROR = "Firmware incompatible : lecture bloquée. Firmware requis : NOVALYS APP PP406 V5.0.0";
    private static final byte[] REQUIRED = (
            "NOVALYS APP   \0" +
            "PP406    \0" +
            "V5.0.0\0" +
            "@ACTIA\0" +
            "03.01.14\0"
    ).getBytes(StandardCharsets.US_ASCII);

    private FirmwareIdentity() {}

    static boolean matches(byte[] boardVersion) {
        if (boardVersion == null || boardVersion.length < REQUIRED.length) return false;
        for (int i = 0; i < REQUIRED.length; i++) {
            if (boardVersion[i] != REQUIRED[i]) return false;
        }
        return true;
    }
}
