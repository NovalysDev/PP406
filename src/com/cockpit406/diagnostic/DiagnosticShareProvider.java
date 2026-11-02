package com.cockpit406.diagnostic;

import android.content.*;
import android.content.pm.*;
import android.database.*;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.security.MessageDigest;

/** Read-only contract for the user's installed 406 Coupé companion. No diagnostic commands. */
public final class DiagnosticShareProvider extends ContentProvider {
    private static final String AUTHORITY="com.cockpit406.diagnostic.readings";
    private static final String CLIENT="com.novalysapps.coupe406club";
    private static final String CERT="fd33c06139bcbd2028f3ff7f9ef264048c47eb6b1630e58e7234198e0cac8b74";
    @Override public boolean onCreate(){return true;}
    private void authorize(){
        int uid=Binder.getCallingUid();if(uid==android.os.Process.myUid())return;
        if(!CLIENT.equals(getCallingPackage()))throw new SecurityException("Accès réservé à l’application 406 Coupé");
        try{
            PackageManager manager=getContext().getPackageManager();
            PackageInfo info=manager.getPackageInfo(CLIENT,PackageManager.GET_SIGNATURES);
            if(info.applicationInfo.uid!=uid||info.signatures==null||info.signatures.length!=1)throw new SecurityException("Application non reconnue");
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(info.signatures[0].toByteArray());
            if(!CERT.equals(VciUsbProtocol.hex(hash)))throw new SecurityException("Signature 406 Coupé non reconnue");
        }catch(SecurityException e){throw e;}catch(Exception e){throw new SecurityException("Vérification de l’application impossible",e);}
    }
    private static void route(Uri uri){if(!AUTHORITY.equals(uri.getAuthority())||!("/v1/snapshot".equals(uri.getPath())||"/v1/faults".equals(uri.getPath())||"/v1/alerts".equals(uri.getPath()))||uri.getQuery()!=null)throw new IllegalArgumentException("Adresse de lecture inconnue");}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        authorize();route(uri);
        if((projection!=null&&(projection.length!=1||!"json".equals(projection[0])))||selection!=null||args!=null||sort!=null)throw new IllegalArgumentException("Ce contrat retourne uniquement une colonne JSON");
        try{
            if("/v1/faults".equals(uri.getPath())){
                MatrixCursor cursor=new MatrixCursor(new String[]{"json"});cursor.addRow(new Object[]{FaultExport.snapshot(getContext()).toString()});cursor.setNotificationUri(getContext().getContentResolver(),uri);return cursor;
            }
            if("/v1/alerts".equals(uri.getPath())){
                MatrixCursor cursor=new MatrixCursor(new String[]{"json"});cursor.addRow(new Object[]{FaultAlerts.snapshot(getContext()).toString()});cursor.setNotificationUri(getContext().getContentResolver(),uri);return cursor;
            }
            long now=SystemClock.elapsedRealtime();DiagnosticDataHub.Snapshot snapshot=DiagnosticDataHub.snapshot();
            JSONObject json=new JSONObject();json.put("schema",1);json.put("app_version",BuildInfo.VERSION);json.put("captured_elapsed_ms",now);
            json.put("vehicle",snapshot.vehicle);json.put("ecu",snapshot.ecu);json.put("ecu_reference",snapshot.reference);
            json.put("live",snapshot.live(now));json.put("note",snapshot.note);JSONArray readings=new JSONArray();
            for(DiagnosticDataHub.Signal signal:snapshot.signals){JSONObject value=new JSONObject();value.put("key",signal.key);value.put("label",signal.label);value.put("unit",signal.unit);
                value.put("value",signal.value);value.put("age_ms",Math.max(0,now-signal.elapsed));value.put("fresh",signal.fresh(now,snapshot.reading));readings.put(value);}
            json.put("readings",readings);MatrixCursor cursor=new MatrixCursor(new String[]{"json"});cursor.addRow(new Object[]{json.toString()});return cursor;
        }catch(JSONException e){throw new IllegalStateException("Instantané indisponible",e);}
    }
    @Override public String getType(Uri uri){route(uri);return "vnd.android.cursor.item/vnd.pp406."+("/v1/faults".equals(uri.getPath())?"faults":"/v1/alerts".equals(uri.getPath())?"alerts":"snapshot")+"+json";}
    @Override public Uri insert(Uri uri,ContentValues values){throw new SecurityException("Lecture seule");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new SecurityException("Lecture seule");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new SecurityException("Lecture seule");}
    @Override public Bundle call(String method,String arg,Bundle extras){throw new SecurityException("Aucune commande exposée");}
}
