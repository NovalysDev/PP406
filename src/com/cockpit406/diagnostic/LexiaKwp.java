package com.cockpit406.diagnostic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Lexia KWP result directory, before the ECU's service bytes.
 * COMMVEH.DLL Buf::CreerBUFSAL (0x410f11..0x410fd8): count, then
 * address/length/big-endian offset descriptors. Never search for a service byte
 * inside an otherwise invalid reply. Original USB replies stay in the journal.
 */
public final class LexiaKwp {
    public static final class Contract {
        public final int address;
        public final boolean slowInit;
        private final int initAddress,initFamily,initOperation;
        private Contract(int address,boolean slowInit,int initAddress,int initFamily,int initOperation){this.address=address;this.slowInit=slowInit;this.initAddress=initAddress;this.initFamily=initFamily;this.initOperation=initOperation;}
        public byte[] payload(byte[] raw)throws IOException{return single(raw,address);}
        public byte[] gatewayPayload(byte[] raw)throws IOException{return single(raw,initAddress);}
        public byte[] payload(byte[] raw,byte[] request)throws IOException{
            if(request==null||request.length<2)throw new IOException("Commande KWP absente");
            int target=(request[0]&255)==initFamily&&(request[1]&255)==initOperation?initAddress:address;
            return single(raw,target);
        }
        public void checkInit(byte[] data)throws IOException {
            // C/5-baud INIT receives two key bytes. It is not service 81/C1.
            if(slowInit?data.length!=2:data.length<2||data.length>128||data[0]!=(byte)0xc1)
                throw new IOException("Initialisation KWP non reconnue : "+VciUsbProtocol.hex(data));
        }
    }
    public static Contract fromProgram(byte[] program,byte[] init,byte[] identify) {
        if(program.length%45!=0)throw new IllegalArgumentException("Table KWP invalide");
        String initKey=VciUsbProtocol.hex(Arrays.copyOf(init,2)).toUpperCase(java.util.Locale.ROOT);
        String idKey=VciUsbProtocol.hex(Arrays.copyOf(identify,2)).toUpperCase(java.util.Locale.ROOT);
        int address=-1;boolean slow=false,fast=false;
        for(int i=0;i<program.length;i+=45){
            String row=new String(program,i,45,StandardCharsets.US_ASCII);
            if(row.charAt(16)!='E')continue;
            String key=row.substring(1,5),value=row.substring(17,24).replaceFirst("[ -]+$","");
            if(key.equals(idKey)&&row.charAt(0)=='T'&&value.matches("[0-9A-F]{2}")){
                int a=Integer.parseInt(value,16);
                if(address!=-1&&address!=a)throw new IllegalArgumentException("Adresses KWP ambiguës");address=a;
            }
            if(key.equals(initKey)){
                if(row.charAt(0)=='C'&&value.matches("[0-9A-F]{2}"))slow=true;
                if((row.charAt(0)=='C'&&value.equals("--1"))||(row.charAt(0)=='D'&&value.equals("81")))fast=true;
            }
        }
        if(address<0||slow==fast)throw new IllegalArgumentException("Contrat KWP non défini par la table");
        int initAddress=address;
        // A multiplexed gateway can start at D0 while the selected ECU is B3.
        // Only derive a destination from a complete checked fast-init frame.
        if(!slow&&init.length>20&&(init[20]&255)==0x81){
            if(init.length!=25||init[2]!=5||init[3]!=0||init[22]!=(byte)0xf1||init[23]!=(byte)0x81||
               (((init[20]&255)+(init[21]&255)+(init[22]&255)+(init[23]&255))&255)!=(init[24]&255))
                throw new IllegalArgumentException("Trame d'initialisation KWP invalide");
            initAddress=init[21]&255;
        }
        return new Contract(address,slow,initAddress,init[0]&255,init[1]&255);
    }
    public static byte[] single(byte[] raw,int address)throws IOException {
        if(raw.length<5||(raw[0]&255)!=1)throw new IOException("Répertoire KWP non reconnu ou réponses multiples : "+VciUsbProtocol.hex(raw));
        int length=raw[2]&255,offset=((raw[3]&255)<<8)|(raw[4]&255);
        if((raw[1]&255)!=address||offset!=5||length==0||offset+length!=raw.length)
            throw new IOException("Descripteur KWP incohérent : "+VciUsbProtocol.hex(raw));
        return Arrays.copyOfRange(raw,offset,offset+length);
    }
    private LexiaKwp(){}
}
