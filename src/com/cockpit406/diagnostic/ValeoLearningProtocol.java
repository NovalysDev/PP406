package com.cockpit406.diagnostic;

/**
 * Fixed catalogue definitions for the Valeo key-learning start/end requests.
 * No transport or session dispatcher is connected. These catalogue bytes are
 * not proof of a safe physical sequence or a successful learning response.
 */
public final class ValeoLearningProtocol {
    public enum Boundary { BEGIN, END }
    public static byte[] catalogueRequest(Boundary boundary) {
        if(boundary==null)throw new IllegalArgumentException("Étape absente");
        byte[] body=new byte[21];body[0]=0x37;body[1]=0x07;body[2]=1;body[4]=(byte)0xaa;
        body[20]=(byte)(boundary==Boundary.BEGIN?0xda:0xfa);
        return body;
    }
    private ValeoLearningProtocol() {}
}
