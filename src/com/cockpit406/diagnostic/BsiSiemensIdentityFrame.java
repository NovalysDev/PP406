package com.cockpit406.diagnostic;

import java.util.Arrays;

/** Offline analysis of the two K-line responses published for a Siemens B4
 * BSI on a 406 Coupe. This does not implement a Lexia session or vehicle IO.
 * Source: roma6868/Peugeot_406_coupe_automatisation, SCAN_OBD_PP2000,
 * RESOURCE/img/Menu_identification_PP2000.png. Applicability to another BSI
 * or to the data returned by the Lexia USB protocol is not established.
 */
public final class BsiSiemensIdentityFrame {
    public final String psaReference, softwareIndex, serial, productionDate;
    private BsiSiemensIdentityFrame(String reference,String software,String serial,String date){
        this.psaReference=reference;this.softwareIndex=software;this.serial=serial;this.productionDate=date;
    }
    /** Matching the public example alone is not proof of vehicle compatibility. */
    public boolean matchesPublishedReference(){return "9637436480".equals(psaReference);}
    public static BsiSiemensIdentityFrame decode(byte[] frame){
        if(frame==null || (frame.length!=14 && frame.length!=28))throw invalid();
        int header=frame[0]&255;
        if((header!=0x8a && header!=0x98) || (header&0x3f)!=frame.length-4
           || (frame[1]&255)!=0xf1 || (frame[2]&255)!=0xd0 || (frame[3]&255)!=0x61)throw invalid();
        int checksum=0;for(int i=0;i<frame.length-1;i++)checksum=(checksum+(frame[i]&255))&255;
        if(checksum!=(frame[frame.length-1]&255))throw invalid();
        return decodePayload(Arrays.copyOfRange(frame,3,frame.length-1));
    }
    /** Decode the ECU payload after a separate transport has validated its envelope.
     * Only the published Siemens layout is recognized; no ECU is contacted here.
     */
    public static BsiSiemensIdentityFrame decodePayload(byte[] payload){
        if(payload==null||(payload.length!=10&&payload.length!=24)||payload[0]!=0x61)throw invalid();
        int identifier=payload[1]&255;
        if(identifier==0x80 && payload.length==24){
            return new BsiSiemensIdentityFrame(bcd(payload,2,5),bcd(payload,14,2),null,null);
        }
        if(identifier==0x81 && payload.length==10){
            String serial=hex(payload,2,3),date=bcd(payload,5,3);
            int day=Integer.parseInt(date.substring(0,2)),month=Integer.parseInt(date.substring(2,4));
            if(day<1||day>31||month<1||month>12)throw invalid();
            return new BsiSiemensIdentityFrame(null,null,serial,date.substring(0,2)+"/"+date.substring(2,4)+"/"+date.substring(4,6));
        }
        throw invalid();
    }
    private static String bcd(byte[] data,int offset,int length){
        for(int i=offset;i<offset+length;i++)if(((data[i]&255)>>>4)>9 || (data[i]&15)>9)throw invalid();
        return hex(data,offset,length);
    }
    private static String hex(byte[] data,int offset,int length){
        final char[] digits="0123456789ABCDEF".toCharArray();StringBuilder s=new StringBuilder(length*2);
        for(int i=offset;i<offset+length;i++){int x=data[i]&255;s.append(digits[x>>>4]).append(digits[x&15]);}return s.toString();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Trame d'identification BSI non reconnue");}
}
