package com.cockpit406.diagnostic;

import java.util.Arrays;

/**
 * Offline preparation for the captured Valeo 9624799080 CODE_ACCES exchange.
 * This class has no transport, UI, persistence or logging. In particular, it
 * does not authorize sending a code or declaring that a code is accepted.
 */
public final class ValeoAccessProtocol {
    public enum Result { REFUSED, REQUIRES_STATUS_CHECK, UNKNOWN }
    private static final String ALLOWED="0ZYXWVUTSRQPNMLKJHGFEDCBA987654321";

    /** Caller owns both input and result and must erase them after use. */
    public static byte[] buildRequest(char[] code) {
        if(code==null||code.length!=4)throw new IllegalArgumentException("Le code doit comporter quatre caractères");
        for(char c:code)if(ALLOWED.indexOf(c)<0)throw new IllegalArgumentException("Caractère non pris en charge");
        byte[] body=new byte[25];
        body[0]=0x37;body[1]=0x06;body[2]=0x05;body[4]=(byte)0xaa;body[20]=(byte)0xea;
        for(int i=0;i<4;i++)body[21+i]=(byte)code[i];
        return body;
    }

    /**
     * Classify only an envelope matching the observed refusal format. A
     * non-0x99 response is not proof of success: PP2000 separately rereads
     * state, and no accepted-code trace has been validated yet.
     */
    public static Result classify(byte[] body) {
        if(body==null||body.length<21||body[0]!=0x37||body[1]!=0x06||body[2]!=5||body[3]!=0||
           body[4]!=(byte)0xaa||body[10]!=0x37||body[11]!=0x06||
           body[14]!=1||body[15]!=(byte)0xaa)return Result.UNKNOWN;
        int length=(body[12]&255)|((body[13]&255)<<8);
        if(length<1||length!=body.length-20)return Result.UNKNOWN;
        return (body[20]&255)==0x99?Result.REFUSED:Result.REQUIRES_STATUS_CHECK;
    }

    public static void clear(char[] value){if(value!=null)Arrays.fill(value,'\0');}
    public static void clear(byte[] value){if(value!=null)Arrays.fill(value,(byte)0);}
    private ValeoAccessProtocol() {}
}
