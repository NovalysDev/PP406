package com.cockpit406.diagnostic;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.UUID;

/** Short, read-only Lexia check before the normal PP406 screen. */
public final class StartupActivity extends Activity {
    private static final int BG=Color.rgb(9,16,20), PANEL=Color.rgb(17,29,35);
    private static final int WHITE=Color.rgb(241,247,246), MUTED=Color.rgb(158,180,187);
    private static final int LIME=Color.rgb(216,255,25), AMBER=Color.rgb(255,188,82);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final String permissionAction="com.cockpit406.diagnostic.STARTUP_USB."+UUID.randomUUID();
    private UsbManager manager;
    private TextView status,detail;
    private ImageView carArtwork;
    private ProgressBar progress,connectionBar;
    private int generation;
    private boolean destroyed,opened,permissionPending,probing,pendingSearch,resumed,restartWhenResumed;
    private Uri garageLink;
    private Runnable autoOpen,retry;
    private ConnectionTimeline connectionTimeline;
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            String action=intent.getAction();
            if(permissionAction.equals(action)){
                permissionPending=false;
                UsbDevice device=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(device!=null&&manager.hasPermission(device))search();
                else show("Accès USB refusé","Rebranche la LEXIA et autorise PP406 à utiliser la sonde.",AMBER,false);
            }else if(UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)||UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)){
                UsbDevice device=intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if(device==null||AndroidLexiaUsb.matches(device))search();
            }
        }
    };

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        rememberGarageIntent(getIntent());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN);
        manager=(UsbManager)getSystemService(USB_SERVICE);
        build();
        RemoteUpdatesService.startIfEnabled(this);
        new Thread(this::loadDailyArtwork,"PP406-daily-color").start();
        IntentFilter filter=new IntentFilter(permissionAction);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,filter,Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver,filter);
        handler.post(this::search);
    }

    private void rememberGarageIntent(Intent intent){
        Uri uri=intent==null?null:intent.getData();
        if(uri!=null&&"pp406".equals(uri.getScheme())&&"diagnostic".equals(uri.getHost()))garageLink=uri;
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);rememberGarageIntent(intent);
        if(intent!=null&&intent.getData()!=null)setIntent(intent);
    }
    @Override protected void onResume(){
        super.onResume();resumed=true;
        if(restartWhenResumed){restartWhenResumed=false;handler.post(this::search);}
    }
    @Override protected void onPause(){
        resumed=false;
        if(!opened&&!destroyed){restartWhenResumed=true;cancelAutoOpen();cancelRetry();}
        super.onPause();
    }

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private GradientDrawable surface(int fill,int stroke,int radius){
        GradientDrawable drawable=new GradientDrawable();drawable.setColor(fill);drawable.setCornerRadius(dp(radius));
        if(stroke!=0)drawable.setStroke(dp(1),stroke);return drawable;
    }
    private TextView label(String value,int size,int color,boolean bold){
        TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(color);
        if(bold)view.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return view;
    }
    private void build(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        root.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        int widthDp=Math.round(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density);
        int heightDp=Math.round(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density);
        boolean wide=widthDp>=730&&heightDp>=450;
        scroll.setPadding(dp(20),dp(10),dp(20),dp(wide?96:120));
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(wide?LinearLayout.HORIZONTAL:LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        scroll.addView(content,new ScrollView.LayoutParams(-1,-1));
        FrameLayout artworkFrame=new FrameLayout(this);
        int artworkSize=wide?Math.min(540,heightDp-90):Math.min(400,widthDp-40);
        LinearLayout.LayoutParams artworkParams=wide
            ?new LinearLayout.LayoutParams(0,dp(artworkSize),1.15f)
            :new LinearLayout.LayoutParams(-1,dp(artworkSize));
        content.addView(artworkFrame,artworkParams);
        ImageView icon=new ImageView(this);carArtwork=icon;
        icon.setContentDescription("Peugeot 406 Coupé avec une interface Lexia");
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setBackground(surface(BG,0,18));icon.setClipToOutline(true);
        artworkFrame.addView(icon,new FrameLayout.LayoutParams(-1,-1));
        icon.setAlpha(0f);icon.setScaleX(.96f);icon.setScaleY(.96f);
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER_VERTICAL);
        if(wide)right.setPadding(dp(24),0,0,0);
        content.addView(right,wide?new LinearLayout.LayoutParams(0,-2,1f):new LinearLayout.LayoutParams(-1,-2));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
        right.addView(copy,new LinearLayout.LayoutParams(-1,-2));
        TextView eyebrow=label("406 COUPÉ  /  DIAGNOSTIC",wide?14:12,LIME,true);
        copy.addView(eyebrow);TextView title=label("PP406",wide?56:42,WHITE,true);
        copy.addView(title);
        TextView subtitle=label("Connexion à votre sonde Lexia",wide?18:15,MUTED,false);
        copy.addView(subtitle);
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(19),dp(18),dp(19),dp(18));
        panel.setBackground(surface(PANEL,Color.rgb(45,65,72),20));
        LinearLayout.LayoutParams panelParams=new LinearLayout.LayoutParams(-1,-2);panelParams.topMargin=dp(20);
        right.addView(panel,panelParams);
        LinearLayout headline=new LinearLayout(this);headline.setOrientation(LinearLayout.HORIZONTAL);headline.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(headline,new LinearLayout.LayoutParams(-1,-2));
        progress=new ProgressBar(this);progress.setIndeterminate(true);
        if(Build.VERSION.SDK_INT>=21)progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(LIME));
        headline.addView(progress,new LinearLayout.LayoutParams(dp(24),dp(24)));
        status=label("Recherche de la LEXIA…",wide?25:20,WHITE,true);
        LinearLayout.LayoutParams s=new LinearLayout.LayoutParams(0,-2,1);s.leftMargin=dp(14);headline.addView(status,s);
        detail=label("Vérification USB en cours.",wide?16:14,MUTED,false);detail.setPadding(dp(38),dp(9),0,0);
        panel.addView(detail,new LinearLayout.LayoutParams(-1,-2));
        connectionBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        connectionBar.setMax(1000);connectionBar.setProgress(0);connectionBar.setVisibility(View.GONE);
        connectionBar.setProgressTintList(android.content.res.ColorStateList.valueOf(LIME));
        connectionBar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(45,65,72)));
        LinearLayout.LayoutParams connectionParams=new LinearLayout.LayoutParams(-1,dp(8));connectionParams.topMargin=dp(16);
        panel.addView(connectionBar,connectionParams);
        TextView version=label("Version PP406 " + BuildInfo.VERSION,14,MUTED,false);
        version.setPadding(0,dp(12),0,0);panel.addView(version);
        Button update=new Button(this);update.setAllCaps(false);update.setText("Mises à jour · vérifier / installer");
        update.setOnClickListener(v->AppUpdates.show(this,()->probing||DiagnosticLease.held()||FaultMonitorService.active()));
        panel.addView(update,new LinearLayout.LayoutParams(-1,dp(50)));
        TextView foot=label("Contrôle de la sonde uniquement · aucune commande envoyée à la voiture",12,MUTED,false);
        foot.setGravity(Gravity.CENTER);LinearLayout.LayoutParams footParams=new LinearLayout.LayoutParams(-1,-2);footParams.topMargin=dp(18);
        right.addView(foot,footParams);
        LinearLayout brandFooter=new LinearLayout(this);brandFooter.setOrientation(LinearLayout.VERTICAL);
        brandFooter.setGravity(Gravity.CENTER);brandFooter.setBackgroundColor(BG);
        brandFooter.setPadding(dp(wide?128:80),0,dp(wide?128:80),0);
        View divider=new View(this);divider.setBackgroundColor(Color.rgb(73,93,98));
        LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(dp(96),dp(1));
        dividerParams.bottomMargin=dp(8);brandFooter.addView(divider,dividerParams);
        TextView credit=label("Une application conçue et développée par",wide?18:15,MUTED,false);
        credit.setGravity(Gravity.CENTER);brandFooter.addView(credit,new LinearLayout.LayoutParams(-1,-2));
        TextView brand=label("NOVALYS APPS",wide?30:24,WHITE,true);
        brand.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
        brand.setGravity(Gravity.CENTER);brand.setLetterSpacing(.10f);
        brand.setPadding(dp(12),dp(2),dp(12),0);brandFooter.addView(brand,new LinearLayout.LayoutParams(-1,-2));
        root.addView(brandFooter,new FrameLayout.LayoutParams(-1,dp(wide?94:118),Gravity.BOTTOM));
        ImageView brandLogo=new ImageView(this);
        brandLogo.setImageResource(R.drawable.novalys_apps_logo);
        brandLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        brandLogo.setContentDescription("Logo NOVALYS APPS");
        FrameLayout.LayoutParams logoParams=new FrameLayout.LayoutParams(dp(wide?112:76),dp(wide?112:76),Gravity.BOTTOM|Gravity.RIGHT);
        logoParams.rightMargin=dp(wide?12:6);logoParams.bottomMargin=dp(wide?4:20);
        root.addView(brandLogo,logoParams);
        setContentView(root);
    }

    private void loadDailyArtwork(){
        DailyCarArtwork.Result artwork=DailyCarArtwork.forSignedInDaily(this);
        handler.post(()->{
            if(destroyed||opened)return;
            carArtwork.setImageBitmap(artwork.image);
            if(!artwork.label.isEmpty())carArtwork.setContentDescription("Peugeot 406 Coupé "+artwork.label+" avec une interface Lexia");
            carArtwork.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(650).start();
        });
    }

    private void show(String title,String description,int color,boolean scanning){
        if(destroyed||opened)return;
        status.setText(title);status.setTextColor(color);detail.setText(description);
        progress.setVisibility(scanning?View.VISIBLE:View.INVISIBLE);
    }
    private void cancelAutoOpen(){
        if(autoOpen!=null){handler.removeCallbacks(autoOpen);autoOpen=null;}
        connectionTimeline=null;
        if(connectionBar!=null){connectionBar.setProgress(0);connectionBar.setVisibility(View.GONE);}
    }
    private void cancelRetry(){if(retry!=null){handler.removeCallbacks(retry);retry=null;}}
    private void retryLater(long delayMs){cancelRetry();retry=this::search;handler.postDelayed(retry,delayMs);}
    private void search(){
        if(destroyed||opened)return;
        if(!resumed){restartWhenResumed=true;return;}
        if(probing){pendingSearch=true;cancelAutoOpen();show("Recherche de la LEXIA…","Vérification de la connexion USB.",WHITE,true);return;}
        pendingSearch=false;
        int attempt=++generation;cancelAutoOpen();cancelRetry();
        show("Recherche de la LEXIA…","Branche la sonde : la connexion se fera automatiquement.",WHITE,true);
        if(manager==null){show("USB indisponible","Cette tablette ne fournit pas le mode hôte USB.",AMBER,false);return;}
        UsbDevice found=null;
        for(UsbDevice device:manager.getDeviceList().values())if(AndroidLexiaUsb.matches(device)){
            if(found!=null){show("Plusieurs LEXIA détectées","Ne laisse branchée qu’une seule sonde.",AMBER,false);retryLater(3000);return;}
            found=device;
        }
        if(found==null){show("En attente de la LEXIA…","Branche la sonde à la tablette en USB ; elle sera détectée automatiquement.",WHITE,true);retryLater(2000);return;}
        if(!manager.hasPermission(found)){
            if(!permissionPending){
                permissionPending=true;
                Intent intent=new Intent(permissionAction).setPackage(getPackageName());
                int flags=PendingIntent.FLAG_CANCEL_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
                manager.requestPermission(found,PendingIntent.getBroadcast(this,1,intent,flags));
            }
            show("LEXIA détectée","Autorise l’accès USB pour lire la version du firmware.",AMBER,true);
            return;
        }
        if(FaultMonitorService.active()){
            show("LEXIA détectée · surveillance en cours","Connexion à PP406 en cours…",LIME,false);
            connectWithFiveSecondBar();return;
        }
        if(DiagnosticLease.held()){
            show("Fermeture du diagnostic précédent…","La connexion reprend automatiquement.",WHITE,true);
            retryLater(500);return;
        }
        UsbDevice device=found;
        probing=true;
        show("Détection du firmware…","Lecture de la sonde LEXIA en cours.",WHITE,false);
        startFirmwareBar(attempt);
        new Thread(()->readVersion(attempt,device),"PP406-startup-lexia").start();
    }
    private void readVersion(int attempt,UsbDevice device){
        String title,description,firmware=null;int color;boolean compatible=false,incompatible=false;
        DiagnosticLease.Token lease=DiagnosticLease.acquire();
        if(lease==null){title="LEXIA déjà utilisée";description="Une session de diagnostic est en cours.";color=AMBER;}
        else try(DiagnosticLease.Token ignored=lease;AndroidLexiaUsb usb=new AndroidLexiaUsb(manager,device,(direction,data)->{})){
            VciUsbProtocol protocol=new VciUsbProtocol(usb,15000,(elapsed,limit)->{});
            LexiaStartupProbe.Result result=LexiaStartupProbe.read(protocol::exchange);
            compatible=result.compatible;
            if(compatible){firmware=result.firmware;title="";description="";color=LIME;}
            else{incompatible=true;title="LEXIA détectée · firmware incompatible";
                description=result.incompatibilityDescription();color=AMBER;}
        }catch(Exception error){title="LEXIA détectée · lecture impossible";
            description="Vérifie la liaison USB. Nouvelle tentative automatique.";color=AMBER;}
        final String resultTitle=title,resultDescription=description,resultFirmware=firmware;final int resultColor=color;final boolean resultCompatible=compatible,resultIncompatible=incompatible;
        handler.post(()->{
            probing=false;
            if(destroyed||opened)return;
            if(!resumed){restartWhenResumed=true;return;}
            if(pendingSearch){pendingSearch=false;search();return;}
            if(attempt!=generation)return;
            if(resultCompatible){if(connectionTimeline!=null)connectionTimeline.verified(resultFirmware);}
            else {cancelAutoOpen();show(resultTitle,resultDescription,resultColor,false);if(!resultIncompatible)retryLater(5000);}
        });
    }
    private void startFirmwareBar(int attempt){
        connectionBar.setProgress(0);connectionBar.setVisibility(View.VISIBLE);
        connectionTimeline=new ConnectionTimeline(SystemClock.uptimeMillis());
        autoOpen=new Runnable(){@Override public void run(){
            if(destroyed||opened||!resumed||attempt!=generation||connectionTimeline==null)return;
            ConnectionTimeline.Frame frame=connectionTimeline.frame(SystemClock.uptimeMillis());
            connectionBar.setProgress(frame.progress);
            String headline=frame.stage==ConnectionTimeline.Stage.DETECTING?"Détection du firmware…":
                frame.stage==ConnectionTimeline.Stage.FIRMWARE?"Firmware "+connectionTimeline.firmware()+" détecté":"Connexion OK";
            String explanation=frame.stage==ConnectionTimeline.Stage.DETECTING?"Lecture de la sonde LEXIA en cours.":
                frame.stage==ConnectionTimeline.Stage.FIRMWARE?"Vérification de la connexion…":"Ouverture de PP406…";
            if(!headline.contentEquals(status.getText()))status.setText(headline);
            if(!explanation.contentEquals(detail.getText()))detail.setText(explanation);
            status.setTextColor(frame.stage==ConnectionTimeline.Stage.DETECTING?WHITE:LIME);
            if(frame.complete){autoOpen=null;openDiagnostic();return;}
            handler.postDelayed(this,16);
        }};
        handler.post(autoOpen);
    }
    private void connectWithFiveSecondBar(){
        final int attempt=generation;
        connectionBar.setProgress(0);connectionBar.setVisibility(View.VISIBLE);
        autoOpen=new Runnable(){
            long started=-1;
            @Override public void run(){
                if(destroyed||opened||!resumed||attempt!=generation)return;
                long now=SystemClock.uptimeMillis();
                if(started<0)started=now;
                long elapsed=now-started;
                connectionBar.setProgress((int)(Math.min(5000,elapsed)*1000/5000));
                if(elapsed>=5000){autoOpen=null;openDiagnostic();return;}
                handler.postDelayed(this,Math.min(16,5000-elapsed));
            }
        };
        handler.post(autoOpen);
    }
    private void openDiagnostic(){
        if(destroyed||opened)return;opened=true;generation++;cancelAutoOpen();
        startActivity(new Intent(this,UsbDiagnosticActivity.class).setData(garageLink).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));finish();
    }
    @Override protected void onDestroy(){
        destroyed=true;generation++;cancelAutoOpen();cancelRetry();handler.removeCallbacksAndMessages(null);
        try{unregisterReceiver(receiver);}catch(IllegalArgumentException ignored){}
        super.onDestroy();
    }
}
