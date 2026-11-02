package com.cockpit406.diagnostic;

/**
 * Read-only decoding of VAN CONF frame 0x824, payload only.
 * References: PSA-RE buses/AEE2001.full/VAN.CONF/824.yml (Apache-2.0)
 * and VanLiveConnect ParseDashboardPkt (MIT). Both describe these two fields.
 * No CAN/VAN transport, automatic ECU identification, or vehicle I/O is implied.
 */
public final class VanDashboardFrame {
    public static final int IDENTIFIER=0x824, PAYLOAD_LENGTH=7;
    public final Double rpm, speedKmh;

    private VanDashboardFrame(Double rpm,Double speedKmh){this.rpm=rpm;this.speedKmh=speedKmh;}

    public static VanDashboardFrame decode(int id,byte[] payload){
        if(id!=IDENTIFIER)throw new IllegalArgumentException("Identifiant VAN 824 requis");
        if(payload==null||payload.length!=PAYLOAD_LENGTH)throw new IllegalArgumentException("La trame 824 doit contenir exactement 7 octets de données");
        int rawRpm=((payload[0]&255)<<8)|(payload[1]&255);
        int rawSpeed=((payload[2]&255)<<8)|(payload[3]&255);
        return new VanDashboardFrame(rawRpm==0xffff?null:rawRpm/8.0,rawSpeed==0xffff?null:rawSpeed/100.0);
    }

    public static byte[] parsePayload(String text){
        if(text==null)throw new IllegalArgumentException("Saisis les 7 octets de données");
        String trimmed=text.trim();
        if(!trimmed.matches("(?i)[0-9a-f\\s:,-]+"))throw new IllegalArgumentException("Utilise uniquement des octets hexadécimaux");
        String hex=trimmed.replaceAll("[\\s:,-]","");
        if(hex.length()!=PAYLOAD_LENGTH*2)throw new IllegalArgumentException("Saisis exactement 7 octets, sans l'identifiant 824");
        byte[] result=new byte[PAYLOAD_LENGTH];
        for(int i=0;i<result.length;i++)result[i]=(byte)Integer.parseInt(hex.substring(i*2,i*2+2),16);
        return result;
    }
}
