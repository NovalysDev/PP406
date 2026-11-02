package com.cockpit406.diagnostic;

import android.content.*;
import android.net.Uri;
import org.json.*;
import java.util.*;

/** Persisted appearance episodes for the Club; distinct from every diagnostic observation. */
public final class FaultAlerts {
    public static final Uri URI=Uri.parse("content://com.cockpit406.diagnostic.readings/v1/alerts");
    public static final String ACTION="com.cockpit406.diagnostic.FAULT_ALERTS_CHANGED";
    private static final String PREF="fault_alerts";
    public static JSONObject next(JSONObject state,String vin,String carId,String identity,FaultReport report,boolean complete)throws JSONException{
        if(!complete||!VehicleEcuMemory.validVin(vin)||carId==null||carId.isEmpty()||!carId.equals(report.carId)||identity==null||identity.isEmpty())return state;
        JSONObject lists=state.optJSONObject("presence");if(lists==null)lists=new JSONObject();
        String key=ClubBridge.normalizeVin(vin)+"|"+carId+"|"+report.module+"|"+report.profile+"|"+identity;
        JSONArray old=lists.optJSONArray(key);Set<String> before=old==null?null:new LinkedHashSet<>();if(old!=null)for(int i=0;i<old.length();i++)before.add(old.getString(i));
        List<String> codes=new ArrayList<>();for(FaultReport.Entry e:report.entries)codes.add(e.code);
        FaultPresence.Change change=FaultPresence.compare(before,codes,true);lists.put(key,new JSONArray(change.present));
        JSONArray events=state.optJSONArray("events");if(events==null)events=new JSONArray();JSONArray observations=FaultExport.eventsFor(report);
        for(int i=0;i<observations.length();i++){JSONObject event=observations.getJSONObject(i);if(change.appeared.contains(event.getString("code").toUpperCase(Locale.ROOT))){
            event.put("alertId",event.getString("sourceEventId")).put("kind","appeared");events.put(event);
        }}
        JSONArray bounded=new JSONArray();int bytes=128;for(int i=events.length()-1;i>=0&&bounded.length()<50;i--){JSONObject event=events.getJSONObject(i);int size=event.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length+1;if(bytes+size<=500*1024){bounded.put(event);bytes+=size;}}
        JSONArray chronological=new JSONArray();for(int i=bounded.length()-1;i>=0;i--)chronological.put(bounded.get(i));
        return new JSONObject().put("presence",lists).put("events",chronological);
    }
    public static synchronized int record(Context context,String vin,String carId,String identity,FaultReport report,boolean complete)throws JSONException{
        SharedPreferences prefs=context.getSharedPreferences(PREF,0);JSONObject old=new JSONObject(prefs.getString("state","{}"));JSONArray prior=old.optJSONArray("events");String oldEvents=prior==null?"[]":prior.toString();
        JSONObject updated=next(old,vin,carId,identity,report,complete);JSONArray events=updated.optJSONArray("events");
        if(!prefs.edit().putString("state",updated.toString()).commit())throw new IllegalStateException("Alertes non enregistrées");
        if(events==null||oldEvents.equals(events.toString()))return 0;
        context.getContentResolver().notifyChange(URI,null,0);
        // The broadcast only wakes the installed, signature-checked Club. Payload is queried through the protected provider.
        try{ClubBridge.verify(context);context.sendBroadcast(new Intent(ACTION).setPackage(ClubBridge.PACKAGE).setData(URI));}catch(Exception e){android.util.Log.i("PP406","Alertes conservées pour le prochain import Club");}
        return events.length();
    }
    public static synchronized JSONObject snapshot(Context context)throws JSONException{
        JSONObject state=new JSONObject(context.getSharedPreferences(PREF,0).getString("state","{}"));JSONArray saved=state.optJSONArray("events"),events=new JSONArray();
        if(saved!=null)for(int i=saved.length()-1;i>=0;i--)events.put(saved.get(i));
        return new JSONObject().put("schema",1).put("events",events);
    }
    private FaultAlerts(){}
}
