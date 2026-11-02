package com.cockpit406.diagnostic;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.*;
import android.os.*;

/** Foreground GPS-only speed, no coordinates stored and no USB or network operations. */
public final class AndroidSpeedReference implements LocationListener {
    private final Context context;
    private final LocationManager manager;
    private volatile SpeedReference.Fix latest;
    private volatile String state="Comparaison GPS désactivée";
    private boolean listening;
    public AndroidSpeedReference(Context context){this.context=context;manager=(LocationManager)context.getSystemService(Context.LOCATION_SERVICE);}
    public boolean permitted(){return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    public String status(){
        SpeedReference.Fix fix=latest;long now=SystemClock.elapsedRealtime();
        return fix!=null?(fix.usable(now)?String.format(java.util.Locale.FRANCE,"GPS %.1f km/h · âge %d ms · précision de position %.0f m",fix.kmh,now-fix.elapsedMs,fix.accuracyMeters):"GPS absent, ancien ou de précision insuffisante · aucun point de comparaison enregistré"):state;
    }
    public SpeedReference.Fix snapshot(long now){SpeedReference.Fix fix=latest;return fix!=null&&fix.usable(now)?fix:null;}
    public void start(){
        if(listening)return;
        if(!permitted()){state="Autorisation de localisation précise non accordée";return;}
        try{
            if(manager==null||!manager.getAllProviders().contains(LocationManager.GPS_PROVIDER)){state="Aucun fournisseur GPS sur cette tablette";return;}
            if(!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)){state="GPS désactivé dans les paramètres Android";return;}
            latest=null;state="GPS actif · attente d’une vitesse récente exploitable";
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this,Looper.getMainLooper());listening=true;
        }catch(SecurityException|IllegalArgumentException e){state="GPS indisponible : "+e.getMessage();}
    }
    public void stop(){
        if(manager!=null&&listening)try{manager.removeUpdates(this);}catch(SecurityException ignored){}
        listening=false;latest=null;state="Comparaison GPS arrêtée";
    }
    @Override public void onLocationChanged(Location location){
        if(!listening)return;
        double speedAccuracy=Build.VERSION.SDK_INT>=26&&location.hasSpeedAccuracy()?location.getSpeedAccuracyMetersPerSecond():Double.NaN;
        SpeedReference.Fix fix=new SpeedReference.Fix(location.getSpeed()*3.6,location.getAccuracy(),speedAccuracy,location.getElapsedRealtimeNanos()/1000000L,
            location.hasSpeed(),location.hasAccuracy(),LocationManager.GPS_PROVIDER.equals(location.getProvider()),location.isFromMockProvider());
        latest=fix;state=fix.usable(SystemClock.elapsedRealtime())?"Vitesse GPS reçue":"Vitesse GPS absente, ancienne ou précision insuffisante · aucun point de comparaison enregistré";
    }
    @Override public void onProviderDisabled(String provider){if(LocationManager.GPS_PROVIDER.equals(provider)){latest=null;state="GPS désactivé";}}
    @Override public void onProviderEnabled(String provider){if(LocationManager.GPS_PROVIDER.equals(provider))state="GPS actif · attente de mesures";}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}
}
