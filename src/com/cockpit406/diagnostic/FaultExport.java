package com.cockpit406.diagnostic;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.nio.charset.StandardCharsets;

/** Persistent real observations, separate from the last scan's display. No diagnostic requests. */
public final class FaultExport {
    static final Uri URI=Uri.parse("content://com.cockpit406.diagnostic.readings/v1/faults");
    private static final String PREF="fault_export",KEY="events";
    private static final int MAX_BYTES=500*1024,MAX_EVENTS=50;
    public static JSONArray eventsFor(FaultReport report)throws JSONException{
        JSONArray events=new JSONArray();
        if(report.carId.isEmpty()||report.observationId==null||report.observationId.isEmpty())return events;
        for(int i=0;i<report.entries.size();i++){
            FaultReport.Entry entry=report.entries.get(i);
            // Only entries decoded from an actual ECU response; notes and unknown raw replies are excluded.
            JSONObject event=new JSONObject();
            event.put("sourceEventId","pp406:"+report.observationId+":"+report.module+":"+i+":"+entry.code);
            event.put("carId",report.carId);event.put("code",entry.code);event.put("detectedAt",report.readAt/1000);
            event.put("ecu",report.title);event.put("label",entry.preview());
            event.put("details",report.profile+"\n"+entry.description+(entry.context.isEmpty()?"":"\n"+entry.context));events.put(event);
        }
        return events;
    }
    public static synchronized void record(Context context,FaultReport report)throws JSONException{
        JSONArray incoming=eventsFor(report);if(incoming.length()==0)return;
        JSONArray existing=new JSONArray(context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"[]"));
        java.util.LinkedHashMap<String,JSONObject> rows=new java.util.LinkedHashMap<>();
        for(int i=0;i<existing.length();i++){JSONObject row=existing.getJSONObject(i);rows.put(row.getString("sourceEventId"),row);}
        for(int i=0;i<incoming.length();i++){JSONObject row=incoming.getJSONObject(i);rows.put(row.getString("sourceEventId"),row);}
        java.util.List<JSONObject> ordered=new java.util.ArrayList<>(rows.values());JSONArray bounded=new JSONArray();int bytes=128;
        // The Club imports at most 50 events per lot. Export the most recent ones first.
        for(int i=ordered.size()-1;i>=0&&bounded.length()<MAX_EVENTS;i--){JSONObject event=ordered.get(i);int size=event.toString().getBytes(StandardCharsets.UTF_8).length+1;if(bytes+size>MAX_BYTES)continue;bounded.put(event);bytes+=size;}
        // Store chronological order so repeated context updates do not reverse the export order.
        JSONArray saved=new JSONArray();for(int i=bounded.length()-1;i>=0;i--)saved.put(bounded.get(i));
        if(!context.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(KEY,saved.toString()).commit())throw new IllegalStateException("Enregistrement des observations impossible");
        context.getContentResolver().notifyChange(URI,null);
    }
    public static synchronized JSONObject snapshot(Context context)throws JSONException{
        JSONArray saved=new JSONArray(context.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"[]")),events=new JSONArray();
        for(int i=saved.length()-1;i>=0;i--)events.put(saved.get(i));
        return new JSONObject().put("schema",1).put("events",events);
    }
    private FaultExport(){}
}
