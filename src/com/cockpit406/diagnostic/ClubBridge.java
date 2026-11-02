package com.cockpit406.diagnostic;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import org.json.*;
import java.security.MessageDigest;
import java.util.Locale;

/** The companion publishes metadata only. This bridge never opens USB or downloads a PDF. */
public final class ClubBridge {
    static final String AUTHORITY="com.novalysapps.coupe406club.pp406";
    static final String PACKAGE="com.novalysapps.coupe406club";
    static final String CERT="fd33c06139bcbd2028f3ff7f9ef264048c47eb6b1630e58e7234198e0cac8b74";
    public static final class Catalog {
        public final JSONArray vehicles,documents;
        Catalog(JSONArray vehicles,JSONArray documents){this.vehicles=vehicles;this.documents=documents;}
        public JSONObject car(String carId){
            if(carId==null||carId.isEmpty())return null;
            for(int i=0;i<vehicles.length();i++){JSONObject car=vehicles.optJSONObject(i);if(car!=null&&carId.equals(car.optString("carId")))return car;}
            return null;
        }
        public JSONObject uniqueVin(String vin){
            vin=normalizeVin(vin);if(!vin.matches("[A-HJ-NPR-Z0-9]{17}"))return null;
            JSONObject found=null;
            for(int i=0;i<vehicles.length();i++){JSONObject car=vehicles.optJSONObject(i);if(car!=null&&!car.optString("carId").isEmpty()&&vin.equals(normalizeVin(car.optString("vin")))){if(found!=null)return null;found=car;}}
            return found;
        }
    }
    public static String normalizeVin(String vin){return vin==null?"":vin.replaceAll("\\s","").toUpperCase(Locale.ROOT);}
    public static void verify(Context context)throws Exception{
        PackageInfo info=context.getPackageManager().getPackageInfo(PACKAGE,PackageManager.GET_SIGNATURES);
        if(info.signatures==null||info.signatures.length!=1||!CERT.equals(VciUsbProtocol.hex(MessageDigest.getInstance("SHA-256").digest(info.signatures[0].toByteArray()))))throw new SecurityException("Signature du Club non reconnue");
    }
    private static JSONObject query(Context context,String path)throws Exception{
        try(Cursor cursor=context.getContentResolver().query(Uri.parse("content://"+AUTHORITY+"/v1/"+path),new String[]{"json"},null,null,null)){
            if(cursor==null||!cursor.moveToFirst())return new JSONObject().put("schema",1);
            String text=cursor.getString(cursor.getColumnIndexOrThrow("json"));
            if(text==null||text.length()>4*1024*1024)throw new IllegalArgumentException("Catalogue Club trop volumineux ou vide");
            JSONObject json=new JSONObject(text);if(json.optInt("schema")!=1)throw new IllegalArgumentException("Version du catalogue Club inconnue");return json;
        }
    }
    public static Catalog read(Context context)throws Exception{
        verify(context);JSONObject garage=query(context,"garage"),documents=query(context,"documents");
        JSONArray cars=garage.optJSONArray("vehicles"),docs=documents.optJSONArray("documents");
        return new Catalog(cars==null?new JSONArray():cars,docs==null?new JSONArray():docs);
    }
    /** Garage alone is enough for startup artwork; no document catalogue or network access. */
    public static JSONArray readGarage(Context context)throws Exception{
        verify(context);
        JSONArray cars=query(context,"garage").optJSONArray("vehicles");
        return cars==null?new JSONArray():cars;
    }
    private ClubBridge(){}
}
