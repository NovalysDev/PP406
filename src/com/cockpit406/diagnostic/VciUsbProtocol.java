package com.cockpit406.diagnostic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** ACTIA F008 framing, reconstructed from USB traces and the installed driver.
 * No Android dependency: the same implementation is exercised against USB traces.
 */
public final class VciUsbProtocol {
    public interface Link {
        void write(byte[] data, int timeoutMs) throws IOException;
        int read(byte[] buffer, int timeoutMs) throws IOException;
    }
    public interface Busy {void update(long elapsedMs,long limitMs);}
    public interface Clock {long millis();void pause(long millis)throws IOException;}
    private static final Clock SYSTEM_CLOCK=new Clock(){
        public long millis(){return System.nanoTime()/1000000L;}
        public void pause(long millis)throws IOException {
            try{Thread.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Lecture interrompue",e);}
        }
    };
    // Application budget for slow/absent-ECU initialization, not a measured
    // factory timeout. Never retransmit while the accepted request is busy.
    public static final int INIT_DEADLINE_MS=180000;
    private final Link link;
    private final int deadlineMs;
    private final Busy busy;
    private final Clock clock;
    private boolean recoveryRequired;
    private final byte[] input = new byte[256];
    private int position, available;
    public VciUsbProtocol(Link link) { this(link,10000); }
    public VciUsbProtocol(Link link,int deadlineMs) {this(link,deadlineMs,(elapsed,limit)->{});}
    public VciUsbProtocol(Link link,int deadlineMs,Busy busy) {this(link,deadlineMs,busy,SYSTEM_CLOCK);}
    public VciUsbProtocol(Link link,int deadlineMs,Busy busy,Clock clock) {
        if(deadlineMs<1000||deadlineMs>20000)throw new IllegalArgumentException("USB deadline");
        this.link=link;this.deadlineMs=deadlineMs;this.busy=busy;this.clock=clock;
    }
    public boolean needsRecovery(){return recoveryRequired;}

    public static byte[] frame(int type, int flags, byte[] piece) {
        if (piece.length > 251) throw new IllegalArgumentException("Frame too long");
        byte[] frame = new byte[piece.length + 5];
        frame[0] = (byte) type; frame[1] = 9;
        frame[2] = (byte) (piece.length + 1); frame[3] = (byte) flags;
        System.arraycopy(piece, 0, frame, 4, piece.length);
        int sum = 0;
        for (int i = 0; i < frame.length - 1; i++) sum += frame[i] & 255;
        frame[frame.length - 1] = (byte) (255 - sum);
        return frame;
    }

    public static List<byte[]> outgoing(byte[] body) {
        if (body.length < 20 || body.length > 8192) throw new IllegalArgumentException("Body size");
        List<byte[]> frames = new ArrayList<>();
        for (int offset = 0, seq = 0; offset < body.length; offset += 251, seq++) {
            int end = Math.min(body.length, offset + 251);
            int flags = seq | (offset == 0 ? 128 : 0) | (end == body.length ? 64 : 0);
            frames.add(frame(0x40, flags, Arrays.copyOfRange(body, offset, end)));
        }
        return frames;
    }

    private int remaining(long deadline) throws IOException {
        long millis = deadline-clock.millis();
        if (millis <= 0) throw new IOException("Délai de réponse du Lexia dépassé");
        return (int) Math.min(5000, Math.max(1, millis));
    }
    private void write(byte[] bytes, long deadline) throws IOException {
        link.write(bytes, remaining(deadline));
    }
    private byte[] read(int count, long deadline) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(count);
        while (result.size() < count) {
            if (position == available) {
                available = link.read(input, remaining(deadline)); position = 0;
                if (available <= 0 || available > input.length) throw new IOException("Lecture USB interrompue");
            }
            int take = Math.min(count - result.size(), available - position);
            result.write(input, position, take); position += take;
        }
        return result.toByteArray();
    }

    /** One request only. Never retries a vehicle command after a failed transfer. */
    public synchronized byte[] exchange(byte[] body) throws IOException {
        if(recoveryRequired)throw new IOException("Liaison non synchronisée · réinitialiser le Lexia avant une nouvelle commande");
        try{return transaction(body);}catch(IOException e){recoveryRequired=true;throw e;}
    }
    private byte[] transaction(byte[] body)throws IOException {
        if (position != available) throw new IOException("Données USB résiduelles");
        long started=clock.millis(),lastProgress=-1000;
        long limit=body.length>=20&&body[0]!=0&&body[1]==1?INIT_DEADLINE_MS:deadlineMs;
        long deadline=started+limit;
        for (byte[] frame : outgoing(body)) {
            write(frame, deadline);
            byte[] ack = read(3, deadline);
            if (ack[0] != 6 || ack[1] != 0x40 || (ack[2] != 0 && ack[2] != 9))
                throw new IOException("Accusé USB inattendu : " + hex(ack));
        }
        for (;;) {
            write(frame(0x41, 0xC0, new byte[0]), deadline);
            byte[] ready = read(4, deadline);
            if (ready[0] != 0x42 || ready[1] != 0x41 || ready[2] != 9 || (ready[3] != 0 && ready[3] != 1))
                throw new IOException("État USB inattendu : " + hex(ready));
            if (ready[3] == 0) break;
            long elapsed=clock.millis()-started;
            if(elapsed-lastProgress>=1000){lastProgress=elapsed;
                // A presentation failure cannot abandon an accepted request.
                try{busy.update(elapsed,limit);}catch(RuntimeException ignored){}
            }
            clock.pause(20);
        }
        write(frame(0x43, 0xC0, new byte[0]), deadline);
        byte[] header = read(4, deadline);
        int declared = header[2] & 255;
        // Every permitted read response fits in one frame. Fragmented responses
        // require separately observed handshake evidence before being supported.
        if (header[0] != 0x44 || header[1] != 9 || declared < 1 || declared > 252 || (header[3] & 255) != 0xC0)
            throw new IOException("Trame de réponse USB non reconnue : " + hex(header));
        byte[] tail = read(declared, deadline);
        int sum = 0;
        for (byte b : header) sum += b & 255;
        for (byte b : tail) sum += b & 255;
        if ((sum & 255) != 255) throw new IOException("Somme de contrôle USB incorrecte");
        write(new byte[] {6, 0x44, 9}, deadline);
        if (position != available) throw new IOException("Réponse USB avec octets supplémentaires");
        return Arrays.copyOf(tail, tail.length - 1);
    }

    public static String hex(byte[] data) {
        char[] result = new char[data.length * 2];
        char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < data.length; i++) {
            result[i * 2] = digits[(data[i] & 255) >>> 4]; result[i * 2 + 1] = digits[data[i] & 15];
        }
        return new String(result);
    }
    public static byte[] unhex(String hex) {
        if ((hex.length() & 1) != 0) throw new IllegalArgumentException("Hex length");
        byte[] result = new byte[hex.length() / 2];
        for (int i = 0; i < result.length; i++) {
            int hi = Character.digit(hex.charAt(i * 2), 16), lo = Character.digit(hex.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("Hex digit");
            result[i] = (byte) (hi * 16 + lo);
        }
        return result;
    }
}
