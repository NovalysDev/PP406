package com.cockpit406.diagnostic;

import java.util.LinkedHashMap;
import java.util.Map;

/** CAL5 129..134 + COMMVEH Buf@get(int*,offset): zero-extended LE words.
 * Two real Valeo frames are regression fixtures; comparison with the vehicle's
 * physical temperatures, including subzero temperatures, is still pending.
 */
public final class ClimateDecoder {
    public static final String[][] SIGNALS={
        {"outside_c","Température extérieure","°C","1"},
        {"evaporator_c","Température évaporateur","°C","1"},
        {"climate_coolant_c","Température d’eau · clim","°C","1"},
        {"sunlight_raw","Ensoleillement","unité calculateur","0"},
        {"climate_speed_kmh","Vitesse · climatisation","km/h","0"},
        {"climate_battery_v","Tension · climatisation","V","1"}
    };
    public static Map<String,Double> decode(byte[] data){
        if(data==null||data.length!=24||data[0]!=0x11||data[1]!=1)throw new IllegalArgumentException("Groupe Valeo 1 non reconnu");
        Map<String,Double> values=new LinkedHashMap<>();
        values.put("outside_c",word(data,4)/10.0);
        values.put("evaporator_c",word(data,6)/10.0);
        values.put("climate_coolant_c",word(data,8)/10.0);
        values.put("sunlight_raw",(double)word(data,10));
        values.put("climate_speed_kmh",(double)word(data,12));
        values.put("climate_battery_v",(data[14]&255)/10.0);
        return values;
    }
    private static int word(byte[] b,int offset){return (b[offset]&255)|((b[offset+1]&255)<<8);}
    private ClimateDecoder(){}
}
