package com.cockpit406.diagnostic;

import java.util.LinkedHashMap;
import java.util.Map;

/** MP7.2 CAL13 conversions, statically recovered. Physical layout remains to be checked on a 306. */
public final class Mp72Decoder {
    private Mp72Decoder(){}
    /** Manual variant confirmation is bound to the whole stable identification. */
    public static boolean confirmedIdentity(String current,String confirmed){
        return current!=null&&!current.isEmpty()&&current.equals(confirmed);
    }
    public static Map<String,Double> decode(byte[] data) {
        if(data==null||data.length<2||data.length>128||data[0]!=0x61)throw new IllegalArgumentException("Réponse MP7.2 non reconnue");
        int group=data[1]&255;Map<String,Double> out=new LinkedHashMap<>();
        if(group==0xc0){
            if(data.length<18)throw new IllegalArgumentException("Groupe MP7.2 C0 incomplet");
            // The five-byte Buf wrapper used by CAL13 is absent from the USB payload.
            out.put("rpm",u(data,2)*40.0);
            out.put("battery_v",u(data,3)*0.0705);
            out.put("ecu_mv",u(data,4)*70.5);
            out.put("throttle_deg",u(data,10)*0.395);
            out.put("throttle_mv",u(data,11)*19.61);
            out.put("coolant_r_c",u(data,13)*0.75-48);
            out.put("air_r_c",u(data,15)*0.75-48);
            out.put("map_mbar",u(data,16)*4.43);
            out.put("idle_steps",(double)u(data,17));
        }else if(group==0xc4){
            if(data.length<20)throw new IllegalArgumentException("Groupe MP7.2 C4 incomplet");
            out.put("speed_kmh",u(data,19)*1.113);
        }else throw new IllegalArgumentException("Conversion de ce groupe MP7.2 non vérifiée");
        return out;
    }
    private static int u(byte[] data,int offset){return data[offset]&255;}
    public static final String[][] SIGNALS={
        {"rpm","Régime moteur","tr/min","0"},{"battery_v","Tension calculateur","V","2"},
        {"ecu_mv","Tension UCE","mV","0"},{"throttle_deg","Papillon","°","1"},
        {"throttle_mv","Potentiomètre papillon","mV","0"},{"coolant_r_c","Température eau","°C","1"},
        {"air_r_c","Température admission","°C","1"},{"map_mbar","Pression admission","mbar","0"},
        {"idle_steps","Position moteur pas à pas","pas","0"},{"speed_kmh","Vitesse véhicule","km/h","1"}
    };
    public static EngineValueGuide.Help help(String key){
        EngineValueGuide.Help common=EngineValueGuide.help(key);
        String meaning=key.equals("ecu_mv")?"Champ tension UCE de la définition MP7.2, distinct de la tension batterie. Sa signification physique reste à confirmer."
            :key.equals("idle_steps")?"Position de commande du moteur pas à pas de ralenti. Ce n’est pas un pourcentage d’ouverture ni une mesure de débit d’air."
            :key.equals("coolant_r_c")?"Température du liquide de refroidissement rapportée par le calculateur MP7.2."
            :key.equals("air_r_c")?"Température de l’air d’admission rapportée par le calculateur MP7.2.":common.meaning;
        String checks=key.equals("ecu_mv")?"Comparer avec les autres alimentations et les défauts. Vérifier la définition de ce champ avant de conclure à une panne."
            :key.equals("idle_steps")?"Comparer la commande avec le régime, la température et les charges. Examiner l’admission et les connexions selon la procédure de cette motorisation.":common.checks;
        return new EngineValueGuide.Help(meaning,
            "306 · Bosch MP7.2 · année, moteur et référence exacte à confirmer par identification.\n\nConversions étudiées dans la définition MP7.2 ; elles restent à comparer à PP2000 sur la voiture. Les réponses brutes sont conservées.\n\nAucune plage officielle de diagnostic confirmée pour la variante de cette 306. Les couleurs orange et rouge restent désactivées : les seuils du V6 ne s’appliquent pas ici.",checks);
    }
}
