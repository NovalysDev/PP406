package com.cockpit406.diagnostic;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;

/** Android USB host access; no Windows driver, native library or root. */
public final class AndroidLexiaUsb implements VciUsbProtocol.Link, Closeable {
    public interface Trace { void transfer(String direction, byte[] data) throws IOException; }
    private final UsbDeviceConnection connection;
    private final UsbInterface intf;
    private final UsbEndpoint incoming, outgoing;
    private final Trace trace;
    public static boolean matches(UsbDevice device) { return device.getVendorId() == 0x103A && device.getProductId() == 0xF008; }

    public AndroidLexiaUsb(UsbManager manager, UsbDevice device, Trace trace) throws IOException {
        if (!matches(device)) throw new IOException("Ce périphérique n’est pas le Lexia 103A:F008");
        UsbInterface candidate = null; UsbEndpoint in = null, out = null;
        for (int i = 0; i < device.getInterfaceCount(); i++) {
            UsbInterface item = device.getInterface(i);
            if (item.getId() != 0) continue;
            for (int j = 0; j < item.getEndpointCount(); j++) {
                UsbEndpoint ep = item.getEndpoint(j);
                if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_BULK || ep.getMaxPacketSize() != 64) continue;
                if (ep.getAddress() == 0x85) in = ep;
                if (ep.getAddress() == 0x06) out = ep;
            }
            candidate = item;
        }
        if (candidate == null || in == null || out == null) throw new IOException("Points d’accès USB du Lexia non reconnus");
        UsbDeviceConnection opened = manager.openDevice(device);
        if (opened == null) throw new IOException("Ouverture USB refusée : vérifier l’autorisation Android");
        if (!opened.claimInterface(candidate, true)) { opened.close(); throw new IOException("Interface USB déjà occupée"); }
        connection = opened; intf = candidate; incoming = in; outgoing = out; this.trace = trace;
    }
    @Override public void write(byte[] data, int timeoutMs) throws IOException {
        int sent = connection.bulkTransfer(outgoing, data, data.length, timeoutMs);
        if (sent != data.length) throw new IOException("Écriture USB incomplète : " + sent + "/" + data.length);
        trace.transfer("out", data);
    }
    @Override public int read(byte[] buffer, int timeoutMs) throws IOException {
        int count = connection.bulkTransfer(incoming, buffer, buffer.length, timeoutMs);
        if (count <= 0) throw new IOException("Pas de réponse USB (câble, alimentation ou délai)");
        trace.transfer("in", Arrays.copyOf(buffer, count));
        return count;
    }
    @Override public void close() { connection.releaseInterface(intf); connection.close(); }
}
