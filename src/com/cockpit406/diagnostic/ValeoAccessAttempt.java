package com.cockpit406.diagnostic;

import java.io.IOException;

/**
 * One-shot access check for a separately verified Valeo session.
 * Not wired to Android UI or USB: the caller must provide a confidential
 * transport and validate session identity and vehicle conditions first.
 * This is not a key-learning command and never retries.
 */
public final class ValeoAccessAttempt {
    public interface ConfidentialTransport {
        /** Must not record request or reply bytes, including USB-level traces. */
        byte[] exchangeSensitive(byte[] request) throws IOException;
    }

    private boolean consumed;

    public synchronized ValeoAccessProtocol.Result check(String identifiedReference,char[] code,
                                                          ConfidentialTransport transport) {
        byte[] request=null,reply=null;
        try {
            if(consumed)throw new IllegalStateException("Tentative déjà utilisée : ne pas répéter automatiquement");
            if(!"9624799080".equals(identifiedReference)||transport==null)
                throw new IllegalArgumentException("Session Valeo non identifiée");
            request=ValeoAccessProtocol.buildRequest(code);
            consumed=true; // consume before entering I/O, including lost replies
            try { reply=transport.exchangeSensitive(request); }
            catch(IOException failure) { return ValeoAccessProtocol.Result.UNKNOWN; }
            return ValeoAccessProtocol.classify(reply);
        } finally {
            ValeoAccessProtocol.clear(code);
            ValeoAccessProtocol.clear(request);
            ValeoAccessProtocol.clear(reply);
        }
    }
    public synchronized boolean wasConsumed(){return consumed;}
}
