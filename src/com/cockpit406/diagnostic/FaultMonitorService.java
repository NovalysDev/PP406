package com.cockpit406.diagnostic;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.hardware.usb.*;
import android.os.*;
import org.json.*;
import java.io.*;
import java.util.*;

/** Explicit user-started foreground, read-only monitor. Never restarts after process/USB loss. */
public final class FaultMonitorService extends Service {
    public static final String STOP="com.cockpit406.diagnostic.STOP_MONITOR";
    private static final String CHANNEL="pp406_monitor";
    private static final int NOTICE=40601;
    private static volatile boolean active;
    private static volatile String status="Surveillance arrêtée";
    private volatile boolean stop;
    private volatile MonitorReadSession reader;
    private final Object waiting=new Object();
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean foreground;
    private PowerManager.WakeLock wake;
    public static boolean active(){return active;}
    public static String status(){return status;}
    public static void stop(Context context){if(active)context.startService(new Intent(context,FaultMonitorService.class).setAction(STOP));}
    private final BroadcastReceiver detach=new BroadcastReceiver(){public void onReceive(Context context,Intent intent){UsbDevice device=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);if(device!=null&&AndroidLexiaUsb.matches(device))requestStop();}};
    @Override public void onCreate(){super.onCreate();IntentFilter filter=new IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED);if(Build.VERSION.SDK_INT>=33)registerReceiver(detach,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(detach,filter);}
    @Override public IBinder onBind(Intent intent){return null;}
    private Notification notification(String text,boolean ongoing){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Surveillance diagnostic",NotificationManager.IMPORTANCE_LOW));
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        builder.setSmallIcon(com.cockpit406.diagnostic.R.drawable.ic_diagnostic).setContentTitle("PP406 · surveillance des défauts").setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text)).setContentIntent(open).setOngoing(ongoing).setOnlyAlertOnce(true);
        if(ongoing){PendingIntent end=PendingIntent.getService(this,1,new Intent(this,FaultMonitorService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);builder.addAction(new Notification.Action.Builder(null,"Arrêter",end).build());}
        return builder.build();
    }
    private void update(String text){status=text;main.post(()->{if(active)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,notification(text,true));});}
    private void requestStop(){stop=true;MonitorReadSession current=reader;if(current!=null)current.stop();synchronized(waiting){waiting.notifyAll();}}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(intent==null){stopSelf();return START_NOT_STICKY;}
        if(STOP.equals(intent.getAction())){requestStop();if(!active)stopSelf();return START_NOT_STICKY;}
        if(active)return START_NOT_STICKY;
        final String vin=ClubBridge.normalizeVin(intent.getStringExtra("vin")),carId=intent.getStringExtra("carId");
        if(!VehicleEcuMemory.validVin(vin)||carId==null||carId.isEmpty()){stopSelf();return START_NOT_STICKY;}
        active=true;stop=false;status="Préparation de la surveillance en lecture seule";
        try{
            if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,notification(status,true),ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(NOTICE,notification(status,true));foreground=true;
            new Thread(()->runMonitor(vin,carId),"PP406-fault-monitor").start();
        }catch(RuntimeException e){active=false;status="Surveillance indisponible : "+e.getMessage();stopSelf();}
        return START_NOT_STICKY;
    }
    private byte[] asset(String name)throws IOException{try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] bytes=new byte[4096];int count;while((count=in.read(bytes))!=-1)out.write(bytes,0,count);return out.toByteArray();}}
    private void log(PrintWriter out,String module,String name,byte[] request,byte[] response){try{JSONObject event=new JSONObject().put("event","monitor_command").put("name",name).put("time_ms",System.currentTimeMillis());if(!SensitiveDiagnostics.hideRaw(module))event.put("request",VciUsbProtocol.hex(request)).put("response",VciUsbProtocol.hex(response));out.println(event);out.flush();}catch(JSONException e){throw new IllegalStateException(e);}}
    private void runMonitor(String vin,String carId){
        DiagnosticLease.Token lease=DiagnosticLease.acquire();String ending="Surveillance arrêtée";
        try{
            if(lease==null)throw new IOException("Autre session encore active · réessaie après sa fermeture");
            if(getSharedPreferences("lexia_link",0).getBoolean("resetRequired",false))throw new IOException("Liaison Lexia à rétablir avant surveillance");
            ClubBridge.Catalog catalogue=ClubBridge.read(this);JSONObject car=catalogue.car(carId);
            if(car==null||!vin.equals(ClubBridge.normalizeVin(car.optString("vin"))))throw new IOException("Association VIN / garage non confirmée");
            JSONObject saved=VehicleEcuMemory.load(this,vin),modules=saved.optJSONObject("modules");
            List<String> targets=new ArrayList<>();if(modules!=null){Iterator<String> it=modules.keys();while(it.hasNext()){String module=it.next();JSONObject ecu=modules.getJSONObject(module);if(ecu.optBoolean("monitorable")&&MonitorReadSession.supported(ecu.optString("profile")))targets.add(module);}}
            if(targets.isEmpty())throw new IOException("Premier scan requis · aucun profil surveillable mémorisé");
            UsbManager manager=(UsbManager)getSystemService(USB_SERVICE);UsbDevice device=null;for(UsbDevice candidate:manager.getDeviceList().values())if(AndroidLexiaUsb.matches(candidate)){device=candidate;break;}
            if(device==null||!manager.hasPermission(device))throw new IOException("Lexia absent ou accès USB non accordé");
            wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PP406:fault-monitor");wake.acquire();
            try(SessionJournal journal=SessionJournal.open(getFilesDir(),getCacheDir(),"usb-monitor",message->update(message));AndroidLexiaUsb usb=new AndroidLexiaUsb(manager,device,(direction,bytes)->{})){
                VciUsbProtocol protocol=new VciUsbProtocol(usb,20000,(elapsed,limit)->update("Lecture en cours · Lexia occupé "+elapsed/1000+" s"));
                while(!stop){
                    for(String module:targets){
                        if(stop)break;JSONObject ecu=modules.getJSONObject(module);String profile=ecu.getString("profile");
                        update("Lecture des défauts · "+module);MonitorReadSession current=new MonitorReadSession();reader=current;if(stop)current.stop();
                        try{
                            MonitorReadSession.Result result=current.read(profile,ecu.getString("identity"),body->protocol.exchange(body),this::asset,(name,req,res)->log(journal,module,name,req,res),millis->{try{Thread.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Attente interrompue",e);}});
                            if(stop)break;
                            FaultReport report=new FaultReport(module,module,profile,System.currentTimeMillis(),result.faults,"",carId,UUID.randomUUID().toString());
                            FaultExport.record(this,report);FaultAlerts.record(this,vin,carId,result.identity,report,true);
                        }catch(IOException e){
                            if(protocol.needsRecovery()||!current.mayContinue()){
                                getSharedPreferences("lexia_link",0).edit().putBoolean("resetRequired",true).putString("reason",e.getMessage()).apply();throw e;
                            }
                            if(!stop)update(module+" indisponible · autres calculateurs conservés");
                        }finally{reader=null;}
                    }
                    if(!stop){update("Surveillance active · prochain tour dans 60 s · export Club disponible");synchronized(waiting){if(!stop)waiting.wait(60000);}}
                }
            }
        }catch(Exception e){ending="Surveillance interrompue : "+(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
        finally{
            reader=null;if(wake!=null&&wake.isHeld())wake.release();if(lease!=null)lease.close();status=ending;final String text=ending;
            main.post(()->{if(foreground){stopForeground(STOP_FOREGROUND_REMOVE);foreground=false;}((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,notification(text,false));active=false;stopSelf();});
        }
    }
    @Override public void onDestroy(){requestStop();try{unregisterReceiver(detach);}catch(IllegalArgumentException ignored){}super.onDestroy();}
}
