package com.cockpit406.diagnostic;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;
import java.io.*;

/** Explicitly enabled by the tablet owner. No USB requests and no diagnostic interruption. */
public final class RemoteUpdatesService extends Service {
    private static final String PREF="remote_updates",CHANNEL="pp406_updates";
    private static final int NOTICE=40602;
    private volatile boolean stopped;
    private volatile boolean installing;
    private Thread worker;
    static volatile boolean visible;
    public static boolean enabled(Context context){return context.getSharedPreferences(PREF,0).getBoolean("enabled",false);}
    public static String status(Context context){return context.getSharedPreferences(PREF,0).getString("status","Mises à jour automatiques désactivées");}
    public static void startIfEnabled(Context context){
        if(!enabled(context))return;
        try{Intent intent=new Intent(context,RemoteUpdatesService.class);if(Build.VERSION.SDK_INT>=26)context.startForegroundService(intent);else context.startService(intent);}catch(RuntimeException e){context.getSharedPreferences(PREF,0).edit().putString("status","Ouvre PP406 pour reprendre les mises à jour automatiques").apply();}
    }
    public static void disable(Context context){context.getSharedPreferences(PREF,0).edit().putBoolean("enabled",false).putString("status","Mises à jour automatiques désactivées").apply();context.stopService(new Intent(context,RemoteUpdatesService.class));}
    public static void enable(Activity activity,java.util.function.Consumer<String> done){
        if(DiagnosticLease.held()||FaultMonitorService.active()){done.accept("Arrête le diagnostic avant d’autoriser l’installation automatique");return;}
        new Thread(()->{
            String message;
            try{
                RootUpdateInstaller.authorize();
                // Own-package exemption permits checks while this Android 7 tablet is idle.
                try{RootUpdateInstaller.root("dumpsys deviceidle whitelist +com.cockpit406.diagnostic",10);}catch(Exception ignored){}
                activity.getSharedPreferences(PREF,0).edit().putBoolean("enabled",true).remove("failed_code").commit();
                startIfEnabled(activity);message="Installation automatique autorisée · tablette connectée à Internet";
            }catch(Exception e){message=e.getMessage();}
            final String text=message;activity.runOnUiThread(()->done.accept(text));
        },"PP406-update-authorize").start();
    }
    @Override public IBinder onBind(Intent intent){return null;}
    private Notification notice(String text){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Mises à jour PP406",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,2,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("PP406 · mises à jour automatiques").setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build();
    }
    private void update(String text){getSharedPreferences(PREF,0).edit().putString("status",text).apply();((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(NOTICE,notice(text));}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        if(!enabled(this)){stopSelf();return START_NOT_STICKY;}
        if(worker!=null)return START_STICKY;
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTICE,notice("Connexion au serveur Novalys Apps"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);else startForeground(NOTICE,notice("Connexion au serveur Novalys Apps"));
        worker=new Thread(this::watch,"PP406-remote-updates");worker.start();return START_STICKY;
    }
    private void watch(){
        while(!stopped&&enabled(this)){
            try{
                int installed=getPackageManager().getPackageInfo(AppUpdates.PACKAGE,0).versionCode;
                if(getSharedPreferences(PREF,0).getInt("pending_code",0)==installed){getSharedPreferences(PREF,0).edit().remove("pending_code").putString("last_installed",BuildInfo.VERSION).commit();}
                AppUpdates.Release release=AppUpdates.check(AppUpdates.DEFAULT_SOURCE);
                if(release.code>installed&&release.code!=getSharedPreferences(PREF,0).getInt("failed_code",0)){
                    update("Téléchargement de PP406 "+release.version);
                    synchronized(AppUpdates.class){
                        File apk=AppUpdates.download(this,release,(current,total)->{});
                        DiagnosticLease.Token token=null;
                        try{
                            while(!stopped&&enabled(this)&&(FaultMonitorService.active()||(token=DiagnosticLease.acquire())==null)){
                                update("PP406 "+release.version+" prête · installation après l’arrêt du diagnostic");Thread.sleep(1000);
                            }
                            if(stopped||!enabled(this))return;
                            // Ownership excludes a new USB session between this check and replacement.
                            if(token==null)throw new IOException("Diagnostic encore actif");
                            update("Installation de PP406 "+release.version);
                            getSharedPreferences(PREF,0).edit().putInt("pending_code",release.code).commit();
                            installing=true;
                            File result=RootUpdateInstaller.schedule(this,release,apk,visible);
                            long deadline=SystemClock.elapsedRealtime()+90000;
                            boolean completed=false;
                            // Once the privileged installer is armed, retain ownership even if
                            // the watcher is disabled. No new diagnostic may race replacement.
                            while(SystemClock.elapsedRealtime()<deadline){
                                Thread.sleep(1000);String reply;
                                try(FileInputStream in=new FileInputStream(result)){byte[] bytes=new byte[32];int n=in.read(bytes);reply=n<0?"":new String(bytes,0,n,"UTF-8").trim();}
                                if(reply.equals("0")){
                                    getSharedPreferences(PREF,0).edit().remove("failed_code").commit();
                                    update("Installation terminée · relance de PP406");completed=true;break;
                                }
                                if(!reply.equals("pending"))throw new IOException("Android a refusé l’installation automatique · utiliser le menu Mises à jour");
                            }
                            if(!completed)throw new IOException("Installation sans confirmation · utiliser le menu Mises à jour");
                        }catch(Exception e){if(!stopped)getSharedPreferences(PREF,0).edit().putInt("failed_code",release.code).commit();throw e;}
                        finally{installing=false;if(token!=null)token.close();}
                    }
                }else if(release.code>installed)update("Installation de "+release.version+" à reprendre depuis le menu Mises à jour");
                else update("PP406 "+BuildInfo.VERSION+" · à jour · attente des prochaines publications");
            }catch(InterruptedException e){Thread.currentThread().interrupt();break;}
            catch(Exception e){if(!stopped)update("Mise à jour en attente : "+e.getMessage());}
            try{Thread.sleep(30000);}catch(InterruptedException e){Thread.currentThread().interrupt();break;}
        }
        if(!stopped)stopSelf();
    }
    @Override public void onDestroy(){stopped=true;if(worker!=null&&!installing)worker.interrupt();stopForeground(true);super.onDestroy();}
}
