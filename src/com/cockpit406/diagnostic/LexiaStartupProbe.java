package com.cockpit406.diagnostic;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Read only the Lexia board identity; no ECU is contacted. */
final class LexiaStartupProbe {
    interface Transport { byte[] exchange(byte[] request) throws IOException; }
    static final class Result {
        final boolean compatible;
        final String firmware;
        Result(boolean compatible,String firmware){this.compatible=compatible;this.firmware=firmware;}
        String incompatibilityDescription(){return "Firmware détecté : "+firmware+"\nFirmware requis : NOVALYS APP PP406 V5.0.0\nLecture des calculateurs bloquée. Après mise à jour du firmware, rebranche la Lexia.";}
    }
    private LexiaStartupProbe(){}

    static Result read(Transport transport)throws IOException{
        payload(transport.exchange(command(7)),7,true);
        byte[] version=payload(transport.exchange(command(18)),18,false);
        return new Result(FirmwareIdentity.matches(version),versionLabel(version));
    }
    private static byte[] command(int operation){
        byte[] request=new byte[20];request[1]=(byte)operation;request[4]=(byte)0xaa;return request;
    }
    private static byte[] payload(byte[] response,int operation,boolean coldAllowed)throws IOException{
        if(response==null||response.length<20||response[10]!=0||response[11]!=(byte)operation||response[15]!=(byte)0xaa||
           ((response[12]&255)|((response[13]&255)<<8))!=response.length-20)throw new IOException("Réponse USB non reconnue");
        int status=response[14]&255;
        if(status!=3&&!(coldAllowed&&status==12&&response.length==20))throw new IOException("Réponse Lexia non reconnue : "+status);
        return Arrays.copyOfRange(response,20,response.length);
    }
    private static String versionLabel(byte[] data){
        String[] fields=new String(data,StandardCharsets.US_ASCII).split("\0",-1);
        StringBuilder result=new StringBuilder();
        for(int i=0;i<Math.min(fields.length,3);i++){
            String value=fields[i].replaceAll("[^A-Za-z0-9 ._@-]","").trim();
            if(!value.isEmpty()){if(result.length()>0)result.append(' ');result.append(value);}
        }
        return result.length()==0?"non lisible":result.substring(0,Math.min(result.length(),65));
    }
}
