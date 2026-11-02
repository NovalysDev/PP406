package com.cockpit406.diagnostic;

import java.io.IOException;
import java.util.Locale;

/** Never assigns physical units to an unverified conversion. */
public final class ExtendedValues {
    public static String decode(String module,String group,byte[] data)throws IOException {
        StringBuilder out=new StringBuilder(group+" · trame : "+VciUsbProtocol.hex(data));
        if(module.equals("seat")&&group.equals("group1")) {
            if(data.length<10)throw new IOException("Positions Bitron incomplètes");
            String[] names={"Site","Glissière","Rehausse","Dossier"};
            for(int i=0;i<4;i++) {
                int pos=2+2*i,value=(data[pos]&255)|((data[pos+1]&255)<<8);
                out.append('\n').append(names[i]).append(" : ").append(value).append(" unités internes");
            }
            out.append("\nValeurs brutes des capteurs ; aucune conversion en distance ou en angle. L’interprétation des valeurs limites reste à confirmer.");
        } else if((module.equals("abs5")||module.equals("abs53"))&&group.equals("group1")) {
            if(data.length<10||data[0]!=0x11||data[1]!=1)throw new IOException("Vitesses ABS incomplètes ou autre groupe");
            // CAL4 exports 51..54 and CAL107 TRAME015799: offsets 8/4/2/6.
            // CAL4 tests the high byte before subtracting 28; CAL107 uses a
            // full-word threshold of 21 with no subtraction. Keep distinct.
            String[] names={"Avant gauche","Avant droite","Arrière gauche","Arrière droite"};
            int[] offsets={8,4,2,6};
            for(int i=0;i<4;i++) {
                int pos=offsets[i],raw=(data[pos]&255)|((data[pos+1]&255)<<8);
                double value=module.equals("abs5")?((raw&0xff00)>28?(raw-28)/16.0:0):(raw>21?raw/16.0:0);
                out.append('\n').append(names[i]).append(" : ").append(String.format(Locale.FRANCE,"%.2f",value)).append(" km/h (conversion à valider)");
            }
        } else if(module.equals("suspension")&&group.equals("group1")) {
            if(data.length<14)throw new IOException("Paramètres Denso incomplets");
            // CAL73 _VITESSEVEHICULE631 explicitly swaps the 16-bit word at 1.
            double speed=(((data[1]&255)<<8)|(data[2]&255))/256.0;
            out.append("\nVitesse : ").append(String.format(Locale.FRANCE,"%.2f",speed)).append(" km/h (conversion à valider)");
            out.append("\nÉtat suspension brut : ").append(data[12]&255);
            out.append("\nContacteur brut : ").append(data[13]&255);
        } else if(module.equals("locking")) {
            if(data.length!=17)throw new IOException("Compteurs Kiekert incomplets");
            out.append("\n15 compteurs internes (FF = non renseigné) :\n");
            for(int i=2;i<17;i++)out.append(i-1).append('=').append((data[i]&255)==255?"—":Integer.toString(data[i]&255)).append(i==16?"":" · ");
        } else out.append("\nRéponse spécifique au calculateur ; conversion physique en cours de validation.");
        return out.toString();
    }
    private ExtendedValues() {}
}
