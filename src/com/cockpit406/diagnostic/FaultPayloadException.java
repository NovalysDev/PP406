package com.cockpit406.diagnostic;

import java.io.IOException;

/** A complete, positively acknowledged reply whose fault payload is unsupported.
 * Transport, envelope, ECU status and identity failures must never use this type.
 */
final class FaultPayloadException extends IOException {
    FaultPayloadException(String message) { super(message); }
}
