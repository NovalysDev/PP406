package com.cockpit406.diagnostic;
import android.app.*;
import android.content.*;
import android.os.*;

/** In-activity warning: no activity switch, dialog, sound or diagnostic interruption. */
public final class TabletBatteryWarning extends BroadcastReceiver {
    private final Activity activity;private final BatteryOverlay overlay;private final Runnable checkpoint;
    private final BatteryAlertState state;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable dismiss;
    public TabletBatteryWarning(Activity activity,BatteryOverlay overlay,Runnable checkpoint){
        this.activity=activity;this.overlay=overlay;this.checkpoint=checkpoint;
        dismiss=()->{overlay.hide();try{((NotificationManager)activity.getSystemService(Context.NOTIFICATION_SERVICE)).cancel(40620);}catch(RuntimeException ignored){}};
        state=new BatteryAlertState(activity.getSharedPreferences("tablet_battery",0).getBoolean("warned",false));
        if(Build.VERSION.SDK_INT>=33)activity.registerReceiver(this,new IntentFilter(Intent.ACTION_BATTERY_CHANGED),Context.RECEIVER_NOT_EXPORTED);
        else activity.registerReceiver(this,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }
    @Override public void onReceive(Context context,Intent intent){
        int level=intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=intent.getIntExtra(BatteryManager.EXTRA_SCALE,-1);
        int percent=level<0||scale<=0?-1:Math.round(level*100f/scale);
        boolean charging=intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0;
        boolean alert=state.update(percent,charging);activity.getSharedPreferences("tablet_battery",0).edit().putBoolean("warned",state.warned).apply();
        if(!alert)return;
        checkpoint.run();
        overlay.show(percent);
        handler.removeCallbacks(dismiss);
        handler.postDelayed(dismiss,10000);
        try{
            NotificationManager manager=(NotificationManager)activity.getSystemService(Context.NOTIFICATION_SERVICE);
            String channel="pp406-battery-visual";Notification.Builder builder;
            if(Build.VERSION.SDK_INT>=26){NotificationChannel settings=new NotificationChannel(channel,"Batterie tablette",NotificationManager.IMPORTANCE_LOW);settings.setSound(null,null);manager.createNotificationChannel(settings);builder=new Notification.Builder(activity,channel);}
            else builder=new Notification.Builder(activity);
            manager.notify(40620,builder.setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("PP406 · batterie à "+percent+" %").setContentText("Branche une alimentation. "+TripBackups.status).setAutoCancel(true).build());
        }catch(RuntimeException ignored){}
    }
    public void close(){handler.removeCallbacks(dismiss);overlay.hide();activity.unregisterReceiver(this);}
}
