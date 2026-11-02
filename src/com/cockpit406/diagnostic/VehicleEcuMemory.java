package com.cockpit406.diagnostic;

import android.content.Context;
import org.json.*;
import java.util.*;

/** A selected VIN is an index, never proof of what is connected or authority to write. */
public final class VehicleEcuMemory {
    private static final String PREF="vehicle_ecu_memory";
    public static boolean validVin(String vin){return ClubBridge.normalizeVin(vin).matches("[A-HJ-NPR-Z0-9]{17}");}
    public static synchronized JSONObject load(Context context,String vin){
        vin=ClubBridge.normalizeVin(vin);if(!validVin(vin))return new JSONObject();
        try{return new JSONObject(context.getSharedPreferences(PREF,0).getString(vin,"{}"));}catch(JSONException e){return new JSONObject();}
    }
    public static synchronized void remember(Context context,String vin,String plan,String module,String profile,String identity,boolean monitorable){
        vin=ClubBridge.normalizeVin(vin);if(!validVin(vin)||identity==null||identity.isEmpty())return;
        try{JSONObject car=load(context,vin),modules=car.optJSONObject("modules");if(modules==null)modules=new JSONObject();
            modules.put(module,new JSONObject().put("profile",profile).put("identity",identity).put("observedAt",System.currentTimeMillis()/1000).put("monitorable",monitorable));
            car.put("vin",vin).put("plan",plan).put("modules",modules);
            context.getSharedPreferences(PREF,0).edit().putString(vin,car.toString()).apply();
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    public static List<String> prioritize(List<String> candidates,String remembered){
        List<String> ordered=new ArrayList<>(candidates);if(remembered!=null&&ordered.remove(remembered))ordered.add(0,remembered);return ordered;
    }
    public static String profile(JSONObject car,String module){JSONObject modules=car.optJSONObject("modules"),ecu=modules==null?null:modules.optJSONObject(module);return ecu==null?null:ecu.optString("profile",null);}
    public static String description(JSONObject car){
        JSONObject modules=car.optJSONObject("modules");if(modules==null||modules.length()==0)return "Aucun calculateur mémorisé pour ce VIN. Faire un premier scan.";
        StringBuilder text=new StringBuilder("Dernières identifications conservées. Elles seront vérifiées à chaque connexion ; une absence de réponse ne les supprime pas.\n\n");
        Iterator<String> keys=modules.keys();while(keys.hasNext()){String module=keys.next();JSONObject ecu=modules.optJSONObject(module);text.append(module).append(" · ").append(ecu.optString("profile")).append("\nRelevé : ").append(new java.util.Date(ecu.optLong("observedAt")*1000)).append('\n').append(ecu.optBoolean("monitorable")?"Surveillance préparée":"Lecture mémorisée · surveillance non intégrée").append("\n\n");}return text.toString();
    }
    private VehicleEcuMemory(){}
}
