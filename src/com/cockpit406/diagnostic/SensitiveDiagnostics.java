package com.cockpit406.diagnostic;

import java.io.IOException;

/** Key/immobilizer sessions must never write raw diagnostic bytes to journals. */
public final class SensitiveDiagnostics {
    public interface TransferSink { void record(String direction,byte[] bytes) throws IOException; }
    public static boolean hideRaw(String module){return "keys".equals(module);}
    public static void recordTransfer(String module,TransferSink sink,String direction,byte[] bytes)throws IOException {
        if(!hideRaw(module))sink.record(direction,bytes);
    }
    private SensitiveDiagnostics() {}
}
