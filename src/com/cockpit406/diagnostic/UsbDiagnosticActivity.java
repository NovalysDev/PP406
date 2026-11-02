package com.cockpit406.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.ProgressBar;
import org.json.JSONObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Real USB readings only. Recorded replay is a separate, explicitly labelled screen. */
public final class UsbDiagnosticActivity extends Activity {
    private static final String PERMISSION = "com.cockpit406.diagnostic.USB_PERMISSION";
    private final String permissionAction=PERMISSION+"."+java.util.UUID.randomUUID();
    private final UsbPermissionFlow usbPermission=new UsbPermissionFlow();
    private static final int BG = Color.rgb(13,18,23), PANEL = Color.rgb(23,31,38), WHITE = Color.rgb(243,246,247);
    private static final int MUTED = Color.rgb(160,177,188), LIME = Color.rgb(214,241,57), AMBER = Color.rgb(255,191,86);
    private static final String[][] SIGNALS = {
        {"rpm", "Régime moteur", "tr/min", "0"}, {"coolant_r_c", "Température LDR", "°C", "0"},
        {"battery_v", "Tension calculateur", "V", "1"}, {"map_mbar", "Pression collecteur", "mbar", "0"},
        {"throttle_deg", "Angle papillon", "°", "1"}, {"air_r_c", "Air d’admission", "°C", "0"},
        {"speed_kmh", "Vitesse véhicule", "km/h", "0"}, {"injection_ms", "Temps d’injection", "ms", "1"},
        {"oxygen_mv", "Sonde à oxygène", "mV", "0"}, {"throttle_mv", "Signal papillon", "mV", "0"},
        {"coolant_s_c", "Température d’eau · champ 2", "°C", "0"}, {"air_s_c", "Air admission · champ 2", "°C", "0"},
        {"idle_duty_pct", "Commande de ralenti", "%", "0"}, {"canister_duty_pct", "Commande canister", "%", "0"},
        {"load_ms", "Charge moteur (libellé relevé)", "ms", "1"}
    };
    private static final String[][] DISPLAY_SIGNALS=MeasurementPages.displayedSignals(SIGNALS);
    private final Handler handler = new Handler();
    private final Map<String, TextView> fields = new LinkedHashMap<>();
    private final Map<String, TextView> valueGuides = new LinkedHashMap<>();
    private final Map<String, View> valueCards = new LinkedHashMap<>();
    private final EngineValueGuide engineGuide = new EngineValueGuide();
    private final Object sharingOwner=new Object();
    private EngineValueGuide.Result engineAssessment = EngineValueGuide.neutral("Contrôle du ralenti non activé");
    private TextView guideSummary;
    private AlertDialog guideDialog;
    private final Map<String, Double> values = new LinkedHashMap<>();
    private final Map<String,Double> formattedValues=new LinkedHashMap<>();
    private long measurementRevision,lastSharedRevision=-1;
    private boolean lastSharedLive;
    private String lastSharedReference;
    private final Map<String,Double> climateMeasurements=new LinkedHashMap<>();
    private final Map<String,Long> climateAges=new LinkedHashMap<>();
    private final Map<String, Long> ages = new LinkedHashMap<>();
    private TextView status, age, deviceInfo, faults, raw, logInfo, faultAction, faultStamp;
    private Button start, stopButton, readValuesButton;
    private FunctionCatalog.Action primaryFunction;
    private FunctionCatalog.Action testAwaitingRead;
    private String selectedFuel="";
    private boolean fuelDialogOpen;
    private volatile FuelRecording liveRecording;
    private AndroidSpeedReference gpsSpeed;
    private boolean pendingOutsideRead;
    private Button reread, erase;
    private final Map<String,FaultReport> faultReports=new LinkedHashMap<>();
    private final List<Button> faultReportButtons=new ArrayList<>();
    private FaultResultsPage faultResultsPage;
    private boolean faultResultsRequested,faultResultsReady;
    private TextView vehicleSupport;
    private String recognizedReference;
    private String recognizedOtherReference,activeModule="motor",pendingModule="motor";
    private String pendingProfile="motor",activeProfile="motor";
    private TextView otherReadingView;
    private String displayedOtherAction;
    private final Map<String,String> otherIdentities=new LinkedHashMap<>(),otherValues=new LinkedHashMap<>(),otherFaults=new LinkedHashMap<>();
    private final Map<String,String> otherOperations=new LinkedHashMap<>();
    private final Map<String,String> readIssues=new LinkedHashMap<>();
    private final Map<String,String> genericIdentities=new LinkedHashMap<>();
    private final Map<String,String> confirmedMp72=new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String,Map<String,Double>> genericMeasurements=new LinkedHashMap<>();
    private ProgressBar inlineProgress;
    private TextView inlineStep;
    private boolean inlineRequested;
    private String inlineModule;
    private final Map<String,Long> otherTimes=new LinkedHashMap<>();
    private final Map<String,Long> otherWallTimes=new LinkedHashMap<>();
    private long motorSampleWallTime;
    private long motorSessionStart=Long.MAX_VALUE;
    private long climateSessionStart=Long.MAX_VALUE,climateSampleWallTime;
    private String outsideReadIssue="";
    private boolean outsidePolling;
    private final List<FunctionRow> functionRows=new ArrayList<>();
    private final Map<String,Button> moduleButtons=new LinkedHashMap<>();
    private final Map<String,String> selectedActions=new LinkedHashMap<>();
    private LinearLayout functionPage;
    private String selectedModule="motor";
    private int selectedTab;
    private int selectedMotorTestSection;
    private static final String[] MOTOR_TEST_SECTIONS={"Injection","Allumage","Autres tests"};
    private static final String[] TAB_NAMES={"Valeurs","Défauts","Tests","Apprentissages","Codage","Cartographie"};
    private static final class FunctionRow {
        final FunctionCatalog.Group group;
        final FunctionCatalog.Action action;
        final Button button;
        final TextView reason;
        FunctionCatalog.Availability last;
        FunctionRow(FunctionCatalog.Group group,FunctionCatalog.Action action,Button button,TextView reason) {
            this.group=group; this.action=action; this.button=button; this.reason=reason;
        }
    }
    private final ViewGroup[] pages = new ViewGroup[3];
    private LinearLayout measurementGrid;
    private boolean needleMode;
    private NeedleDialView rpmDial,speedDial;
    private final DriveChrono chrono=new DriveChrono();
    private Button chronoLapButton;
    private BatteryOverlay batteryOverlay;
    private TabletBatteryWarning batteryWarning;
    private final Map<TextView,Integer> colorTargets=new java.util.IdentityHashMap<>();
    private final Map<TextView,android.animation.ValueAnimator> colorAnimations=new java.util.IdentityHashMap<>();
    private final Runnable backupTicker=new Runnable(){public void run(){TripBackups.kick(UsbDiagnosticActivity.this,liveRecording,null);if(!destroyed)handler.postDelayed(this,30000);}};
    private ClubBridge.Catalog clubCatalog;
    private String clubStatus="Catalogue Club non chargé",selectedCarId="",diagnosticCarId="";
    private String vehicleVin="",vehicleDam="";
    private String diagnosticVin="";
    private JSONObject rememberedEcus=new JSONObject();
    private boolean monitorStartQueued,monitoringShown;
    private boolean clubLoading, garageIntentUnresolved;
    private String requestedClubCarId;

    private final Map<String,Double> highAlerts=new LinkedHashMap<>();
    private final Map<String,Double> lowAlerts=new LinkedHashMap<>();
    private final LambdaSignalGuide lambdaGuide=new LambdaSignalGuide();
    private EngineValueGuide.Result lambdaAssessment=EngineValueGuide.neutral("En attente de mesures lambda");
    private long lastControls;
    private final Button[] tabs = new Button[6];
    private boolean operationPending;
    private final OperationPresentation operationUi=new OperationPresentation();
    private AlertDialog operationDialog;
    private TextView operationStep;
    private TextView operationResult;
    private ScrollView operationResultScroll;
    private String operationFaultResult;
    private ProgressBar operationProgress;
    private String switchProfile,switchModule;
    private boolean switchAutomatic, initialAutoPending=true, fuelPromptAfterConnect;
    private boolean pendingGlobal,queuedGlobal;
    private ScanEquipment scanEquipment=ScanEquipment.searchAll();
    private AlertDialog scanEquipmentDialog;
    private static final int VEHICLE_CHOICE=406;
    private GlobalEcuScan.Pass scanContinuation;
    private volatile GlobalEcuScan.DecisionGate engineChoiceGate;
    private AlertDialog engineChoiceDialog;
    private volatile boolean globalRunning,globalCancel,contactCycleRequired;
    private String globalResults;
    private TextView globalSummary;
    private int globalCompleted;
    private int globalTotal=12;
    private VehicleReadPlans vehiclePlans;
    private VehicleReadPlans.Plan vehiclePlan;
    private LinearLayout moduleRow;
    private TextView vehicleHeading,vehicleMode;
    private volatile EngineReadSession genericSession;
    private final Map<String,String> identifiedProfiles=new LinkedHashMap<>();
    private final DiagnosticNavigation navigation=new DiagnosticNavigation();
    private boolean navigatingBack,backAfterClose,exitAfterClose,returnToStartupAfterClose;
    private volatile String globalCurrentTitle="Recherche des calculateurs";
    private volatile boolean linkResetRequired;
    private UsbManager manager;
    private UsbDevice pending;
    private volatile Mp70Session session;
    private volatile OtherEcuSession otherSession;
    private volatile boolean running;
    private boolean permissionPending, destroyed,waitingForPreviousSession;
    private volatile boolean resumed, stopRequested;
    private int received;
    private final String[] rawGroups = new String[4];
    private String faultText = "Aucune lecture des défauts effectuée.";
    private final Runnable ticker = new Runnable() {
        @Override public void run() { renderValues(); refreshChrono(); refreshOperation(); maybeShowFaultResults(); continueModuleSwitch(); if (!destroyed) handler.postDelayed(this, 100); }
    };
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (permissionAction.equals(intent.getAction())) {
                boolean ours = permissionPending && pending != null && device != null && device.getDeviceName().equals(pending.getDeviceName());
                if (!ours) return;
                UsbPermissionFlow.Reply reply=usbPermission.reply(intent.getLongExtra("requestToken",-1),device.getDeviceName(),manager.hasPermission(device));
                android.util.Log.i("PP406UsbPermission","global reply="+reply+" callbackGranted="+intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)+" resumed="+resumed);
                if(reply==UsbPermissionFlow.Reply.IGNORED)return;
                if(reply==UsbPermissionFlow.Reply.GRANTED){setStatus("Accès USB accordé · reprise à la fermeture de la fenêtre Android",AMBER);handler.post(UsbDiagnosticActivity.this::continueUsbPermission);}
                else { permissionPending=false;pending=null;pendingGlobal=false;inlineRequested=false;if(inlineModule!=null)inlineFinish(inlineModule,"Accès USB non accordé par Android");setStatus("Accès USB non accordé par Android · retouche Connecter et lire puis Autoriser", AMBER); }
                controls();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction()) && device != null && AndroidLexiaUsb.matches(device)) {
                returnToStartupAfterClose=true;
                requestStop(); setStatus("LEXIA débranchée · retour à l’écran de connexion", AMBER);
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction()) && !running) { refreshDevice(); if (!returnToStartupAfterClose) handler.post(UsbDiagnosticActivity.this::autoConnectVisible); }
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        manager = (UsbManager) getSystemService(USB_SERVICE);
        gpsSpeed=new AndroidSpeedReference(this);
        linkResetRequired=getSharedPreferences("lexia_link",MODE_PRIVATE).getBoolean("resetRequired",false);
        try{vehiclePlans=new VehicleReadPlans(new JSONObject(new String(asset("vehicle-readers.json"),StandardCharsets.UTF_8)));vehiclePlan=vehiclePlans.automatic;}
        catch(Exception e){throw new IllegalStateException("Plans de lecture indisponibles",e);}
        restoreFaultReports();
        vehicleVin=getSharedPreferences("vehicle_identity",MODE_PRIVATE).getString("vin","");
        vehicleDam=getSharedPreferences("vehicle_identity",MODE_PRIVATE).getString("dam","");
        rememberedEcus=VehicleEcuMemory.load(this,vehicleVin);
        restoreVehiclePlan();
        restoreRememberedProfiles();restoreScanEquipment();
        android.content.SharedPreferences alertPrefs=getSharedPreferences("display_alerts",MODE_PRIVATE);
        if(!alertPrefs.getBoolean("rpm.red6000.upgraded",false)){
            double[] previous=tierLimits("rpm");android.content.SharedPreferences.Editor edit=alertPrefs.edit().putBoolean("rpm.red6000.upgraded",true);
            if(ValueAlerts.upgradeDefaultRpmRed(previous[0],previous[1],previous[2]))edit.putString("rpm.tier2",Double.toString(ValueAlerts.DEFAULT_RPM_RED));
            edit.apply();
        }
        for(String[] signal:allDisplaySignals()){String key=signal[0];String limit=getSharedPreferences("display_alerts",MODE_PRIVATE).getString(key,key.equals("rpm")?"6000":"");try{double n=Double.parseDouble(limit);if(ValueAlerts.finite(n))highAlerts.put(key,n);}catch(NumberFormatException ignored){}}
        for(String[] signal:allDisplaySignals()){String key=signal[0];try{double n=Double.parseDouble(getSharedPreferences("display_alerts",MODE_PRIVATE).getString(key+".low",""));if(ValueAlerts.finite(n))lowAlerts.put(key,n);}catch(NumberFormatException ignored){}}
        TripBackups.start(this);DiagnosticBackups.start(this);handler.postDelayed(backupTicker,30000);
        android.content.SharedPreferences displayPrefs=getSharedPreferences("measurement_display",MODE_PRIVATE);
        if(!displayPrefs.getBoolean("needles_default_20261028",false))
            displayPrefs.edit().putBoolean("needles",true).putBoolean("needles_default_20261028",true).apply();
        needleMode=displayPrefs.getBoolean("needles",true);
        build();
        if(needleMode)measurementGrid.post(()->{
            if(destroyed)return;
            long start=SystemClock.uptimeMillis();
            rpmDial.startSelfTest(start,tierLimits("rpm"),alertPrefs.getBoolean("rpm.tiers",true));
            speedDial.startSelfTest(start,tierLimits("speed_kmh"),alertPrefs.getBoolean("speed_kmh.tiers",true));
        });
        batteryWarning=new TabletBatteryWarning(this,batteryOverlay,()->TripBackups.kick(this,liveRecording,null));
        IntentFilter filter = new IntentFilter(permissionAction);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
        handler.post(ticker);
        acceptGarageIntent(getIntent());refreshClub(()->{if(initialAutoPending&&resumed){initialAutoPending=false;autoConnectVisible();}});
        RemoteUpdatesService.startIfEnabled(this);
        if(!DiagnosticBackups.asked(this))handler.postDelayed(()->{if(!destroyed&&!DiagnosticBackups.asked(this))promptDiagnosticConsent();},900);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration configuration){
        super.onConfigurationChanged(configuration);
        // Keep the current activity, worker and USB handle during either landscape rotation.
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN);
    }
    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);setIntent(intent);
        if(running||DiagnosticLease.held()){setStatus("Arrête la lecture avant de changer de voiture du garage",AMBER);return;}
        acceptGarageIntent(intent);refreshClub(null);
    }
    @Override protected void onResume() { super.onResume(); resumed = true;RemoteUpdatesService.visible=true;linkResetRequired=getSharedPreferences("lexia_link",MODE_PRIVATE).getBoolean("resetRequired",false); refreshDevice();handler.post(this::continueUsbPermission);if(initialAutoPending&&!clubLoading)handler.post(()->{if(initialAutoPending&&resumed){initialAutoPending=false;autoConnectVisible();}});if(gpsSpeed!=null&&getSharedPreferences("measurement_display",MODE_PRIVATE).getBoolean("gps_comparison",false))gpsSpeed.start(); }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==VEHICLE_CHOICE) {
            if(data!=null)contactCycleRequired=data.getBooleanExtra("contactCycleRequired",contactCycleRequired);
            String key=data==null?null:data.getStringExtra("vehicleKey");
            if(result==RESULT_OK&&key!=null&&(key.equals("auto")||vehiclePlans.models.containsKey(key))){
                vehiclePlan=vehicleArchitecture().equals("nonmux")?vehiclePlans.models.get("Peugeot:30"):key.equals("auto")?vehiclePlans.automatic:vehiclePlans.models.get(key);scanContinuation=null;selectedModule="motor";clearScanReadings();restoreScanEquipment();rebuildModuleButtons();showTab(0);queuedGlobal=true;
                setStatus("Recherche des calculateurs · "+vehiclePlan.label,AMBER);
            }else if(scanContinuation!=null){queuedGlobal=true;setStatus("Reprise de la recherche des autres calculateurs",AMBER);}
        }
    }
    @Override protected void onPause() {
        resumed = false;
        chrono.cancel();
        if(gpsSpeed!=null)gpsSpeed.stop();
        RemoteUpdatesService.visible=false;
        DiagnosticDataHub.inactive(sharingOwner);
        // Permission dialogs can pause an activity before any session exists.
        if (running) { requestStop(); setStatus("Arrêt demandé · application en arrière-plan", AMBER); }
        super.onPause();
    }
    @Override protected void onDestroy() {
        destroyed = true; requestStop(); handler.removeCallbacksAndMessages(null);
        if(operationDialog!=null){operationDialog.dismiss();operationDialog=null;}
        if(guideDialog!=null){guideDialog.dismiss();guideDialog=null;}
        if(faultResultsPage!=null){faultResultsPage.dismiss();faultResultsPage=null;}
        if(scanEquipmentDialog!=null){scanEquipmentDialog.dismiss();scanEquipmentDialog=null;}
        if(batteryWarning!=null)batteryWarning.close();
        if(gpsSpeed!=null)gpsSpeed.stop();
        for(android.animation.ValueAnimator animation:colorAnimations.values())animation.cancel();
        unregisterReceiver(receiver); super.onDestroy();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("tnum"); return view;
    }
    private static void updateText(TextView view,String value) {if(!value.contentEquals(view.getText()))view.setText(value);}
    private void updateNumber(String[] signal,Double value,TextView field){
        if(value==null){formattedValues.remove(signal[0]);updateText(field,"—");return;}
        if(!value.equals(formattedValues.get(signal[0]))){
            formattedValues.put(signal[0],value);
            updateText(field,String.format(Locale.FRANCE,"%."+signal[3]+"f",value));
        }
    }
    private static void updateAppearance(TextView field,int color,float alpha){
        if(field.getCurrentTextColor()!=color)field.setTextColor(color);
        if(field.getAlpha()!=alpha)field.setAlpha(alpha);
    }
    private GradientDrawable background(int color) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color); drawable.setCornerRadius(dp(12)); return drawable;
    }
    private Button button(String label, View.OnClickListener action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(14);
        button.setTextColor(WHITE); button.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(43,57,67)));
        button.setOnClickListener(action); return button;
    }
    private void build() {
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);root.setPadding(dp(8),dp(6),dp(8),dp(6));setContentView(root);
        LinearLayout content=root;
        batteryOverlay=new BatteryOverlay(this);
        ((FrameLayout)findViewById(android.R.id.content)).addView(batteryOverlay,new FrameLayout.LayoutParams(-1,-1));
        boolean compactTop=getResources().getConfiguration().screenWidthDp<1000;
        LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);content.addView(top,new LinearLayout.LayoutParams(-1,dp(50)));
        Button menu=button("PP406 ☰\n"+BuildInfo.VERSION,v->showMainMenu());menu.setTextSize(14);top.addView(menu,new LinearLayout.LayoutParams(dp(compactTop?110:148),-1));
        LinearLayout state=new LinearLayout(this);state.setOrientation(LinearLayout.VERTICAL);state.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(state,new LinearLayout.LayoutParams(0,-1,1));
        status=text("Aucune connexion",13,AMBER);status.setMaxLines(1);status.setEllipsize(android.text.TextUtils.TruncateAt.END);state.addView(status);
        age=text("diagnostique · aucune mesure",11,MUTED);age.setMaxLines(1);state.addView(age);
        start=button("▶ Scan",v->connectGlobal());start.setContentDescription("Lancer manuellement le scan des calculateurs");top.addView(start,new LinearLayout.LayoutParams(dp(compactTop?120:158),-1));
        readValuesButton=button("Lire les valeurs",v->runPrimaryAction());top.addView(readValuesButton,new LinearLayout.LayoutParams(dp(compactTop?120:155),-1));
        Button compare=button("Comparatifs",v->FuelComparisonDialog.show(this));top.addView(compare,new LinearLayout.LayoutParams(dp(compactTop?100:130),-1));
        stopButton=button("■ Arrêter",v->{monitorStartQueued=false;FaultMonitorService.stop(this);requestStop();setStatus("Arrêt demandé · fermeture de session",AMBER);});top.addView(stopButton,new LinearLayout.LayoutParams(dp(compactTop?75:100),-1));
        ImageButton reportProblem=new ImageButton(this);
        reportProblem.setImageResource(android.R.drawable.ic_dialog_alert);
        reportProblem.setColorFilter(AMBER);
        reportProblem.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(43,57,67)));
        reportProblem.setContentDescription("Signaler un problème sur cette page");
        reportProblem.setOnClickListener(v->showReportProblem());
        top.addView(reportProblem,new LinearLayout.LayoutParams(dp(50),-1));
        vehicleHeading=text("Diagnostic · détection automatique",20,WHITE);vehicleMode=text("USB DIRECT",14,MUTED);
        inlineStep=text("",13,MUTED);inlineStep.setVisibility(View.GONE);content.addView(inlineStep);
        inlineProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);inlineProgress.setIndeterminate(true);inlineProgress.setVisibility(View.GONE);content.addView(inlineProgress,new LinearLayout.LayoutParams(-1,dp(5)));
        LinearLayout navigation = new LinearLayout(this); content.addView(navigation);
        for(int i=0;i<TAB_NAMES.length;i++) {
            final int page=i; tabs[i]=button(TAB_NAMES[i],v -> {showTab(page);autoConnectVisible();}); tabs[i].setTextSize(13);
            navigation.addView(tabs[i],new LinearLayout.LayoutParams(0,dp(42),1));
        }
        HorizontalScrollView moduleScroll=new HorizontalScrollView(this); moduleScroll.setHorizontalScrollBarEnabled(true);
        moduleRow=new LinearLayout(this); moduleScroll.addView(moduleRow); content.addView(moduleScroll,new LinearLayout.LayoutParams(-1,dp(44)));
        rebuildModuleButtons();
        LinearLayout valuePage=new LinearLayout(this);valuePage.setOrientation(LinearLayout.VERTICAL);
        pages[0]=valuePage;content.addView(valuePage,new LinearLayout.LayoutParams(-1,0,1));
        // Technical information is available through the menu, outside the measurement grid.
        deviceInfo=text("",13,MUTED);globalSummary=text("Aucun scan effectué.",15,MUTED);
        guideSummary=text("",13,MUTED);raw=text("Aucune réponse reçue.",12,MUTED);
        logInfo=text("Un journal privé est conservé à chaque essai.",12,MUTED);
        measurementGrid=new LinearLayout(this);measurementGrid.setOrientation(LinearLayout.VERTICAL);
        valuePage.addView(measurementGrid,new LinearLayout.LayoutParams(-1,0,1));
        for(String[] signal:allDisplaySignals()){
            LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(12),dp(8),dp(8),dp(6));
            card.setBackground(background(PANEL));card.setOnClickListener(v->showValueGuide(signal));valueCards.put(signal[0],card);
            TextView label=text(signal[1],12,MUTED);label.setMaxLines(1);label.setEllipsize(android.text.TextUtils.TruncateAt.END);card.addView(label);
            addValueField(card,signal);
        }
        rpmDial=new NeedleDialView(this,NeedleDial.Instrument.RPM);rpmDial.setBackground(background(PANEL));rpmDial.setOnClickListener(v->showValueGuide(SIGNALS[0]));
        speedDial=new NeedleDialView(this,NeedleDial.Instrument.SPEED);speedDial.setBackground(background(PANEL));speedDial.setOnClickListener(v->showValueGuide(SIGNALS[6]));
        showMeasurements();
        LinearLayout faultPage = new LinearLayout(this); faultPage.setOrientation(LinearLayout.VERTICAL); faultPage.setPadding(0,dp(20),dp(10),dp(20));
        pages[1]=new ScrollView(this); pages[1].addView(faultPage); content.addView(pages[1],new LinearLayout.LayoutParams(-1,0,1));
        faultPage.addView(text("Défauts moteur · Bosch MP7.0",25,WHITE));
        faultStamp=text("Aucune lecture dans cette session",13,MUTED); faultStamp.setPadding(0,dp(8),0,dp(12)); faultPage.addView(faultStamp);
        LinearLayout actions=new LinearLayout(this); faultPage.addView(actions);
        reread=button("Relire les défauts",v -> rereadFaults());
        erase=button("Effacer les défauts moteur…",v -> confirmErase());
        erase.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(109,63,35)));
        actions.addView(reread,new LinearLayout.LayoutParams(0,dp(60),1)); actions.addView(erase,new LinearLayout.LayoutParams(0,dp(60),1));
        faultAction=text("Effacement disponible avec le contact mis, moteur arrêté et voiture garée. Confirmation obligatoire.",15,MUTED);
        faultAction.setPadding(0,dp(12),0,dp(18)); faultPage.addView(faultAction);
        faultPage.addView(faultReportButton(),new LinearLayout.LayoutParams(-1,dp(60)));
        faults=text(faultSummary("motor"),19,AMBER); faults.setPadding(dp(18),dp(18),dp(18),dp(18)); faults.setBackground(background(PANEL)); faultPage.addView(faults);
        TextView note=text("Les défauts et leurs contextes sont sauvegardés avant effacement, puis relus. Un défaut encore actif peut revenir immédiatement.\n\nCodes internes PSA accompagnés de leur description pour le profil identifié. Un code inconnu reste signalé avec sa valeur brute. L’effacement concerne uniquement le moteur MP7.0 identifié.",14,MUTED);
        note.setPadding(0,dp(18),0,0); faultPage.addView(note);
        functionPage = new LinearLayout(this); functionPage.setOrientation(LinearLayout.VERTICAL); functionPage.setPadding(0,dp(18),dp(12),dp(18));
        pages[2]=new ScrollView(this); pages[2].addView(functionPage); content.addView(pages[2],new LinearLayout.LayoutParams(-1,0,1));
        showTab(0);
        renderMotorFaults();
        controls();
    }
    private void rebuildModuleButtons(){
        moduleRow.removeAllViews();moduleButtons.clear();
        vehicleHeading.setText("Diagnostic · "+(vehiclePlan.key.equals("auto")?"détection automatique":vehiclePlan.label));
        vehicleMode.setText("USB DIRECT\n"+(vehiclePlan.key.equals("auto")?"Profils 406 prioritaires":vehiclePlan.label));
        for(VehicleReadPlans.Module m:vehiclePlan.modules){if(!scanEquipment.searches(m.id)||isLateralAirbag(m))continue;Button b=button(m.title,v->selectCalculator(m.id));b.setTextSize(13);moduleButtons.put(m.id,b);moduleRow.addView(b,new LinearLayout.LayoutParams(dp(158),dp(42)));}
    }
    private static boolean isLateralAirbag(VehicleReadPlans.Module m){
        return m!=null&&((m.id.equals("ecu_29")&&m.title.equals("Airbag latéral droit"))||(m.id.equals("ecu_30")&&m.title.equals("Airbag latéral gauche")));
    }
    private void addLateralAirbags(){
        if(!selectedModule.equals("airbag"))return;
        for(String id:new String[]{"ecu_29","ecu_30"}){
            VehicleReadPlans.Module m=vehiclePlan.module(id);if(!isLateralAirbag(m))continue;
            Button entry=button(m.title,v->{
                if(!m.candidates.isEmpty()){selectCalculator(m.id);return;}
                new AlertDialog.Builder(this).setTitle(m.title).setMessage("Entrée présente dans le catalogue de cette version. Sa présence sur la voiture n’est pas confirmée et sa lecture n’est pas encore prise en charge. Aucune commande n’est envoyée.").setPositiveButton("Fermer",null).show();
            });functionPage.addView(entry,new LinearLayout.LayoutParams(-1,dp(50)));
        }
    }
    private void clearScanReadings(){
        faultReports.clear();persistFaultReports();faultResultsRequested=false;faultResultsReady=false;
        identifiedProfiles.clear();otherIdentities.clear();otherValues.clear();otherFaults.clear();otherTimes.clear();otherWallTimes.clear();readIssues.clear();otherOperations.clear();genericIdentities.clear();confirmedMp72.clear();genericMeasurements.clear();values.clear();ages.clear();climateMeasurements.clear();climateAges.clear();received=0;motorSampleWallTime=0;
        measurementRevision++;climateSampleWallTime=0;formattedValues.clear();java.util.Arrays.fill(rawGroups,null);raw.setText("Aucune réponse reçue dans ce scan.");globalResults=null;globalSummary.setText("Recherche des calculateurs · "+vehiclePlan.label);
        faultText="Aucun défaut moteur lu dans ce scan.";faultStamp.setText("Aucune lecture dans ce scan");renderMotorFaults();
    }
    private boolean hasReader(String module){VehicleReadPlans.Module m=vehiclePlan.module(module);return identifiedProfiles.containsKey(module)||m!=null&&!m.candidates.isEmpty();}
    private boolean genericPage(){
        String profile=defaultProfile(selectedModule);
        if(vehiclePlans.generic(profile)||profile.startsWith("unavailable:"))return true;
        for(FunctionCatalog.Group group:FunctionCatalog.GROUPS)if(group.id.equals(selectedModule))return false;
        return true;
    }
    private void buildGenericPage(){
        VehicleReadPlans.Module module=vehiclePlan.module(selectedModule);String title=module==null?selectedModule:module.title;
        functionPage.addView(text(title+" · "+TAB_NAMES[selectedTab],24,WHITE));
        String profile=defaultProfile(selectedModule);
        boolean identityOnly=vehiclePlans.generic(profile)&&vehiclePlans.definition(profile).optBoolean("identity_only",false);
        if(identityOnly&&selectedTab==1){functionPage.addView(text("Cette première version interroge uniquement l'identification BSI. La lecture des défauts n'est pas encore disponible.",18,AMBER));return;}
        if(selectedTab>1){functionPage.addView(text("Ce profil propose la lecture seule. Les tests, apprentissages, codages et lectures mémoire nécessitent des séquences spécifiques encore à intégrer et à vérifier.",18,AMBER));return;}
        if(module!=null&&!module.unavailable.isEmpty()){
            functionPage.addView(button("Variantes et limites de prise en charge",v->new AlertDialog.Builder(this).setTitle(title).setMessage(module.unavailable).setPositiveButton("Fermer",null).show()),new LinearLayout.LayoutParams(-1,dp(48)));
        }
        Button refresh=button(identityOnly?"Identifier la BSI":selectedTab==1?"Actualiser les défauts":"Lire les valeurs en direct",v->{
            if(selectedTab==1)requestFaultResults();
            if(running&&selectedModule.equals(activeModule)&&genericSession!=null){if(selectedTab==1){operationUi.scan(selectedModule);inlineStart(selectedModule,"Actualisation des défauts…");genericSession.rereadFaults();}return;}
            inlineRequested=true;inlineStart(selectedModule,"Identification et lecture…");connectSelected();
        });
        refresh.setEnabled(hasReader(selectedModule)&&!globalRunning&&!operationUi.open&&!permissionPending);
        functionPage.addView(refresh,new LinearLayout.LayoutParams(-1,dp(60)));
        if(selectedTab==1)functionPage.addView(faultReportButton(),new LinearLayout.LayoutParams(-1,dp(60)));
        if(!hasReader(selectedModule))functionPage.addView(text("Les calculateurs sont répertoriés pour ce modèle, mais leur transport ou leur format n’est pas encore intégré. Aucune commande n’est envoyée pour cette entrée.",18,AMBER));
        else functionPage.addView(text(identityOnly?"Identification expérimentale en lecture seule. Aucun défaut, codage ou test d'actionneur ne sera lancé. Compatibilité à confirmer sur le véhicule.":"Lecteur : "+vehiclePlans.title(profile)+". Les réponses brutes sont conservées ; une réponse cohérente ne suffit pas à confirmer la variante exacte ni les conversions.",15,MUTED));
        if(profile.equals("read:13:10")&&selectedTab==0&&genericIdentities.containsKey(selectedModule)&&!genericIdentities.get(selectedModule).equals(confirmedMp72.get(selectedModule))){
            final String moduleId=selectedModule,identity=genericIdentities.get(selectedModule);
            functionPage.addView(button("Confirmer le calculateur MP7.2 pour afficher les conversions",v->new AlertDialog.Builder(this).setTitle("Calculateur MP7.2 vérifié ?")
                .setMessage("Confirme si l’étiquette ou l’identification PP2000 indique Bosch MP7.2. La réponse KWP seule ne suffit pas. Les conversions restent à comparer sur la voiture.")
                .setNegativeButton("Garder les valeurs brutes",null).setPositiveButton("MP7.2 vérifié",(d,w)->{if(identity.equals(genericIdentities.get(moduleId))){confirmedMp72.put(moduleId,identity);buildSelectedFunction();}}).show()),new LinearLayout.LayoutParams(-1,dp(58)));
        }
        displayedOtherAction=selectedModule+(selectedTab==1?"_faults":"_values");otherReadingView=text("",18,WHITE);otherReadingView.setPadding(dp(12),dp(16),dp(12),dp(16));functionPage.addView(otherReadingView);updateOtherReading();
        String identity=otherIdentities.get(selectedModule);if(identity!=null)functionPage.addView(text(identity,14,MUTED));
    }
    private void buildSelectedFunction() {
        primaryFunction=null;
        functionPage.removeAllViews(); functionRows.clear();
        while(faultReportButtons.size()>1)faultReportButtons.remove(faultReportButtons.size()-1);
        otherReadingView=null;displayedOtherAction=null;
        if(genericPage()){buildGenericPage();addLateralAirbags();return;}
        FunctionCatalog.Group selectedGroup=null;
        for(FunctionCatalog.Group group:FunctionCatalog.GROUPS) if(group.id.equals(selectedModule)) selectedGroup=group;
        functionPage.addView(text(selectedGroup.title+" · "+TAB_NAMES[selectedTab],24,WHITE));
        addLateralAirbags();
        boolean motorTests=selectedModule.equals("motor")&&selectedTab==2;
        if(motorTests) {
            LinearLayout sections=new LinearLayout(this);
            functionPage.addView(sections,new LinearLayout.LayoutParams(-1,dp(52)));
            for(int i=0;i<MOTOR_TEST_SECTIONS.length;i++) {
                final int section=i;
                Button tab=button(MOTOR_TEST_SECTIONS[i],v->{selectedMotorTestSection=section;buildSelectedFunction();pages[2].scrollTo(0,0);});
                tab.setTextColor(i==selectedMotorTestSection?LIME:WHITE);
                sections.addView(tab,new LinearLayout.LayoutParams(0,dp(48),1));
            }
        }
        vehicleSupport=text("",14,AMBER); vehicleSupport.setPadding(0,dp(12),0,dp(12)); functionPage.addView(vehicleSupport);
        List<FunctionCatalog.Action> actions=new ArrayList<>();
        Map<String,FunctionCatalog.Group> owners=new LinkedHashMap<>();
        for(FunctionCatalog.Group group:FunctionCatalog.GROUPS) {
            if(!FunctionCatalog.module(group).equals(selectedModule)) continue;
            for(FunctionCatalog.Action action:group.actions) if(FunctionCatalog.tab(action)==selectedTab) {
                int section=action.id.startsWith("injectors")?0:action.id.startsWith("ignition")?1:2;
                if(motorTests&&section!=selectedMotorTestSection)continue;
                actions.add(action); owners.put(action.id,group);
            }
        }
        if(actions.isEmpty()) {
            functionPage.addView(text("Aucune fonction de cet onglet n’est encore répertoriée pour ce calculateur.",17,MUTED));
            updateFunctionAvailability(); return;
        }
        String key=selectedModule+":"+selectedTab+(motorTests?":"+selectedMotorTestSection:"");
        FunctionCatalog.Action chosen=actions.get(0);
        for(FunctionCatalog.Action action:actions) if(action.id.equals(selectedActions.get(key))) chosen=action;
        LinearLayout choiceGrid=new LinearLayout(this);choiceGrid.setOrientation(LinearLayout.VERTICAL);functionPage.addView(choiceGrid);
        for(int offset=0;offset<actions.size();offset+=3){
            LinearLayout row=new LinearLayout(this);choiceGrid.addView(row,new LinearLayout.LayoutParams(-1,dp(60)));
            for(int j=offset;j<Math.min(offset+3,actions.size());j++){
                FunctionCatalog.Action option=actions.get(j);
                Button choice=button(option.title,v->{selectedActions.put(key,option.id);buildSelectedFunction();});
                choice.setTextSize(12);choice.setTextColor(option==chosen?LIME:WHITE);
                row.addView(choice,new LinearLayout.LayoutParams(0,dp(56),1));
            }
        }
        FunctionCatalog.Group owner=owners.get(chosen.id);
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setPadding(dp(20),dp(20),dp(20),dp(20)); card.setBackground(background(PANEL));
        LinearLayout.LayoutParams layout=new LinearLayout.LayoutParams(-1,-2); layout.setMargins(0,dp(18),0,0); functionPage.addView(card,layout);
        card.addView(text(chosen.title,23,WHITE));
        FunctionCatalog.Action action=chosen;
        primaryFunction=action;
        Button execute=button(chosen.title,v->runFunction(action));card.addView(execute,new LinearLayout.LayoutParams(-1,dp(58)));
        TextView reason=text("",15,MUTED);reason.setPadding(0,dp(6),0,dp(8));card.addView(reason);
        functionRows.add(new FunctionRow(owner,chosen,execute,reason));
        String explanation=!motorTests?owner.detail:selectedMotorTestSection==0?
            "Tests des injecteurs par paires. Procédure d’isolement du carburant et conditions de reprise encore à valider pour le MP7.0 identifié.":selectedMotorTestSection==1?
            "Tests de l’allumage par paires. Procédure de mise hors service de l’injection et conditions de reprise encore à valider pour le MP7.0 identifié.":
            "Chauffage de sonde, pompe à essence, signal compte-tours, commande de ralenti et purge canister. Chaque test possède sa confirmation, sa demande d’arrêt et son résultat. Effets physiques encore à valider sur véhicule.";
        TextView detail=text(explanation,16,MUTED); detail.setPadding(0,dp(8),0,dp(12)); detail.setTextSize(13); card.addView(detail);
        FunctionEvidence.Entry evidence=FunctionEvidence.get(chosen.id);
        if(evidence!=null) {
            final String chosenTitle=chosen.title;
            Button progress=button("État de cette fonction",v -> new AlertDialog.Builder(this)
                .setTitle("Prise en charge · "+chosenTitle)
                .setMessage(evidence.finding+"\n\nÀ vérifier : "+evidence.remaining)
                .setPositiveButton("Fermer",null).show());
            card.addView(progress,new LinearLayout.LayoutParams(-1,dp(48)));
        }
        if((OtherEcuSession.profile(defaultProfile(selectedModule))!=null||MotorOperations.plan(action.id)!=null) && action.implemented) {
            displayedOtherAction=action.id;otherReadingView=text("",17,WHITE);otherReadingView.setPadding(0,0,0,dp(16));
            card.addView(otherReadingView,1);updateOtherReading();
        }
        ActionWarnings.Warning warning=ActionWarnings.forAction(action.id);
        if(warning!=null) {
            TextView caution=text(warning.title+"\n"+warning.summary,17,AMBER);
            caution.setPadding(dp(14),dp(14),dp(14),dp(14)); caution.setBackground(background(Color.rgb(60,43,25))); card.addView(caution);
            Button precautions=button("Lire les précautions",v -> showPrecautions(action,false));
            card.addView(precautions,new LinearLayout.LayoutParams(-1,dp(52)));
        }
        if(action.id.endsWith("_faults")||action.id.endsWith("_clear"))card.addView(faultReportButton(),new LinearLayout.LayoutParams(-1,dp(60)));
        updateFunctionAvailability();
    }
    private void runFunction(FunctionCatalog.Action action) {
        if(canPrepareTest(action)){testAwaitingRead=action;prepareTest(action);return;}
        if(canConnectForRead(action)) {if(action.id.endsWith("_faults"))requestFaultResults();inlineRequested=true;inlineStart(selectedModule,"Actualisation de la lecture…");connectSelected();return;}
        if(functionAvailability(action)!=FunctionCatalog.Availability.AVAILABLE) return;
        if(ActionWarnings.forAction(action.id)!=null) { showPrecautions(action,true); return; }
        dispatchFunction(action,false);
    }
    private void showPrecautions(FunctionCatalog.Action action,boolean executionRequested) {
        ActionWarnings.Warning warning=ActionWarnings.forAction(action.id);
        if(warning==null) return;
        TextView body=text(ActionWarnings.fullText(warning),18,WHITE); body.setPadding(dp(24),dp(16),dp(24),dp(16));
        ScrollView scroll=new ScrollView(this); scroll.addView(body);
        AlertDialog.Builder dialog=new AlertDialog.Builder(this).setTitle(warning.title).setView(scroll);
        final OtherEcuSession confirmedOther=otherSession;
        final Mp70Session confirmedMotor=session;
        if(executionRequested) dialog.setNegativeButton("Annuler",null).setPositiveButton("Conditions vérifiées · confirmer",(d,w) -> {
            boolean same=FunctionCatalog.module(action).equals("motor")?session==confirmedMotor:otherSession==confirmedOther;
            if(same)dispatchFunction(action,true);
            else setStatus("La session a changé · relire les conditions avant une nouvelle confirmation",AMBER);
        });
        else dialog.setPositiveButton("Fermer",null);
        dialog.show();
    }
    private void dispatchFunction(FunctionCatalog.Action action,boolean acknowledged) {
        if(!resumed || !ActionWarnings.mayDispatch(functionAvailability(action),action.id,acknowledged)) return;
        if(genericSession!=null&&vehiclePlans.generic(activeProfile)&&FunctionCatalog.module(action).equals(activeModule)){
            if(action.id.endsWith("_faults")&&genericSession.queueFaultRead()){
                requestFaultResults();operationUi.scan(selectedModule);beginOperation(selectedModule,"Lecture des défauts","Interrogation du calculateur");
            }else if(action.id.endsWith("_identify")){
                new AlertDialog.Builder(this).setTitle("Identification du calculateur").setMessage(otherIdentities.containsKey(activeModule)?otherIdentities.get(activeModule):"Identification en cours.").setPositiveButton("Fermer",null).show();
            }else if(action.id.endsWith("_values"))showTab(0);
            controls();updateOtherReading();return;
        }
        if(!FunctionCatalog.module(action).equals("motor")) {
            OtherEcuSession current=otherSession;
            if(action.id.endsWith("_clear")) {confirmOtherErase(action);return;}
            if(ClimateOperations.plan(action.id)!=null) {
                if(current!=null && current.queueConfirmedProcedure(action.id)) {
                    beginOperation(selectedModule,action.title,"Sauvegarde des lectures avant l’envoi");
                    String key=selectedModule+":op:"+action.id;
                    otherOperations.put(key,"Procédure confirmée · sauvegarde des lectures avant l’envoi");
                    markReading(key);
                }
                controls();updateOtherReading();return;
            }
            if(current!=null && action.id.endsWith("_faults")&&current.queueFaultRead()) {
                requestFaultResults();
                operationUi.scan(selectedModule);
                beginOperation(selectedModule,"Lecture des défauts","Interrogation du calculateur");
            }
            else if(action.id.endsWith("_values"))showTab(0);
            else if(action.id.endsWith("_identify"))new AlertDialog.Builder(this).setTitle("Identification du calculateur").setMessage(otherIdentities.containsKey(activeModule)?otherIdentities.get(activeModule):"Identification en cours.").setPositiveButton("Fermer",null).show();
            controls();updateOtherReading();return;
        }
        if(MotorOperations.plan(action.id)!=null) {
            Mp70Session current=session;
            if(current!=null&&current.queueConfirmedProcedure(action.id)) {
                operationPending=true;
                beginOperation("motor",action.title,"Contrôle des conditions avant envoi");
                otherOperations.put("motor:op:"+action.id,"Test confirmé · contrôle des conditions avant envoi");
                markReading("motor:op:"+action.id);
            }
            controls();updateOtherReading();return;
        }
        switch(action.id) {
            case "motor_values": showTab(0); break;
            case "motor_faults": showTab(1); rereadFaults(); break;
            case "motor_clear": showTab(1); confirmErase(); break;
            default: break; // No command dispatcher for entries awaiting development.
        }
    }
    private void rereadFaults() {
        Mp70Session current=session;
        if(canReadFaults() && current!=null && current.queueFaultRead()) {
            requestFaultResults();
            operationPending=true;operationUi.scan("motor");beginOperation("motor","Lecture des défauts","Lecture des défauts et de leurs contextes");
            faultAction.setText("Lecture des défauts en cours…"); controls();
        } else if(!operationUi.open&&!running){requestFaultResults();inlineRequested=true;inlineStart("motor","Actualisation des défauts…");connectSelected();}
        else faultAction.setText("Connecte d’abord le calculateur moteur ou termine l’opération en cours.");
    }
    private boolean canReadFaults() {
        Mp70Session current=session;
        return !operationUi.open && running && !stopRequested && !operationPending && current!=null && current.isReady()
            && FunctionCatalog.MP70_REFERENCE.equals(recognizedReference);
    }
    private FunctionCatalog.Availability functionAvailability(FunctionCatalog.Action action) {
        if(operationUi.open)return FunctionCatalog.Availability.BUSY;
        String module=FunctionCatalog.module(action);
        if(running&&!stopRequested&&module.equals(activeModule)&&genericSession!=null&&vehiclePlans.generic(activeProfile)){
            boolean groups=false;JSONObject definition=vehiclePlans.definition(activeProfile);
            if(definition!=null){org.json.JSONArray commands=definition.optJSONArray("commands");if(commands!=null)for(int i=0;i<commands.length();i++){JSONObject item=commands.optJSONObject(i);if(item!=null&&item.optString("name").startsWith("group")){groups=true;break;}}}
            return FunctionCatalog.genericReadAvailability(action,genericSession.isReady(),groups,false);
        }
        if(!module.equals("motor")) {
            OtherEcuSession current=otherSession;
            boolean active=running&&!stopRequested&&module.equals(activeModule)&&current!=null&&current.isReady();
            FunctionCatalog.Availability availability=FunctionCatalog.evaluate(action,active?FunctionCatalog.Presence.PRESENT:FunctionCatalog.Presence.UNKNOWN,
                active,module.equals(activeModule)?recognizedOtherReference:null,false,current!=null&&current.isFaultReadPending());
            if(availability==FunctionCatalog.Availability.AVAILABLE && (action.id.endsWith("_clear")||ClimateOperations.plan(action.id)!=null) && !current.hasVerifiedFaults())
                return FunctionCatalog.Availability.FAULT_READ_REQUIRED;
            return availability;
        }
        Mp70Session current=session;
        boolean active=running && !stopRequested && current!=null && current.isReady();
        FunctionCatalog.Presence presence=action.implemented && recognizedReference!=null
            ?FunctionCatalog.Presence.PRESENT:FunctionCatalog.Presence.UNKNOWN;
        FunctionCatalog.Availability state=FunctionCatalog.evaluate(action,presence,active,recognizedReference,stationaryEngineOffFresh(),operationPending||current!=null&&current.isOperationPending());
        if(state==FunctionCatalog.Availability.AVAILABLE&&MotorOperations.plan(action.id)!=null&&!current.hasVerifiedFaults())return FunctionCatalog.Availability.FAULT_READ_REQUIRED;
        return state;
    }
    private void updateFunctionAvailability() {
        if(vehicleSupport==null) return;
        boolean active=canReadFaults() || (operationPending && running && !stopRequested);
        String support=!selectedModule.equals("motor")?(OtherEcuSession.profile(defaultProfile(selectedModule))==null?"Calculateur non identifié · reconnaissance et fonctions en préparation."
            :selectedModule.equals(activeModule)&&recognizedOtherReference!=null?(recognizedOtherReference.startsWith("profile:")?"Profil de lecture : "+OtherEcuSession.profile(activeProfile).title+" · compatibilité physique à confirmer":"Référence reconnue : "+recognizedOtherReference)+(running&&!stopRequested?" · session active":" · session inactive")
            :otherIdentities.containsKey(selectedModule)?"Résultats du dernier relevé disponibles · Actualiser pour les relire.":"Aucun relevé disponible · lecture possible avec le Lexia branché.")
            :recognizedReference==null?"Aucun calculateur identifié dans cette session."
            :"Moteur reconnu : Bosch MP7.0 · "+recognizedReference+(active?" · session active":" · session inactive");
        if(!support.contentEquals(vehicleSupport.getText())) vehicleSupport.setText(support);
        for(FunctionRow row:functionRows) {
            FunctionCatalog.Availability state=functionAvailability(row.action);
            row.last=state;boolean connectRead=canConnectForRead(row.action),prepare=canPrepareTest(row.action);
            boolean available=state==FunctionCatalog.Availability.AVAILABLE||connectRead||prepare;
            updateText(row.button,prepare?"Lancer le test":
                connectRead?(row.action.id.endsWith("_faults")?"Actualiser les défauts":"Actualiser la lecture"):"Lancer · "+row.action.title);
            row.button.setEnabled(available); row.button.setAlpha(available?1f:0.40f);
            updateText(row.reason,prepare?"Préparation en lecture seule. Aucune activation avant la confirmation des précautions.":connectRead?"Les résultats déjà lus restent affichés pendant l’actualisation.":state.label); row.reason.setTextColor(available?LIME:MUTED);
        }
        updateOtherReading();
        updatePrimaryAction();
    }
    private void markReading(String key) {
        otherTimes.put(key,SystemClock.elapsedRealtime());otherWallTimes.put(key,System.currentTimeMillis());
    }
    private void updateOtherReading() {
        if(otherReadingView==null||displayedOtherAction==null)return;
        if(ClimateOperations.plan(displayedOtherAction)!=null||MotorOperations.plan(displayedOtherAction)!=null) {
            String key=selectedModule+":op:"+displayedOtherAction,result=otherOperations.get(key);
            Long when=otherTimes.get(key);
            Long wall=otherWallTimes.get(key);
            String time=wall==null?"":"DERNIER ÉTAT · à "+ReadingTime.clock(wall)+"\n\n";
            updateText(otherReadingView,result==null?"Aucun test exécuté pour cette fonction. La lecture en direct reste séparée du résultat de test.":time+result);
            return;
        }
        String module=selectedModule,kind=displayedOtherAction.endsWith("_faults")||displayedOtherAction.endsWith("_clear")?"faults":displayedOtherAction.endsWith("_identify")||module.equals("steering")&&!genericPage()?"id":"values";
        if(kind.equals("faults")) {
            updateText(otherReadingView,faultSummary(module));return;
        }
        String value=(kind.equals("faults")?otherFaults:kind.equals("id")?otherIdentities:otherValues).get(module);
        if(value!=null&&module.equals("keys")&&kind.equals("values"))value=KeyReadingPresentation.select(displayedOtherAction,value);
        Long when=otherTimes.get(module+":"+kind);
        boolean acquiring=running&&!stopRequested&&module.equals(activeModule);
        Long wall=otherWallTimes.get(module+":"+kind);
        String stamp=wall==null?"DERNIÈRE LECTURE":kind.equals("values")&&when!=null?
            ReadingTime.label(wall,when,SystemClock.elapsedRealtime(),acquiring):
            (acquiring?"DERNIÈRE LECTURE RÉELLE":"LECTURE ARRÊTÉE · dernier relevé")+" à "+ReadingTime.clock(wall);
        boolean pending=kind.equals("faults")&&acquiring&&otherSession!=null&&otherSession.isFaultReadPending();
        String issue=readIssues.get(module);
        String text=(pending?"LECTURE DES DÉFAUTS EN COURS…\n\n":"")+(issue==null?"":"Dernier essai : "+issue+"\n\n")+(value==null?"Aucune réponse reçue pour cette fonction.":stamp+"\n\n"+value);
        if(!text.contentEquals(otherReadingView.getText()))otherReadingView.setText(text);
    }
    private void showTab(int selected) {
        if(selected!=selectedTab)testAwaitingRead=null;
        if(!navigatingBack)navigation.visit(selectedModule,selected);
        selectedTab=selected;
        boolean nativeMotorPage=selectedModule.equals("motor") && selected<2&&!genericPage()
            ||selectedModule.equals("climate")&&selected==0&&!genericPage();
        if(nativeMotorPage&&selected==0)showMeasurements();
        for(int i=0;i<pages.length;i++) pages[i].setVisibility((nativeMotorPage?i==selected:i==2)?View.VISIBLE:View.GONE);
        for(int i=0;i<tabs.length;i++) {
            tabs[i].setTextColor(i==selected?LIME:WHITE);
        }
        for(Map.Entry<String,Button> entry:moduleButtons.entrySet()) entry.getValue().setTextColor(entry.getKey().equals(selectedModule)?LIME:WHITE);
        if(!nativeMotorPage) { buildSelectedFunction(); pages[2].scrollTo(0,0); }
        updatePrimaryAction();
    }
    private void inlineStart(String module,String step){inlineModule=module;inlineStep.setText(step);inlineStep.setVisibility(View.VISIBLE);inlineProgress.setVisibility(View.VISIBLE);}
    private void inlineFinish(String module,String step){if(!module.equals(inlineModule))return;inlineStep.setText(step);inlineStep.setVisibility(View.GONE);inlineProgress.setVisibility(View.GONE);inlineModule=null;}
    private void selectCalculator(String module){
        selectedModule=module;showTab(selectedTab);controls();
        autoConnectVisible();
    }
    /** Only identification and read-only data; never launch a test or an erase. */
    private void autoConnectVisible(){
        if(operationUi.open||globalRunning||permissionPending||monitorStartQueued||!resumed||destroyed||returnToStartupAfterClose)return;
        if(FaultMonitorService.active()||operationPending||coreOperationBusy())return;
        final String module=selectedModule;
        if(!hasReader(module))return;
        if(linkResetRequired||contactCycleRequired){
            setStatus(linkResetRequired?"Liaison Lexia à rétablir avant la lecture":"Coupe puis remets le contact avant la lecture",AMBER);return;
        }
        try{if(findDevice()==null)return;}catch(IOException e){return;}
        if(!vehicleContextReady()){
            setStatus("Renseigne le VIN et le type de véhicule avant la lecture",AMBER);
            requestVehicleContext(this::autoConnectVisible);return;
        }
        if(!AutoConnectionPolicy.mayStart(resumed,false,true,true,hasReader(module),
            linkResetRequired||contactCycleRequired,garageIntentUnresolved,running&&module.equals(activeModule)))return;
        inlineRequested=true;
        inlineStart(module,"Connexion automatique · identification et lecture seules…");
        connectProfile(defaultProfile(module),true);
    }
    private void confirmErase() {
        if(!canErase()) { faultAction.setText("Effacement indisponible : défauts et contextes vérifiés, lecture active récente, moteur à 0 tr/min et vitesse à 0 km/h requis."); return; }
        final Mp70Session confirmedSession=session;
        new AlertDialog.Builder(this).setTitle("Effacer les défauts moteur ?")
            .setMessage("Calculateur Bosch MP7.0 · 0261204407\n\nConfirme que la voiture est garée, contact mis et moteur arrêté.\n\nLa liste et les contextes seront enregistrés, puis les défauts effacés et relus. Un défaut présent peut réapparaître.")
            .setNegativeButton("Annuler",null).setPositiveButton("Confirmer l’effacement",(dialog,which) -> {
                Mp70Session current=session;
                if(canErase() && current!=null && current==confirmedSession && resumed && current.queueConfirmedClear()) {
                    operationPending=true; faultAction.setText("Effacement confirmé · vérification et sauvegarde en cours…"); controls();
                    operationUi.scan("motor");beginOperation("motor","Effacement des défauts moteur","Sauvegarde et contrôle des conditions");
                } else faultAction.setText("Conditions modifiées : effacement non envoyé.");
            }).show();
    }
    private void confirmOtherErase(FunctionCatalog.Action action) {
        if(!resumed || functionAvailability(action)!=FunctionCatalog.Availability.AVAILABLE)return;
        final String module=FunctionCatalog.module(action);
        final OtherEcuSession confirmedSession=otherSession;
        String extra=module.equals("airbag")?"\n\nAprès l’accusé, une attente de 33 secondes est prévue avant la relecture. Garder le contact et le Lexia branché jusqu’à la fin.":"";
        new AlertDialog.Builder(this).setTitle("Effacer les défauts de ce calculateur ?")
            .setMessage("Calculateur : "+vehiclePlans.title(activeProfile)+"\n\nVoiture garée, contact mis, moteur arrêté. La liste sera sauvegardée puis effacée et relue. Un défaut actif peut réapparaître.\n\nNouvelle fonction : procédure reconstruite et testée hors ligne ; premier essai physique à valider."+extra)
            .setNegativeButton("Annuler",null).setPositiveButton("Confirmer l’effacement",(dialog,which)->{
                OtherEcuSession current=otherSession;
                if(resumed && module.equals(activeModule) && functionAvailability(action)==FunctionCatalog.Availability.AVAILABLE
                    && current!=null && current==confirmedSession && current.queueConfirmedClear()) {
                    operationUi.scan(module);beginOperation(module,"Effacement des défauts","Sauvegarde de la liste avant l’effacement");
                    otherFaults.put(module,"Effacement demandé · sauvegarde et contrôle des défauts en cours.");
                    otherTimes.remove(module+":faults");otherWallTimes.remove(module+":faults");controls();updateOtherReading();
                }
            }).show();
    }
    private boolean canErase() {
        Mp70Session current=session;
        return canReadFaults() && current!=null && current.hasVerifiedFaults() && stationaryEngineOffFresh();
    }
    private boolean stationaryEngineOffFresh() {
        Long stamp=ages.get("speed_kmh");
        Long rpmStamp=ages.get("rpm");
        return stamp!=null&&rpmStamp!=null&&stamp>=motorSessionStart&&rpmStamp>=motorSessionStart&&SystemClock.elapsedRealtime()-rpmStamp<3000&&SystemClock.elapsedRealtime()-stamp<3000
            && Double.valueOf(0).equals(values.get("rpm")) && Double.valueOf(0).equals(values.get("speed_kmh"));
    }
    private void setStatus(String message,int color) { updateText(status,message);if(status.getCurrentTextColor()!=color)status.setTextColor(color); }
    private boolean coreOperationBusy() {
        Mp70Session motor=session;OtherEcuSession other=otherSession;
        return motor!=null&&motor.isOperationPending()||other!=null&&other.isFaultReadPending();
    }
    private boolean operationClosing(){return stopRequested&&running||!running&&DiagnosticLease.held();}
    private void beginOperation(String module,String title,String message) {
        engineGuide.disable("Opération lancée · revalider les conditions du ralenti après son résultat");
        operationUi.begin(module,title,message);
        operationFaultResult=null;
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(24),dp(16),dp(24),dp(16));body.setBackgroundColor(PANEL);
        operationStep=text(message,20,WHITE);body.addView(operationStep,new LinearLayout.LayoutParams(-1,-2));
        operationProgress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        operationProgress.setMax(100);operationProgress.setIndeterminate(true);
        LinearLayout.LayoutParams bar=new LinearLayout.LayoutParams(-1,dp(20));bar.setMargins(0,dp(24),0,dp(20));body.addView(operationProgress,bar);
        operationResultScroll=new ScrollView(this);operationResult=text("",17,WHITE);
        operationResultScroll.addView(operationResult);operationResultScroll.setVisibility(View.GONE);
        body.addView(operationResultScroll,new LinearLayout.LayoutParams(-1,dp(180)));
        body.addView(text(module.equals("global")?"Les calculateurs déjà lus restent consultables si une autre recherche échoue.\nContinuer donne accès aux résultats obtenus.":"Garde le contact et le Lexia branché jusqu’au résultat.\nLe rapport reste réouvrable avec « Voir les derniers défauts ».",15,MUTED));
        operationDialog=new AlertDialog.Builder(this).setTitle(title).setView(body).setCancelable(false)
            .setPositiveButton(module.equals("global")?"Continuer":"Terminé",null).setNeutralButton("‹ Retour",null)
            .setNegativeButton(module.equals("global")?"Interrompre la recherche":"Interrompre le test",null).create();
        operationDialog.show();
        operationDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> navigateBack());
        operationDialog.setOnKeyListener((d,key,event)->{
            if(key==android.view.KeyEvent.KEYCODE_BACK){if(event.getAction()==android.view.KeyEvent.ACTION_UP)navigateBack();return true;}return false;
        });
            operationDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
                if(!operationUi.mayAcknowledge(coreOperationBusy(),operationClosing()))return;
                String completedModule=operationUi.module;
                operationUi.acknowledge(false,false);operationDialog.dismiss();operationDialog=null;
                if(completedModule.equals("motor"))renderMotorFaults();
                if(completedModule.equals("global")){selectedModule="motor";showTab(0);}
                updateOtherReading();controls();
                if(running&&!stopRequested)setStatus("Connexion conservée · prêt pour une nouvelle lecture",LIME);
                if(backAfterClose){backAfterClose=false;navigateBack();}
                else if(faultResultsRequested&&!faultReports.isEmpty())showFaultResults();
                else if(completedModule.equals("global")&&identifiedProfiles.containsKey("motor")&&!linkResetRequired&&!contactCycleRequired)readSelectedValues();
            });
            operationDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v->{
                if(globalRunning){globalCancel=true;Mp70Session motor=session;OtherEcuSession other=otherSession;EngineReadSession generic=genericSession;if(motor!=null)motor.stop();if(other!=null)other.stop();if(generic!=null)generic.stop();}
                else {Mp70Session motor=session;OtherEcuSession other=otherSession;if(motor!=null)motor.cancelProcedure();if(other!=null)other.cancelProcedure();}
                operationUi.phase("Interruption demandée · attente du retour au mode normal et de la relecture");refreshOperation();
            });
        refreshOperation();controls();
    }
    private void operationEvent(String module,String kind,String text,String actionId) {
        if(!operationUi.open||!module.equals(operationUi.module))return;
        if(kind.equals("clear_send")||kind.equals("procedure_start")||kind.equals("clear_unknown")||kind.equals("clear_unverified")
            ||kind.equals("faults_unavailable")||kind.equals("procedure_rejected")||kind.equals("procedure_cancelled"))operationFaultResult=null;
        if(kind.equals("faults_read")||kind.equals("faults_unavailable"))faultResultsReady=true;
        if(OperationPresentation.terminal(kind))operationUi.finish(text);
        else if(kind.equals("clear_wait")) {
            OtherEcuErasure.Plan plan=OtherEcuErasure.plan(activeProfile);
            long duration=module.equals("airbag")?33000:plan!=null?plan.completionMs:0;
            if(duration>0)operationUi.timed(text,SystemClock.elapsedRealtime(),duration);else operationUi.phase(text);
        }
        else if(kind.equals("clear_progress")) {operationUi.message=text; /* keep the timer origin */}
        else if(kind.equals("procedure_wait")) {
            MotorOperations.Plan motor=MotorOperations.plan(actionId);
            ClimateOperations.Plan climate=ClimateOperations.plan(actionId);
            long duration=motor!=null?motor.durationMs:climate!=null?climate.durationMs:0;
            if(duration>0)operationUi.timed(text,SystemClock.elapsedRealtime(),duration);else operationUi.phase(text);
        }
        else if(kind.equals("procedure_start")) {
            // Procedure startup includes protocol exchanges. Its bar remains
            // indeterminate; only the explicit ECU wait has a measured fraction.
            operationUi.phase(text);
        } else operationUi.phase(text);
        refreshOperation();
    }
    private void refreshOperation() {
        if(operationDialog==null||!operationUi.open)return;
        updateText(operationStep,operationUi.message);
        // Code lists live only in the dedicated report page. The scan keeps its ECU status list.
        boolean showResult=operationUi.module.equals("global")&&operationFaultResult!=null;
        operationResultScroll.setVisibility(showResult?View.VISIBLE:View.GONE);
        if(showResult)updateText(operationResult,operationFaultResult);
        operationProgress.setIndeterminate(!operationUi.determinate());
        if(operationUi.determinate())operationProgress.setProgress(operationUi.progress(SystemClock.elapsedRealtime()));
        if(operationUi.module.equals("global")&&!operationUi.finished){operationProgress.setIndeterminate(false);operationProgress.setProgress(globalCompleted*100/Math.max(1,globalTotal));}
        Button done=operationDialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if(done!=null){done.setEnabled(operationUi.mayAcknowledge(coreOperationBusy(),operationClosing()));done.setAlpha(done.isEnabled()?1f:0.35f);}
        if(backAfterClose&&done!=null&&done.isEnabled()){done.performClick();return;}
        Button cancel=operationDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if(cancel!=null) {
            Mp70Session motor=session;OtherEcuSession other=otherSession;
            boolean cancellable=globalRunning&&!globalCancel||motor!=null&&motor.isProcedurePending()||other!=null&&other.isProcedurePending();
            cancel.setEnabled(!operationUi.finished&&running&&!stopRequested&&cancellable);
            cancel.setVisibility(cancellable?View.VISIBLE:View.GONE);
        }
    }
    private void operationSessionEnded(String module,String outcome) {
        if(operationUi.open&&module.equals(operationUi.module)&&!operationUi.finished)operationUi.finish(outcome);
        refreshOperation();
        if(resumed&&outcome.contains(FirmwareIdentity.ERROR))new AlertDialog.Builder(this)
            .setTitle("Lexia détectée · firmware incompatible")
            .setMessage(FirmwareIdentity.ERROR+". Mets à jour le firmware de la Lexia, puis rebranche-la.")
            .setPositiveButton("Fermer",null).show();
    }
    private void renderMotorFaults() {
        faults.setText(faultSummary("motor"));
        FaultReport report=faultReports.get("motor");
        faultStamp.setText(report==null?"Aucune lecture dans ce scan":"Dernier relevé : "+ReadingTime.clock(report.readAt));
        updateFaultReportButtons();
    }
    private String faultModuleTitle(String module){
        VehicleReadPlans.Module m=vehiclePlan.module(module);
        if(m!=null)return m.title;
        for(FunctionCatalog.Group group:FunctionCatalog.GROUPS)if(group.id.equals(module))return group.title;
        return module;
    }
    private String faultSummary(String module){
        FaultReport report=faultReports.get(module);
        return report==null?"Aucun résultat de défauts pour ce calculateur dans ce scan.":report.summary()+"\nDernier relevé : "+ReadingTime.clock(report.readAt);
    }
    private Button faultReportButton(){
        Button b=button("Voir les derniers défauts",v->showFaultResults());faultReportButtons.add(b);b.setEnabled(!faultReports.isEmpty());return b;
    }
    private void updateFaultReportButtons(){for(Button b:faultReportButtons){b.setEnabled(!faultReports.isEmpty());b.setAlpha(b.isEnabled()?1f:0.35f);}}
    private void rememberFaultReport(String module,String profile,String result){rememberFaultReport(module,profile,result,false);}
    private void rememberFaultReport(String module,String profile,String result,boolean keepStamp){
        FaultReport old=faultReports.get(module);
        long stamp=keepStamp&&old!=null?old.readAt:System.currentTimeMillis();
        String profileTitle=vehiclePlans.title(profile);
        FaultReport report=new FaultReport(module,faultModuleTitle(module),profileTitle,stamp,result,"",
            keepStamp&&old!=null?old.carId:diagnosticCarId,
            keepStamp&&old!=null?old.observationId:java.util.UUID.randomUUID().toString());
        faultReports.put(module,report);
        if(!report.carId.isEmpty())try{FaultExport.record(this,report);}catch(Exception e){android.util.Log.w("PP406","Export défauts non conservé",e);}
        persistFaultReports();updateFaultReportButtons();
    }
    private void faultReportIssue(String module,String message){
        FaultReport previous=faultReports.get(module);
        if(previous==null)faultReports.put(module,new FaultReport(module,faultModuleTitle(module),vehiclePlans.title(defaultProfile(module)),System.currentTimeMillis(),message,"Lecture complète non confirmée"));
        else faultReports.put(module,previous.withNotice(message+" · dernier relevé conservé"));
        persistFaultReports();updateFaultReportButtons();
    }
    private void persistFaultReports(){
        org.json.JSONArray saved=new org.json.JSONArray();
        try{for(FaultReport r:faultReports.values())saved.put(new JSONObject().put("module",r.module).put("title",r.title).put("profile",r.profile).put("readAt",r.readAt).put("text",r.text).put("notice",r.notice).put("carId",r.carId).put("observationId",r.observationId));
            getSharedPreferences("fault_reports",MODE_PRIVATE).edit().putString("last",saved.toString()).apply();
        }catch(org.json.JSONException e){android.util.Log.w("PP406","Rapport non conservé",e);}
    }
    private void restoreFaultReports(){
        try{org.json.JSONArray saved=new org.json.JSONArray(getSharedPreferences("fault_reports",MODE_PRIVATE).getString("last","[]"));
            for(int i=0;i<saved.length();i++){JSONObject r=saved.getJSONObject(i);String module=r.getString("module");faultReports.put(module,new FaultReport(module,r.getString("title"),r.getString("profile"),r.getLong("readAt"),r.getString("text"),"Rapport conservé d’une session précédente · relancer un scan pour actualiser",r.optString("carId",""),r.optString("observationId","")));}
        }catch(Exception e){faultReports.clear();android.util.Log.w("PP406","Rapport conservé illisible",e);}
    }
    private void requestFaultResults(){faultResultsRequested=true;faultResultsReady=false;}
    private void showFaultResults(){
        faultResultsRequested=false;faultResultsReady=false;
        if(destroyed||!resumed||faultReports.isEmpty())return;
        if(faultResultsPage!=null&&faultResultsPage.isShowing())return;
        faultResultsPage=new FaultResultsPage(this,faultReports.values());
        faultResultsPage.setOnDismissListener(d->faultResultsPage=null);faultResultsPage.show();
    }
    private void maybeShowFaultResults(){
        if(!faultResultsRequested||!faultResultsReady||globalRunning||!resumed||coreOperationBusy()||operationClosing())return;
        if(operationUi.open){
            if(!operationUi.mayAcknowledge(false,false))return;
            operationUi.acknowledge(false,false);if(operationDialog!=null){operationDialog.dismiss();operationDialog=null;}
            controls();
        }
        showFaultResults();
    }
    private void controls() {
        if(FaultMonitorService.active()){monitoringShown=true;setStatus(FaultMonitorService.status(),AMBER);}
        else if(monitoringShown){monitoringShown=false;refreshDevice();setStatus(FaultMonitorService.status(),AMBER);}
        if(!running&&waitingForPreviousSession&&!DiagnosticLease.held()) {
            waitingForPreviousSession=false;refreshDevice();
        }
        start.setText("▶ Scan");
        start.setEnabled(!monitorStartQueued&&!operationUi.open&&!permissionPending&&switchProfile==null&&!queuedGlobal&&(!running&&!DiagnosticLease.held()||running&&!globalRunning)); stopButton.setEnabled(running&&!stopRequested||FaultMonitorService.active()||monitorStartQueued);
        readValuesButton.setEnabled(!monitorStartQueued&&!operationUi.open&&!permissionPending&&!globalRunning&&switchProfile==null&&!queuedGlobal&&(!DiagnosticLease.held()||running&&!stopRequested));
        updatePrimaryAction();
        readValuesButton.setAlpha(readValuesButton.isEnabled()?1f:.35f);
        start.setAlpha(start.isEnabled()?1f:0.35f); stopButton.setAlpha(stopButton.isEnabled()?1f:0.35f);
        if(reread!=null) reread.setEnabled(canReadFaults()||!operationUi.open&&!running&&!permissionPending&&!DiagnosticLease.held());
        if(erase!=null) erase.setEnabled(canErase());
        if(reread!=null) reread.setAlpha(reread.isEnabled()?1f:0.35f);
        if(erase!=null) erase.setAlpha(erase.isEnabled()?1f:0.35f);
        updateFunctionAvailability();
        continuePreparedTest();
    }
    private void continuePreparedTest(){
        FunctionCatalog.Action action=testAwaitingRead;
        if(action==null||!resumed||operationUi.open||permissionPending)return;
        if(selectedTab!=2||primaryFunction==null||!action.id.equals(primaryFunction.id)||!FunctionCatalog.module(action).equals(selectedModule)){testAwaitingRead=null;return;}
        FunctionCatalog.Availability state=functionAvailability(action);
        if(state==FunctionCatalog.Availability.AVAILABLE){testAwaitingRead=null;handler.post(()->{if(resumed&&selectedTab==2&&primaryFunction!=null&&primaryFunction.id.equals(action.id)&&functionAvailability(action)==FunctionCatalog.Availability.AVAILABLE)runFunction(action);});}
        else if(state!=FunctionCatalog.Availability.BUSY&&state!=FunctionCatalog.Availability.IDENTIFICATION_REQUIRED&&state!=FunctionCatalog.Availability.FAULT_READ_REQUIRED){testAwaitingRead=null;setStatus("Test non lancé · "+state.label,AMBER);}
    }
    private UsbDevice findDevice() throws IOException {
        UsbDevice found = null;
        for (UsbDevice device : manager.getDeviceList().values()) if (AndroidLexiaUsb.matches(device)) {
            if (found != null) throw new IOException("Plusieurs Lexia connectés : n’en garder qu’un"); found = device;
        }
        return found;
    }
    private void refreshDevice() {
        if (running) return;
        if(FaultMonitorService.active()){updateText(deviceInfo,FaultMonitorService.status());setStatus(FaultMonitorService.status(),AMBER);return;}
        if(DiagnosticLease.held()) {
            waitingForPreviousSession=true;
            updateText(deviceInfo,"La session précédente termine sa sortie. Attendre la fermeture avant de reconnecter.");
            setStatus("Sortie de la session précédente en cours",AMBER);return;
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_USB_HOST)) {
            deviceInfo.setText("Android ne déclare pas la fonction USB hôte."); return;
        }
        try {
            UsbDevice device = findDevice();
            deviceInfo.setText(device == null ? "Branche le Lexia à la tablette avec l’adaptateur OTG. Voiture garée, contact mis."
                : "Lexia détecté · 103A:F008 · autorisation USB " + (manager.hasPermission(device)?"accordée":"à demander") + "\nLa détection seule ne confirme pas la communication avec le calculateur.");
        } catch(IOException e) { deviceInfo.setText(e.getMessage()); }
    }
    private String readInterruption(Exception e){
        String detail=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();
        String reason=detail.toLowerCase(Locale.ROOT).contains("usb")?"la liaison USB a échoué":detail.contains("état Lexia")?"une commande a été refusée par le Lexia ou le calculateur":"la réponse attendue n’a pas été validée";
        return "Lecture interrompue à "+ReadingTime.clock(System.currentTimeMillis())+" : "+reason+". Derniers relevés conservés. Détail : "+detail;
    }
    private void restoreScanEquipment(){
        java.util.Set<String> omitted=new java.util.LinkedHashSet<>();android.content.SharedPreferences prefs=getSharedPreferences("scan_equipment",MODE_PRIVATE);
        for(String id:ScanEquipment.MODULES)if(!prefs.getBoolean(scanEquipmentKey(vehiclePlan.key,id),true))omitted.add(id);
        scanEquipment=new ScanEquipment(omitted);
    }
    private String scanEquipmentKey(String plan,String id){return (VehicleEcuMemory.validVin(vehicleVin)?ClubBridge.normalizeVin(vehicleVin):"sans-vin")+":"+plan+":"+id;}
    private void restoreRememberedProfiles(){
        for(VehicleReadPlans.Module module:vehiclePlan.modules){
            String profile=VehicleEcuMemory.profile(rememberedEcus,module.id);
            if(module.candidates.contains(profile))identifiedProfiles.put(module.id,profile);
        }
    }
    private static void deferKeyboard(AlertDialog dialog,ViewGroup body){
        body.setFocusableInTouchMode(true);body.requestFocus();
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN|WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    private void readSelectedValues(){
        if(operationUi.open||globalRunning||permissionPending||monitorStartQueued)return;
        showTab(0);
        if(selectedModule.equals("motor")&&selectedFuel.isEmpty())fuelPromptAfterConnect=true;
        if(running&&!stopRequested&&selectedModule.equals(activeModule))return;
        inlineRequested=true;inlineStart(selectedModule,identifiedProfiles.containsKey(selectedModule)?"Reprise du calculateur mémorisé · vérification de la liaison…":"Connexion et lecture des valeurs…");connectSelected();
    }
    private void maybeAskFuelAfterConnect(){
        if(!fuelPromptAfterConnect||fuelDialogOpen||!resumed||destroyed||!running||stopRequested||!"motor".equals(activeModule)||recognizedReference==null)return;
        fuelPromptAfterConnect=false;fuelDialogOpen=true;
        new AlertDialog.Builder(this).setTitle("Carburant pour les comparatifs")
            .setMessage("Le moteur est connecté et les compteurs fonctionnent. Choisis le carburant pour enregistrer les prochains relevés, ou passe cette étape.")
            .setItems(FuelRecording.FUELS,(d,index)->{
                selectedFuel=FuelRecording.FUELS[index];
                if(!running||stopRequested||!"motor".equals(activeModule)||liveRecording!=null||recognizedReference==null)return;
                FuelRecording next=new FuelRecording(getFilesDir(),selectedFuel,diagnosticVin,diagnosticCarId,activeProfile,System.currentTimeMillis(),SystemClock.elapsedRealtime());
                next.identified(recognizedReference);liveRecording=next;
            }).setNegativeButton("Plus tard",null).setOnDismissListener(d->fuelDialogOpen=false).show();
    }
    private void updatePrimaryAction(){
        if(readValuesButton==null)return;
        boolean idle=!monitorStartQueued&&!operationUi.open&&!permissionPending&&!globalRunning&&switchProfile==null&&!queuedGlobal&&(!DiagnosticLease.held()||running&&!stopRequested);
        if(selectedTab==0){readValuesButton.setText(running&&!stopRequested&&selectedModule.equals(activeModule)?"Valeurs en direct":"▶ Lire");readValuesButton.setEnabled(idle);}
        else if(selectedTab==1){readValuesButton.setText("▶ Défauts");readValuesButton.setEnabled(idle&&hasReader(selectedModule));}
        else{
            boolean prepare=primaryFunction!=null&&canPrepareTest(primaryFunction);
            boolean enabled=primaryFunction!=null&&(prepare||functionAvailability(primaryFunction)==FunctionCatalog.Availability.AVAILABLE);
            readValuesButton.setText(selectedTab==2?"Lancer le test":TAB_NAMES[selectedTab]);readValuesButton.setEnabled(idle&&enabled);
        }
        readValuesButton.setAlpha(readValuesButton.isEnabled()?1f:.35f);
    }
    private void runPrimaryAction(){
        if(selectedTab==0){readSelectedValues();return;}
        if(selectedTab==1){readSelectedFaults();return;}
        if(primaryFunction!=null)runFunction(primaryFunction);
    }
    private void readSelectedFaults(){
        if(operationUi.open||globalRunning||permissionPending||operationPending)return;
        requestFaultResults();
        if(running&&!stopRequested&&selectedModule.equals(activeModule)){
            if(session!=null){rereadFaults();return;}
            boolean queued=false;
            if(genericSession!=null){genericSession.rereadFaults();queued=true;}
            else if(otherSession!=null)queued=otherSession.queueFaultRead();
            if(queued){operationUi.scan(selectedModule);inlineStart(selectedModule,"Lecture des défauts…");controls();}
            else setStatus("Lecture des défauts indisponible pendant l’opération en cours",AMBER);
            return;
        }
        inlineRequested=true;inlineStart(selectedModule,"Lecture des défauts du calculateur sélectionné…");connectSelected();
    }
    private void connectSelected() {
        if (operationUi.open || permissionPending || running&&selectedModule.equals(activeModule)) return;
        if(linkResetRequired){confirmLinkReset(this::connectSelected);return;}
        if(contactCycleRequired){confirmContactCycle(this::connectSelected);return;}
        final String module=selectedModule;
        if(identifiedProfiles.containsKey(module)){connectProfile(identifiedProfiles.get(module));return;}
        connectProfile(defaultProfile(module));
    }
    private String defaultProfile(String module) {if(identifiedProfiles.containsKey(module))return identifiedProfiles.get(module);VehicleReadPlans.Module m=vehiclePlan.module(module);return m!=null&&!m.candidates.isEmpty()?VehicleEcuMemory.prioritize(m.candidates,VehicleEcuMemory.profile(rememberedEcus,module)).get(0):"unavailable:"+module;}
    private void rememberEcu(String module,String profile,String identity){
        if(identity!=null&&!identity.isEmpty())identifiedProfiles.put(module,profile);
        VehicleEcuMemory.remember(this,diagnosticVin,vehiclePlan.key,module,profile,identity,MonitorReadSession.supported(profile));
        rememberedEcus=VehicleEcuMemory.load(this,vehicleVin);
    }
    private void markLinkResetRequired(String reason) {
        linkResetRequired=true;
        getSharedPreferences("lexia_link",MODE_PRIVATE).edit().putBoolean("resetRequired",true).putString("reason",reason).apply();
    }
    private void confirmLinkReset(Runnable next) {
        new AlertDialog.Builder(this).setTitle("Rétablir la liaison Lexia")
            .setMessage("La réponse précédente n’a pas été terminée. Réenvoyer une commande peut être refusé par le boîtier encore occupé.\n\nVoiture garée, moteur arrêté et contact coupé : débranche le Lexia de la prise voiture ET de l’USB pendant 10 secondes, puis rebranche les deux et remets le contact.\n\nLa fermeture de PP406 seule ne coupe pas l’alimentation du boîtier.")
            .setNegativeButton("Plus tard",null).setPositiveButton("Lexia débranché puis rebranché",(d,w)->{
                if(!resumed)return;
                linkResetRequired=false;contactCycleRequired=false;
                getSharedPreferences("lexia_link",MODE_PRIVATE).edit().putBoolean("resetRequired",false).remove("reason").apply();
                next.run();
            }).show();
    }
    private VciUsbProtocol liveProtocol(AndroidLexiaUsb link,String module) {
        return new VciUsbProtocol(link,20000,(elapsed,limit)->{
            String title=module.equals("global")?globalCurrentTitle:module;
            String step=(stopRequested||globalCancel&&module.equals("global")?"Arrêt demandé · attente de la réponse en cours\n":"")+title+" · Lexia occupé : "+elapsed/1000+" s · attente maximale "+limit/1000+" s";
            ui(()->{if(operationUi.open&&!operationUi.finished){operationUi.phase(step);refreshOperation();}else if(inlineModule!=null)inlineStep.setText(step);});
        });
    }
    private byte[] exchangeLive(VciUsbProtocol protocol,byte[] body)throws IOException {
        try{return protocol.exchange(body);}catch(IOException e){if(protocol.needsRecovery())markLinkResetRequired(e.getMessage());throw e;}
    }
    private static String scanModule(String profile) {return profile.startsWith("abs")?"abs":profile.startsWith("auto")?"automatic":profile;}
    private void retainScanReading(String profile,String reference,String raw,String faultResult) {
        String module=scanModule(profile),confirmed=OtherEcuSession.confirmedReadReference(profile,raw);
        operationUi.scan(module);
        otherIdentities.put(module,(confirmed!=null?"Référence identifiée : "+confirmed:reference.startsWith("profile:")?"Profil de lecture à confirmer : "+OtherEcuSession.profile(profile).title:"Référence : "+reference)+"\nIdentification brute : "+raw);
        markReading(module+":id");
        if(faultResult!=null){otherFaults.put(module,faultResult);markReading(module+":faults");rememberFaultReport(module,profile,faultResult);}
        updateOtherReading();controls();
    }
    private void retainMotorSample(int group,String hex,Map<String,Double> decoded){
        final long now=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();
        final Map<String,Double> sample=decoded==null?null:new LinkedHashMap<>(decoded);
        ui(()->{received++;measurementRevision++;rawGroups[group]=hex;motorSampleWallTime=wall;
            if(sample!=null)for(Map.Entry<String,Double> e:sample.entrySet())if(!e.getKey().equals("group")){values.put(e.getKey(),e.getValue());ages.put(e.getKey(),now);}
            StringBuilder lines=new StringBuilder();for(int g=0;g<4;g++)lines.append("G").append(g).append(" : ").append(rawGroups[g]==null?"non lu":rawGroups[g]).append('\n');raw.setText(lines.toString());
        });
    }
    private static String scanError(String message) {
        if(message==null)return "Échec de lecture · détail dans le journal";
        if(message.startsWith("Initialisation de profil non reconnue"))return "Réponse incompatible avec cette variante";
        if(message.startsWith("Référence de")||message.startsWith("Référence moteur"))return "Référence différente du profil pris en charge";
        return message;
    }
    private void confirmContactCycle(Runnable next) {
        new AlertDialog.Builder(this).setTitle("Fin de lecture du verrouillage")
            .setMessage("Le profil Kiekert impose de couper puis remettre le contact avant une autre session. Fais cette opération voiture garée, moteur arrêté.")
            .setNegativeButton("Annuler",null).setPositiveButton("Contact coupé puis remis",(d,w)->{if(resumed){contactCycleRequired=false;next.run();}}).show();
    }
    private void connectGlobal() {
        if(!resumed||operationUi.open||permissionPending||globalRunning)return;
        if(!vehicleContextReady()){requestVehicleContext(this::connectGlobal);return;}
        applyArchitecturePlan();
        if(linkResetRequired){confirmLinkReset(this::connectGlobal);return;}
        if(contactCycleRequired){confirmContactCycle(this::connectGlobal);return;}
        if(running){requestStop();queuedGlobal=true;setStatus("Fermeture de la lecture avant la recherche globale",AMBER);return;}
        if(scanContinuation==null){refreshClub(this::chooseScanEquipment);return;}
        connectGlobalPrepared();
    }
    private void chooseScanEquipment() {
        if(scanEquipmentDialog!=null)return;
        final String planKey=vehiclePlan.key;
        android.content.SharedPreferences prefs=getSharedPreferences("scan_equipment",MODE_PRIVATE);
        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(8),dp(20),dp(8));
        content.addView(text(vehiclePlan.label+"\nCoche les équipements présents ou incertains. Décoche uniquement ceux dont tu es sûr de l’absence.\nLes choix sont mémorisés pour ce profil : vérifie-les si tu changes de voiture.",16,WHITE));
        EditText vinInput=new EditText(this);vinInput.setSingleLine();vinInput.setHint("VIN obligatoire · 17 caractères");vinInput.setText(vehicleVin);content.addView(vinInput);
        EditText damInput=new EditText(this);damInput.setSingleLine();damInput.setHint("OPR / DAM · conseillé, facultatif");damInput.setText(vehicleDam);content.addView(damInput);
        Button garage=button(garageLabel(),null);content.addView(garage,1);
        garage.setOnClickListener(v->showGarageChooser(()->{vinInput.setText(vehicleVin);damInput.setText(vehicleDam);garage.setText(garageLabel());}));
        content.addView(text("Le VIN est obligatoire pour associer l’analyse à la bonne voiture. L’OPR est conseillé. Le type de multiplexage doit être confirmé s’il n’est pas connu.",13,MUTED));
        java.util.Map<String,CheckBox> choices=new java.util.LinkedHashMap<>();
        for(int i=0;i<ScanEquipment.MODULES.length;i++) {
            String id=ScanEquipment.MODULES[i];if(vehiclePlan.module(id)==null)continue;
            CheckBox box=new CheckBox(this);box.setText(ScanEquipment.TITLES[i]);box.setTextColor(WHITE);box.setTextSize(18);
            box.setChecked(prefs.getBoolean(scanEquipmentKey(planKey,id),true));choices.put(id,box);content.addView(box);
            content.addView(text(ScanEquipment.DESCRIPTIONS[i],13,MUTED));
        }
        content.addView(text("Le moteur, l’ABS, les airbags et les autres calculateurs restent recherchés. Un équipement décoché disparaît du menu et ne sera pas recherché. En cas de doute, laisse-le coché.",14,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        scanEquipmentDialog=new AlertDialog.Builder(this).setTitle("Équipements à rechercher").setView(scroll)
            .setNegativeButton("Annuler",null)
            .setNeutralButton("Je ne sais pas : tout rechercher",(d,w)->{
                if(!resumed||!planKey.equals(vehiclePlan.key))return;
                if(!scanIdentityReady(vinInput.getText().toString(),damInput.getText().toString()))return;
                android.content.SharedPreferences.Editor edit=prefs.edit();for(String id:ScanEquipment.MODULES)edit.putBoolean(scanEquipmentKey(planKey,id),true);edit.apply();
                applyVehicleIdentity(vinInput.getText().toString(),damInput.getText().toString());scanEquipment=ScanEquipment.searchAll();rebuildModuleButtons();connectGlobalPrepared();
            })
            .setPositiveButton("Lancer le scan",(d,w)->{
                if(!resumed||!planKey.equals(vehiclePlan.key))return;
                if(!scanIdentityReady(vinInput.getText().toString(),damInput.getText().toString()))return;
                java.util.Set<String> omitted=new java.util.LinkedHashSet<>();android.content.SharedPreferences.Editor edit=prefs.edit();
                for(java.util.Map.Entry<String,CheckBox> e:choices.entrySet()){boolean search=e.getValue().isChecked();edit.putBoolean(scanEquipmentKey(planKey,e.getKey()),search);if(!search)omitted.add(e.getKey());}
                edit.apply();applyVehicleIdentity(vinInput.getText().toString(),damInput.getText().toString());scanEquipment=new ScanEquipment(omitted);rebuildModuleButtons();connectGlobalPrepared();
            }).create();
        scanEquipmentDialog.setOnDismissListener(d->scanEquipmentDialog=null);deferKeyboard(scanEquipmentDialog,content);scanEquipmentDialog.show();
    }
    private void connectGlobalPrepared() {
        if(!resumed||operationUi.open||permissionPending||globalRunning||running||destroyed)return;
        pendingGlobal=true;
        try {
            UsbDevice device=findDevice();
            if(device==null){pendingGlobal=false;refreshDevice();setStatus("Branche le Lexia en OTG pour rechercher les calculateurs",AMBER);return;}
            if(manager.hasPermission(device))beginGlobal(device);
            else {
                requestUsbPermission(device);
                setStatus("Autorise PP406 à accéder au Lexia pour le scan global",AMBER);
            }
        }catch(Exception e){pendingGlobal=false;permissionPending=false;setStatus("Recherche impossible : "+e.getMessage(),AMBER);controls();}
    }
    private void connectProfile(String profile) { connectProfile(profile,false); }
    private void connectProfile(String profile,boolean automatic) {
        if(permissionPending||!resumed||operationUi.open)return;
        if(!vehicleContextReady()){inlineRequested=false;inlineFinish(selectedModule,"VIN requis avant diagnostic");requestVehicleContext(this::connectSelected);return;}
        if(applyArchitecturePlan()){inlineRequested=false;inlineFinish(selectedModule,"Véhicule sélectionné");handler.post(automatic?this::autoConnectVisible:this::connectSelected);return;}
        if(selectedModule.equals("motor")&&selectedFuel.isEmpty())fuelPromptAfterConnect=true;
        if(!running&&!DiagnosticLease.held()&&(!selectedCarId.equals(diagnosticCarId)||!vehicleVin.equals(diagnosticVin))){diagnosticCarId=selectedCarId;diagnosticVin=vehicleVin;clearScanReadings();restoreRememberedProfiles();}
        if(linkResetRequired){if(inlineRequested){inlineRequested=false;inlineFinish(selectedModule,"Résultats conservés · liaison Lexia à rétablir");return;}confirmLinkReset(()->connectProfile(profile));return;}
        if(running) {
            if(selectedModule.equals(activeModule))return;
            if(activeModule.equals("locking")){inlineRequested=false;inlineFinish(selectedModule,"Résultats conservés · couper puis remettre le contact après le verrouillage");setStatus("Arrête la lecture Kiekert puis coupe le contact avant de changer de calculateur",AMBER);return;}
            String target=selectedModule,fuel=selectedFuel;boolean quiet=inlineRequested;FunctionCatalog.Action wantedTest=testAwaitingRead;requestStop();testAwaitingRead=wantedTest;selectedFuel=fuel;inlineRequested=quiet;switchProfile=profile;switchModule=target;switchAutomatic=automatic;
            setStatus("Fermeture de "+activeModule+" avant connexion de "+target,AMBER);return;
        }
        if(!profile.equals("motor")&&OtherEcuSession.profile(profile)==null&&!vehiclePlans.generic(profile)) {
            inlineRequested=false;inlineFinish(selectedModule,"Lecture indisponible pour cette variante");
            setStatus("Pilote de ce calculateur en préparation",AMBER);return;
        }
        pendingModule=selectedModule;pendingProfile=profile;
        pendingGlobal=false;
        try {
            UsbDevice device = findDevice();
            if (device == null) {inlineRequested=false;inlineFinish(selectedModule,"Lexia absent · derniers relevés conservés");refreshDevice(); setStatus("Lexia absent du port USB de la tablette",AMBER); return; }
            if (manager.hasPermission(device)) begin(device);
            else {
                requestUsbPermission(device);
                setStatus("Autorise PP406 à accéder au Lexia",AMBER);
            }
        } catch(Exception e) { permissionPending = false;inlineRequested=false;inlineFinish(selectedModule,"Connexion impossible · derniers relevés conservés");setStatus("Connexion impossible : " + e.getMessage(),AMBER); controls(); }
    }
    private void requestUsbPermission(UsbDevice device) {
        pending=device;permissionPending=true;controls();
        long token=usbPermission.request(device.getDeviceName());
        Intent intent=new Intent(permissionAction).setPackage(getPackageName()).putExtra("requestToken",token);
        int flags=PendingIntent.FLAG_CANCEL_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
        manager.requestPermission(device,PendingIntent.getBroadcast(this,0,intent,flags));
    }
    private void continueUsbPermission() {
        if(!resumed||destroyed||running||operationUi.open||DiagnosticLease.held()||!usbPermission.approved())return;
        UsbDevice device=manager.getDeviceList().get(usbPermission.device());
        UsbPermissionFlow.Ready result=usbPermission.resume(resumed,device!=null,device!=null&&manager.hasPermission(device));
        if(result==UsbPermissionFlow.Ready.NONE)return;
        permissionPending=false;pending=null;
        android.util.Log.i("PP406UsbPermission","global resume="+result);
        if(result==UsbPermissionFlow.Ready.START)begin(device);
        else {pendingGlobal=false;inlineRequested=false;if(inlineModule!=null)inlineFinish(inlineModule,"Accès USB interrompu · derniers relevés conservés");setStatus(result==UsbPermissionFlow.Ready.DETACHED?"Lexia débranché avant le début de la lecture · rebranche-le en OTG":"Accès USB retiré par Android · retouche Connecter et lire pour autoriser",AMBER);controls();}
    }
    private void requestStop() { testAwaitingRead=null;pendingOutsideRead=false;selectedFuel="";inlineRequested=false;if(inlineModule!=null)inlineFinish(inlineModule,"Fermeture de session · résultats conservés");usbPermission.cancel();permissionPending=false;pending=null;pendingGlobal=false;queuedGlobal=false;scanContinuation=null;globalCancel=true;switchProfile=null;switchModule=null;stopRequested = true; GlobalEcuScan.DecisionGate choice=engineChoiceGate;if(choice!=null)choice.choose(GlobalEcuScan.Decision.STOP);if(engineChoiceDialog!=null){engineChoiceDialog.dismiss();engineChoiceDialog=null;} Mp70Session current = session; if (current != null) current.stop(); OtherEcuSession other=otherSession;if(other!=null)other.stop();EngineReadSession generic=genericSession;if(generic!=null)generic.stop(); if(operationUi.open&&!operationUi.finished)operationUi.phase("Arrêt demandé · attente de la fin de procédure et de la fermeture de session"); if(start!=null) controls(); }
    private void continueModuleSwitch() {
        continueUsbPermission();
        if(returnToStartupAfterClose&&!running&&!DiagnosticLease.held()&&resumed&&!destroyed){
            returnToStartupAfterClose=false;
            Intent startup=new Intent(this,StartupActivity.class);
            if(!selectedCarId.isEmpty())startup.setData(new android.net.Uri.Builder().scheme("pp406").authority("diagnostic").appendQueryParameter("carId",selectedCarId).build());
            startActivity(startup);finish();return;
        }
        if(exitAfterClose&&!running&&!DiagnosticLease.held()){exitAfterClose=false;finish();return;}
        if(queuedGlobal&&!running&&!DiagnosticLease.held()&&resumed&&!destroyed) {queuedGlobal=false;connectGlobal();return;}
        if(switchProfile==null||running||DiagnosticLease.held())return;
        String target=switchModule,profile=switchProfile;boolean automatic=switchAutomatic;switchProfile=null;switchModule=null;switchAutomatic=false;
        if(resumed&&!destroyed&&selectedModule.equals(target))connectProfile(profile,automatic);
    }
    private boolean canConnectForRead(FunctionCatalog.Action action) {
        boolean read=action.id.endsWith("_faults")||action.id.endsWith("_values")||action.id.endsWith("_identify");
        if(!action.implemented||!read||operationUi.open||permissionPending||switchProfile!=null)return false;
        String module=FunctionCatalog.module(action);
        return hasReader(module)&&(!running||!activeModule.equals(module));
    }
    @Override public void onBackPressed(){navigateBack();}
    private void navigateBack() {
        if(operationDialog!=null&&operationUi.open) {
            backAfterClose=true;
            if(!operationUi.mayAcknowledge(coreOperationBusy(),operationClosing())) {
                requestStop();operationUi.phase("Retour demandé · fermeture propre de l’opération en cours");
            }
            refreshOperation();return;
        }
        DiagnosticNavigation.Page previous=navigation.back();
        if(previous!=null){navigatingBack=true;selectedModule=previous.module;showTab(previous.tab);navigatingBack=false;controls();return;}
        if(FaultMonitorService.active()){finish();return;}
        if(running||DiagnosticLease.held()){exitAfterClose=true;requestStop();setStatus("Retour au menu · fermeture de la session en cours",AMBER);return;}
        finish();
    }
    private void showVehicleFallback() {
        if(running||DiagnosticLease.held()||!resumed)return;
        if(!vehicleContextReady()){requestVehicleContext(this::showVehicleFallback);return;}
        startActivityForResult(new Intent(this,VehicleRecoveryActivity.class)
            .putExtra("contactCycleRequired",contactCycleRequired).putExtra("resumeGlobalScan",scanContinuation!=null).putExtra("fullScan",true),VEHICLE_CHOICE);
    }
    private GlobalEcuScan.Decision awaitEngineChoice(GlobalEcuScan.Row engine)throws IOException {
        GlobalEcuScan.DecisionGate gate=new GlobalEcuScan.DecisionGate();engineChoiceGate=gate;
        if(stopRequested||globalCancel||!resumed)gate.choose(GlobalEcuScan.Decision.STOP);
        ui(()->showEngineChoice(gate,engine.detail));
        try{return gate.await();}
        catch(InterruptedException e){Thread.currentThread().interrupt();gate.choose(GlobalEcuScan.Decision.STOP);throw new IOException("Choix moteur interrompu",e);}
        finally{engineChoiceGate=null;}
    }
    private void showEngineChoice(GlobalEcuScan.DecisionGate gate,String detail) {
        if(!resumed||globalCancel||stopRequested||gate!=engineChoiceGate||!gate.pending()){gate.choose(GlobalEcuScan.Decision.STOP);return;}
        engineChoiceDialog=new AlertDialog.Builder(this).setTitle("Calculateur moteur non reconnu")
            .setMessage(detail+"\n\nLa recherche est en pause avant les autres calculateurs. Choisis le véhicule pour essayer un autre profil moteur, ou ignore pour poursuivre le scan normal.")
            .setPositiveButton("Choisir le véhicule",(d,w)->gate.choose(GlobalEcuScan.Decision.SELECT_VEHICLE))
            .setNegativeButton("Ignorer et continuer",(d,w)->gate.choose(GlobalEcuScan.Decision.CONTINUE))
            .setNeutralButton("Arrêter",(d,w)->gate.choose(GlobalEcuScan.Decision.STOP))
            .setCancelable(false).create();
        engineChoiceDialog.setOnDismissListener(d->{gate.choose(GlobalEcuScan.Decision.STOP);if(engineChoiceDialog==d)engineChoiceDialog=null;});
        engineChoiceDialog.show();
    }
    private void ui(Runnable action) { handler.post(() -> { if (!destroyed) action.run(); }); }
    private byte[] asset(String name) throws IOException {
        try (InputStream in = getAssets().open(name); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while((count=in.read(buffer))!=-1) out.write(buffer,0,count); return out.toByteArray();
        }
    }
    private void beginGlobal(UsbDevice device) {
        if(running||!resumed)return;
        DiagnosticLease.Token ownership=DiagnosticLease.acquire();
        if(ownership==null){waitingForPreviousSession=true;setStatus("Attendre la fermeture de la session précédente",AMBER);return;}
        final VehicleReadPlans.Plan plan=vehiclePlan;
        final ScanEquipment equipment=scanEquipment;
        if(scanContinuation==null){diagnosticCarId=selectedCarId;diagnosticVin=vehicleVin;}
        final JSONObject priorEcus=VehicleEcuMemory.load(this,diagnosticVin);
        final GlobalEcuScan.Pass scan=scanContinuation!=null?scanContinuation:new GlobalEcuScan.Pass(plan.rows(),equipment);scanContinuation=null;
        globalTotal=plan.modules.size();
        boolean resuming=scan.complete()>0;
        pendingGlobal=false;globalCancel=false;stopRequested=false;running=true;globalRunning=true;globalCompleted=scan.complete();
        recognizedReference=null;recognizedOtherReference=null;
        if(!resuming)clearScanReadings();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        beginOperation("global","Recherche des calculateurs","Ouverture USB · recherche en lecture seule");
        new Thread(()->{
            String outcome="Recherche terminée · choisis ensuite un calculateur et sa fonction de lecture";
            try {
                final String[] probing={"startup"},probingModule={"startup"};
                try(SessionJournal log=openJournal("usb-global")) {
                    log(log,"start","mode","global_read_only","vehicle_plan",plan.key,"app",BuildInfo.VERSION);
                    log(log,"scan_equipment","omitted_by_user",equipment.omitted().toString());
                    try(AndroidLexiaUsb link=new AndroidLexiaUsb(manager,device,(direction,data)->
                        SensitiveDiagnostics.recordTransfer(probingModule[0],(dir,bytes)->log(log,"usb","profile",probing[0],"direction",dir,"hex",VciUsbProtocol.hex(bytes)),direction,data))) {
                        VciUsbProtocol protocol=liveProtocol(link,"global");
                        final Map<String,String> resolved=new LinkedHashMap<>();
                        List<GlobalEcuScan.Row> scanRows=GlobalEcuScan.run(scan,new GlobalEcuScan.Runner(){
                            public boolean stopped(){return globalCancel||stopRequested||!resumed;}
                            public GlobalEcuScan.Result probe(String profile)throws IOException {
                                String module=profile.substring(4);VehicleReadPlans.Module group=plan.module(module);
                                if(group==null||group.candidates.isEmpty())return new GlobalEcuScan.Result(GlobalEcuScan.State.SKIPPED,"Transport ou format à intégrer · détails dans la page du calculateur",false);
                                GlobalEcuScan.Result last=null;List<String> attempts=new ArrayList<>();
                                List<String> candidates=VehicleEcuMemory.prioritize(group.candidates,VehicleEcuMemory.profile(priorEcus,module));
                                for(int i=0;i<candidates.size();i++){
                                    if(stopped())return new GlobalEcuScan.Result(GlobalEcuScan.State.INTERRUPTED,"Recherche arrêtée",true);
                                    String candidate=candidates.get(i);String title=vehiclePlans.title(candidate);
                                    String step=group.title+" · profil "+(i+1)+"/"+group.candidates.size()+" · "+title;
                                    globalCurrentTitle=step;ui(()->{operationUi.phase(step);refreshOperation();});
                                    last=probeOne(candidate,module);attempts.add(title+" : "+last.detail);
                                    if(last.state==GlobalEcuScan.State.IDENTIFIED||last.state==GlobalEcuScan.State.READABLE){resolved.put(module,candidate);return last;}
                                    if(last.abort)return last;
                                }
                                log(log,"module_no_match","module",module,"attempts",attempts.toString());
                                final String issue=group.candidates.size()+" profils essayés sans identification · "+(last==null?"aucun résultat":last.detail);
                                ui(()->readIssues.put(module,issue));
                                return new GlobalEcuScan.Result(last==null?GlobalEcuScan.State.NO_RESPONSE:last.state,issue,false);
                            }
                            private GlobalEcuScan.Result probeOne(String profile,String module)throws IOException {
                                probing[0]=profile;
                                probingModule[0]=module;
                                final boolean[] identified={false},responded={false},lost={false},finishBad={false},closed={false};
                                final String[] readReference={null};
                                Mp70Session.Transport transport=body->{
                                    try{return exchangeLive(protocol,body);}catch(IOException e){lost[0]=true;throw e;}
                                };
                                try {
                                    if(vehiclePlans.generic(profile)){
                                        EngineReadSession current=genericReader(profile,module,transport,log,raw->{identified[0]=true;readReference[0]=raw;});
                                        genericSession=current;if(stopped())current.stop();
                                        try{current.run(1);}finally{closed[0]=current.mayTryAnotherProfile();finishBad[0]=!closed[0];}
                                    }else if(profile.equals("motor")) {
                                        Mp70Session current=new Mp70Session(transport,new Mp70Session.Listener(){
                                            public void record(String name,byte[] req,byte[] res,int status,byte[] data)throws IOException {
                                                if(SensitiveDiagnostics.hideRaw(module))log(log,"command","profile",profile,"name",name,"status",status);
                                                else log(log,"command","profile",profile,"name",name,"request",VciUsbProtocol.hex(req),"response",VciUsbProtocol.hex(res),"status",status,"data",VciUsbProtocol.hex(data));
                                                if(name.startsWith("context:")&&status==1){final String context="\nContexte "+name.substring(8)+" : "+VciUsbProtocol.hex(data);ui(()->{faultText+=context;rememberFaultReport("motor","motor",faultText,true);renderMotorFaults();});}
                                                if((name.equals("init")||name.equals("identify"))&&status==1&&data.length>0)responded[0]=true;
                                                if(name.equals("finish")){closed[0]=status==1&&VciUsbProtocol.hex(data).equals("0f");finishBad[0]=!closed[0];}
                                            }
                                            public void state(String text){}
                                            public void values(int group,String hex,Map<String,Double> decoded){retainMotorSample(group,hex,decoded);}
                                            public void faults(List<String> codes){
                                                String result=EcuFaults.motor(FunctionCatalog.MP70_REFERENCE,codes);
                                                ui(()->{operationUi.scan("motor");faultText=result;rememberFaultReport("motor","motor",result);faultStamp.setText("Défauts obtenus lors du scan global · "+new SimpleDateFormat("HH:mm:ss",Locale.FRANCE).format(new Date()));renderMotorFaults();});
                                            }
                                            public void identified(String reference){identified[0]=true;readReference[0]=reference;ui(()->rememberEcu("motor","motor",reference));}
                                            public void operation(String kind,String message){
                                                if(kind.equals("faults_unavailable")){ui(()->{operationUi.scan("motor");faultText=message;faultReportIssue("motor",message);renderMotorFaults();});}
                                            }
                                        },asset("mp70-coupler.bin"),asset("mp70-program.bin"));
                                        session=current;current.setMeasurementGroups(new int[]{2,0});if(stopped())current.stop();current.run(1);
                                    } else {
                                        final String[] scanFaults={null};
                                        OtherEcuSession current=new OtherEcuSession(profile,transport,new OtherEcuSession.Listener(){
                                            public void record(String name,byte[] req,byte[] res,int status,byte[] data)throws IOException {
                                                log(log,"command","profile",profile,"name",name,"request",VciUsbProtocol.hex(req),"response",VciUsbProtocol.hex(res),"status",status,"data",VciUsbProtocol.hex(data));
                                                if((name.equals("init")||name.equals("identify"))&&status==1&&data.length>0)responded[0]=true;
                                                if(profile.equals("locking")&&name.equals("init"))contactCycleRequired=true;
                                            }
                                            public void state(String text){}
                                            public void identified(String reference,String raw){
                                                identified[0]=true;readReference[0]=OtherEcuSession.confirmedReadReference(profile,raw);
                                                ui(()->rememberEcu(module,profile,raw));
                                                if(readReference[0]==null&&!reference.startsWith("profile:"))readReference[0]=reference;
                                                final String result=scanFaults[0];ui(()->retainScanReading(profile,reference,raw,result));
                                            }
                                            public void identityObserved(String reference,String raw){
                                                String confirmed=OtherEcuSession.confirmedReadReference(profile,raw);
                                                if(confirmed!=null)readReference[0]=confirmed;else if(!reference.startsWith("profile:"))readReference[0]=reference;
                                                ui(()->retainScanReading(profile,reference,raw,null));
                                            }
                                            public void values(String text){ui(()->{otherValues.put(module,text);markReading(module+":values");updateOtherReading();});}
                                            public void measurements(Map<String,Double> sample){if(module.equals("climate"))retainClimateSample(sample);}
                                            public void faults(String text){scanFaults[0]=text;}
                                            public void faultsUnavailable(String text){scanFaults[0]=text;}
                                        },asset(profile+"-coupler.bin"),asset(profile+"-program.bin"));
                                        otherSession=current;if(stopped())current.stop();
                                        try{current.run(1);}finally{closed[0]=current.mayTryAnotherProfile();finishBad[0]=!closed[0];}
                                    }
                                    if(stopped())return new GlobalEcuScan.Result(GlobalEcuScan.State.INTERRUPTED,"Recherche interrompue",true);
                                    boolean discovery=readReference[0]==null;
                                    boolean generic=vehiclePlans.generic(profile);
                                    return new GlobalEcuScan.Result(generic?GlobalEcuScan.State.READABLE:discovery?GlobalEcuScan.State.RESPONSE:GlobalEcuScan.State.IDENTIFIED,
                                        generic?"Communication cohérente · "+vehiclePlans.title(profile)+" · variante à confirmer":discovery?"Réponse reçue · profil à confirmer":"Identifié · "+readReference[0],false);
                                }catch(IOException e){
                                    log(log,"probe_error","profile",profile,"message",e.getMessage(),"transport_failed",lost[0]);
                                    ui(()->{readIssues.put(module,scanError(e.getMessage()));updateOtherReading();});
                                    boolean abort=lost[0]||finishBad[0]||identified[0]&&!closed[0]||stopped();
                                    boolean known=readReference[0]!=null;
                                    return new GlobalEcuScan.Result(known?(vehiclePlans.generic(profile)?GlobalEcuScan.State.READABLE:GlobalEcuScan.State.IDENTIFIED):abort?GlobalEcuScan.State.INTERRUPTED:responded[0]?GlobalEcuScan.State.RESPONSE:GlobalEcuScan.State.NO_RESPONSE,
                                        (known?"Identifié · "+readReference[0]+" · suite interrompue : ":abort?"Interrompu · ":responded[0]?"Réponse à vérifier · ":"Pas de réponse exploitable · ")+scanError(e.getMessage()),abort);
                                }finally{session=null;otherSession=null;genericSession=null;}
                            }
                        },(rows,complete,current)->{
                            String list=GlobalEcuScan.text(rows);
                            globalCurrentTitle=current;
                            Map<String,String> found=new LinkedHashMap<>(resolved);
                            log(log,"scan_progress","complete",complete,"current",current,"results",list);
                            ui(()->{identifiedProfiles.putAll(found);globalCompleted=complete;globalResults=list;operationFaultResult=list;operationUi.phase(current);globalSummary.setText(list);refreshOperation();});
                        },engine->{
                            log(log,"engine_choice","phase","waiting_before_other_ecus","engine_result",engine.detail);
                            GlobalEcuScan.Decision decision=awaitEngineChoice(engine);
                            log(log,"engine_choice","phase","selected","decision",decision.name());return decision;
                        });
                        if(scan.paused())outcome="Recherche en pause · choix du véhicule";
                        for(GlobalEcuScan.Row row:scanRows)if(row.state==GlobalEcuScan.State.INTERRUPTED){outcome="Recherche interrompue · les résultats déjà obtenus restent consultables";break;}
                    }
                    log(log,"end","message","global handle closed");
                }
            }catch(Exception e){outcome="Recherche interrompue : "+e.getMessage();}
            finally {
                session=null;otherSession=null;genericSession=null;running=false;globalRunning=false;ownership.close();
                final String result=outcome+(linkResetRequired?"\nLiaison non synchronisée · rétablir le Lexia avant une nouvelle lecture.":contactCycleRequired?"\nVerrouillage interrogé : couper puis remettre le contact avant une nouvelle session.":"");
                ui(()->{
                    if(engineChoiceDialog!=null){engineChoiceDialog.dismiss();engineChoiceDialog=null;}
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);operationUi.finish(result);showTab(selectedTab);setStatus(result,AMBER);refreshOperation();controls();refreshDevice();
                    if(scan.paused()&&!globalCancel&&!stopRequested&&resumed) {
                        // The prior ECU is finished and the USB lease released
                        // before the picker can start a different engine profile.
                        scanContinuation=scan;operationUi.acknowledge(false,false);
                        if(operationDialog!=null){operationDialog.dismiss();operationDialog=null;}
                        showVehicleFallback();
                    }
                });
            }
        },"PP406-global-read-only").start();
    }
    private void begin(UsbDevice device) {
        if(pendingGlobal){beginGlobal(device);return;}
        if(pendingOutsideRead){pendingOutsideRead=false;beginOutsideRead(device);return;}
        if (running || !resumed) return;
        final DiagnosticLease.Token ownership=DiagnosticLease.acquire();
        if(ownership==null){waitingForPreviousSession=true;setStatus("La session précédente termine sa sortie · attendre avant de reconnecter",AMBER);controls();return;}
        try {
        running = true; stopRequested = false; operationPending=false; recognizedReference=null;recognizedOtherReference=null;activeModule=pendingModule;activeProfile=pendingProfile;
        diagnosticCarId=selectedCarId;diagnosticVin=vehicleVin;
        operationUi.scan(activeModule);
        if(activeModule.equals("motor"))motorSessionStart=SystemClock.elapsedRealtime();
        if(activeModule.equals("climate"))climateSessionStart=SystemClock.elapsedRealtime();
        controls();
        setStatus("Ouverture du Lexia · derniers relevés conservés",AMBER); renderValues();
        boolean quiet=inlineRequested;inlineRequested=false;
        if(selectedTab==1)requestFaultResults();
        if(quiet)inlineStart(activeModule,"Ouverture USB · vérification du calculateur puis valeurs en direct…");
        else beginOperation(activeModule,selectedTab==1?"Lecture des défauts":"Lecture des valeurs","Ouverture USB et vérification du calculateur sélectionné");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final String module=activeModule;
        final VehicleReadPlans.Module climate=vehiclePlan.module("climate");
        final boolean outsideBeforeMotor=selectedTab==0&&climate!=null&&climate.candidates.contains("climate")&&(!identifiedProfiles.containsKey("climate")||"climate".equals(identifiedProfiles.get("climate")));
        outsideReadIssue="";outsidePolling=false;
        final FuelRecording recording=module.equals("motor")&&!selectedFuel.isEmpty()?new FuelRecording(getFilesDir(),selectedFuel,diagnosticVin,diagnosticCarId,activeProfile,System.currentTimeMillis(),SystemClock.elapsedRealtime()):null;
        liveRecording=recording;
        new Thread(() -> {
            try{if(vehiclePlans.generic(activeProfile))runGenericSession(device,module,activeProfile);else if(module.equals("motor"))runSession(device,outsideBeforeMotor);else runOtherSession(device,module);}
            finally{FuelRecording closing=liveRecording;liveRecording=null;if(closing!=null){closing.checkpoint();closing.close();TripBackups.kick(this,null,null);String issue=closing.issue();if(!issue.isEmpty())ui(()->logInfo.setText(issue));}ownership.close();}
        },"PP406-USB").start();
        }catch(RuntimeException|Error e){ownership.close();running=false;throw e;}
    }
    private static String commandLabel(String name){
        if(name.endsWith("init"))return "Initialisation de la liaison";
        if(name.equals("identify"))return "Identification du calculateur";
        if(name.equals("faults"))return "Lecture des défauts";
        if(name.startsWith("group"))return "Lecture des valeurs · groupe "+name.substring(5);
        if(name.equals("finish"))return "Fermeture de la session";
        if(name.equals("coupler")||name.equals("program"))return "Configuration de la communication";
        return "Vérification du Lexia";
    }
    private EngineReadSession genericReader(String profile,String module,Mp70Session.Transport transport,PrintWriter journal,java.util.function.Consumer<String> onIdentity)throws IOException {
        EngineReadSession.Profile definition;String coupler,program;
        try{JSONObject p=vehiclePlans.definition(profile);org.json.JSONArray names=p.getJSONArray("assets");definition=VehicleReadPlans.parse(p);coupler=names.getString(0);program=names.getString(1);}
        catch(org.json.JSONException e){throw new IOException("Définition de lecture invalide",e);}
        final String[] sessionIdentity={null};
        return new EngineReadSession(definition,asset(coupler),asset(program),transport,new EngineReadSession.Listener(){
            public void record(String name,byte[] request,byte[] response)throws IOException{log(journal,"command","profile",profile,"module",module,"name",name,"request",VciUsbProtocol.hex(request),"response",VciUsbProtocol.hex(response));}
            public void state(String message){
                String name=message.substring(message.lastIndexOf(" · ")+3);String step=vehiclePlans.title(profile)+" · "+commandLabel(name);
                ui(()->{if(globalRunning){operationUi.phase(step);refreshOperation();}else {setStatus(step,AMBER);if(module.equals(inlineModule))inlineStep.setText(step);if(operationUi.open&&!operationUi.finished)operationUi.phase(step);}});
            }
            public void identity(String raw){
                sessionIdentity[0]=raw;onIdentity.accept(raw);
                ui(()->{rememberEcu(module,profile,raw);if(!raw.equals(genericIdentities.get(module))){confirmedMp72.remove(module);genericMeasurements.remove(module);}genericIdentities.put(module,raw);otherIdentities.put(module,"Profil de communication : "+vehiclePlans.title(profile)+" · variante à confirmer\nIdentification brute : "+raw);markReading(module+":id");readIssues.remove(module);updateOtherReading();});
            }
            public void ready(){ui(()->{controls();updateOtherReading();});}
            public void measurements(int id,Map<String,Double> sample){if(id==13){FuelRecording recording=liveRecording;if(recording!=null&&Mp72Decoder.confirmedIdentity(sessionIdentity[0],confirmedMp72.get(module))){recording.identified(sessionIdentity[0]);recording.sample(id,sample,SystemClock.elapsedRealtime(),System.currentTimeMillis());}Map<String,Double> copy=new LinkedHashMap<>(sample);String identity=sessionIdentity[0];ui(()->{if(identity!=null&&identity.equals(confirmedMp72.get(module))){Map<String,Double> stored=genericMeasurements.get(module);if(stored==null){stored=new LinkedHashMap<>();genericMeasurements.put(module,stored);}stored.putAll(copy);}});}}
            public void values(String value){ui(()->{
                StringBuilder rendered=new StringBuilder();Map<String,Double> numbers=genericMeasurements.get(module);
                if(profile.equals("read:13:10")&&numbers!=null&&genericIdentities.get(module).equals(confirmedMp72.get(module))){
                    rendered.append("MP7.2 · mesures reçues · conversions à valider sur voiture\n\n");
                    for(String[] signal:Mp72Decoder.SIGNALS){Double number=numbers.get(signal[0]);if(number!=null)rendered.append(signal[1]).append(" : ").append(String.format(Locale.FRANCE,"%.1f",number)).append(' ').append(signal[2]).append('\n');}
                    rendered.append("\nRéponses brutes\n");
                }
                rendered.append(value);otherValues.put(module,rendered.toString());markReading(module+":values");updateOtherReading();
            });}
            public void faults(String value){ui(()->{
                operationUi.scan(module);otherFaults.put(module,value);markReading(module+":faults");rememberFaultReport(module,profile,value);if(!globalRunning)faultResultsReady=true;updateOtherReading();
                if(!globalRunning){inlineFinish(module,"Lecture des défauts terminée · relevé actualisé");setStatus("LECTURE RÉELLE · "+vehiclePlans.title(profile)+" · variante à confirmer",LIME);
                    if(operationUi.open&&!operationUi.finished){operationFaultResult=value;operationUi.finish("Identification et lecture initiale terminées");refreshOperation();}}
            });}
        },millis->{try{Thread.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Attente interrompue",e);}});
    }
    private void runGenericSession(UsbDevice device,String module,String profile){
        String outcome="Lecture arrêtée · session fermée";
        try{
            try(SessionJournal journal=openJournal("usb-reader")){
                log(journal,"start","mode","fixed_read_only","module",module,"profile",profile,"app",BuildInfo.VERSION);
                try(AndroidLexiaUsb link=new AndroidLexiaUsb(manager,device,(direction,data)->log(journal,"usb","direction",direction,"hex",VciUsbProtocol.hex(data)))){
                    VciUsbProtocol protocol=liveProtocol(link,module);EngineReadSession current=genericReader(profile,module,body->exchangeLive(protocol,body),journal,raw->{});
                    genericSession=current;if(stopRequested||!resumed)current.stop();current.run(0);
                }catch(Exception e){log(journal,"error","message",e.toString());throw e;}finally{log(journal,"end","message","handle closed");}
            }
        }catch(Exception e){outcome=readInterruption(e);}
        finally{
            genericSession=null;running=false;final String result=outcome;
            ui(()->{getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(result.startsWith("Lecture interrompue"))readIssues.put(module,result);inlineFinish(module,result);setStatus(result,AMBER);operationSessionEnded(module,result);controls();updateOtherReading();refreshDevice();});
        }
    }
    private void runOtherSession(UsbDevice device,String module) {
        final String profileId=activeProfile;
        String outcome=module.equals("locking")?"Lecture Kiekert arrêtée · commande de fin omise · couper le contact avant de changer de module":"Lecture arrêtée · session fermée";
        try {
            try(SessionJournal log=openJournal("usb-"+module)) {
                log(log,"start","module",module,"profile",profileId,"device",device.getDeviceName(),"api",Build.VERSION.SDK_INT,"app",BuildInfo.VERSION);
                try(AndroidLexiaUsb link=new AndroidLexiaUsb(manager,device,(direction,data)->
                    SensitiveDiagnostics.recordTransfer(module,(dir,bytes)->log(log,"usb","direction",dir,"hex",VciUsbProtocol.hex(bytes)),direction,data))) {
                    VciUsbProtocol protocol=liveProtocol(link,module);
                    OtherEcuSession current=new OtherEcuSession(profileId,body->exchangeLive(protocol,body),new OtherEcuSession.Listener() {
                        @Override public void record(String name,byte[] request,byte[] response,int status,byte[] data)throws IOException {
                            if(SensitiveDiagnostics.hideRaw(module))log(log,"command","module",module,"name",name,"status",status);
                            else log(log,"command","module",module,"name",name,"request",VciUsbProtocol.hex(request),"response",VciUsbProtocol.hex(response),"status",status,"data",VciUsbProtocol.hex(data));
                            ui(()->{if(module.equals(inlineModule))inlineStep.setText("Lecture · "+name);});
                        }
                        @Override public void state(String text){ui(()->{setStatus(text,AMBER);if(operationUi.open&&!operationUi.finished)operationUi.phase(text);});}
                        @Override public void identified(String reference,String raw){
                            final String confirmed=OtherEcuSession.confirmedReadReference(profileId,raw);
                            ui(()->rememberEcu(module,profileId,raw));
                            ui(()->{recognizedOtherReference=reference;otherIdentities.put(module,(confirmed!=null?"Référence identifiée : "+confirmed:reference.startsWith("profile:")?"Profil de lecture à confirmer : "+OtherEcuSession.profile(profileId).title:"Référence : "+reference)+"\nIdentification brute : "+raw);
                                markReading(module+":id");readIssues.remove(module);inlineFinish(module,"Lecture terminée · résultats actualisés");setStatus("LECTURE RÉELLE · "+module+" · "+reference,LIME);controls();});
                            ui(()->{faultResultsReady=true;if(operationUi.open&&!operationUi.finished)operationUi.finish("Identification et scan initial terminés · consulter le résultat de lecture");refreshOperation();});
                        }
                        @Override public void measurements(Map<String,Double> sample){if(module.equals("climate"))retainClimateSample(sample);}
                        @Override public void values(String text){ui(()->{otherValues.put(module,text);markReading(module+":values");updateOtherReading();});}
                        @Override public void faults(String text){ui(()->{otherFaults.put(module,text);rememberFaultReport(module,activeProfile,text);if(operationUi.open&&module.equals(operationUi.module)&&!operationUi.faultsHidden(module))operationFaultResult=text;markReading(module+":faults");updateOtherReading();controls();});}
                        @Override public void faultsUnavailable(String text){ui(()->{otherFaults.put(module,text);faultReportIssue(module,text);faultResultsReady=true;otherTimes.remove(module+":faults");otherWallTimes.remove(module+":faults");operationEvent(module,"faults_unavailable",text,null);updateOtherReading();controls();});}
                        @Override public void operation(String kind,String text)throws IOException {
                            log(log,"operation","module",module,"kind",kind,"message",text);
                            ui(()->{
                                boolean complete=kind.equals("clear_result");
                                setStatus(text,complete?LIME:AMBER);
                                operationEvent(module,kind,text,null);
                                if(kind.equals("faults_read")||kind.equals("faults_unavailable")){faultResultsReady=true;inlineFinish(module,text);}
                                if(kind.equals("clear_send")||kind.equals("clear_unknown")||kind.equals("clear_unverified"))faultReportIssue(module,text);
                                if(kind.startsWith("clear_")&&!complete) {
                                    otherTimes.remove(module+":faults");otherWallTimes.remove(module+":faults");
                                    otherFaults.put(module,text+"\nLe résultat après effacement n’est pas encore confirmé.");
                                }
                                updateOtherReading();controls();
                            });
                        }
                        @Override public void procedure(String id,String kind,String text)throws IOException {
                            log(log,"procedure","module",module,"id",id,"kind",kind,"message",text);
                            ui(()->{
                                String key=module+":op:"+id;
                                otherOperations.put(key,text);
                                markReading(key);
                                setStatus(text,kind.equals("procedure_result")?LIME:AMBER);
                                operationEvent(module,kind,text,id);
                                if(kind.equals("procedure_start")||kind.equals("procedure_unverified")||kind.equals("procedure_cancelled")) {
                                    otherTimes.remove(module+":faults");otherWallTimes.remove(module+":faults");
                                    otherFaults.put(module,"Procédure en cours ou résultat non confirmé · une relecture des défauts est requise.");
                                    faultReportIssue(module,"Procédure en cours ou résultat non confirmé · dernier relevé conservé");
                                }
                                updateOtherReading();controls();
                            });
                        }
                    },asset(profileId+"-coupler.bin"),asset(profileId+"-program.bin"));
                    otherSession=current;if(!resumed||stopRequested)current.stop();current.run(0);
                } catch(Exception e){log(log,"error","message",e.toString());throw e;}
                finally{log(log,"end","message","handle closed");}
            }
        }catch(Exception e){outcome=readInterruption(e);}
        finally {
            otherSession=null;running=false;String message=outcome;
            ui(()->{getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(message.startsWith("Lecture interrompue"))readIssues.put(module,message);inlineFinish(module,message);setStatus(message,AMBER);operationSessionEnded(module,message);controls();refreshDevice();});
        }
    }
    private void runSession(UsbDevice device,boolean outsideBeforeMotor) {
        String outcome="Lecture arrêtée · session fermée";
        try {
            try (SessionJournal log=openJournal("usb")) {
                log(log,"start","device",device.getDeviceName(),"api",Build.VERSION.SDK_INT,"app",BuildInfo.VERSION);
                try (AndroidLexiaUsb link = new AndroidLexiaUsb(manager,device,(direction,data) -> log(log,"usb","direction",direction,"hex",VciUsbProtocol.hex(data)))) {
                    VciUsbProtocol protocol = liveProtocol(link,"motor");
                    boolean scheduleOutside=outsideBeforeMotor&&readMotorOutside(body->exchangeLive(protocol,body),log);
                    Mp70Session current = new Mp70Session(body->exchangeLive(protocol,body),new Mp70Session.Listener() {
                        @Override public void plannedPause(boolean paused){FuelRecording recording=liveRecording;if(recording!=null)recording.plannedPause(paused,SystemClock.elapsedRealtime(),System.currentTimeMillis());}
                        @Override public void record(String name,byte[] request,byte[] response,int status,byte[] data) throws IOException {
                            log(log,"command","name",name,"request",VciUsbProtocol.hex(request),"response",VciUsbProtocol.hex(response),"status",status,"data",VciUsbProtocol.hex(data));
                            if (name.startsWith("context:")) ui(() -> { faultText += "\nContexte "+name.substring(8)+" : "+VciUsbProtocol.hex(data);rememberFaultReport("motor","motor",faultText,true);if(operationUi.open&&operationUi.module.equals("motor")&&!operationUi.faultsHidden("motor"))operationFaultResult=faultText;renderMotorFaults(); });
                        }
                        @Override public void state(String message) { ui(() -> {
                            setStatus(message,AMBER);
                            if(message.startsWith("Lecture moteur"))faultResultsReady=true;
                            if("motor".equals(inlineModule)){inlineStep.setText(message);if(message.startsWith("Lecture moteur"))inlineFinish("motor","Lecture terminée · valeurs en direct");}
                            if(operationUi.open&&!operationUi.finished) {
                                if(message.startsWith("Lecture moteur")){faultResultsReady=true;operationUi.finish("Identification et scan initial terminés · consulter le résultat de lecture");}
                                else operationUi.phase(message);
                                refreshOperation();
                            }
                        }); }
                        @Override public void identified(String reference) {
                            FuelRecording recording=liveRecording;if(recording!=null)recording.identified(reference);
                            ui(() -> { rememberEcu("motor","motor",reference);recognizedReference=reference;readIssues.remove("motor"); controls(); });
                        }
                        @Override public void values(int group,String hex,Map<String,Double> decoded) {
                            long now=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();
                            FuelRecording recording=liveRecording;if(recording!=null){recording.sample(group,decoded,now,wall);
                                if(group==2&&hex!=null&&hex.length()==48&&decoded.containsKey("speed_kmh"))try{recording.speedReference(Integer.parseInt(hex.substring(42,44),16),decoded.get("speed_kmh"),gpsSpeed==null?null:gpsSpeed.snapshot(now),now,wall);}catch(NumberFormatException ignored){}
                            }
                            ui(() -> {
                                received++;measurementRevision++; rawGroups[group]=hex;motorSampleWallTime=wall;
                                if(decoded!=null) for(Map.Entry<String,Double> entry:decoded.entrySet()) if(!entry.getKey().equals("group")) {
                                    values.put(entry.getKey(),entry.getValue()); ages.put(entry.getKey(),now);
                                }
                                StringBuilder lines=new StringBuilder();
                                for(int g=0;g<4;g++) lines.append("G").append(g).append(g==1||g==3?" · brut : ":" : ").append(rawGroups[g]==null?"en attente":rawGroups[g]).append('\n');
                                raw.setText(lines.toString());
                                if(!operationUi.open)setStatus("LECTURE EN DIRECT · moteur MP7.0",LIME);
                                renderValues(); // Present this actual response immediately.
                                maybeAskFuelAfterConnect();
                            });
                        }
                        @Override public void faults(List<String> codes) {
                            String clock=new SimpleDateFormat("HH:mm:ss",Locale.FRANCE).format(new Date());
                            ui(() -> { faultText=EcuFaults.motor(recognizedReference,codes);rememberFaultReport("motor","motor",faultText);if(operationUi.open&&operationUi.module.equals("motor")&&!operationUi.faultsHidden("motor"))operationFaultResult=faultText; if(!operationUi.faultsHidden("motor"))faultStamp.setText("Dernière lecture réelle : "+clock);renderMotorFaults(); });
                        }
                        @Override public void operation(String kind,String message) {
                            try { log(log,"operation","kind",kind,"message",message); }
                            catch(IOException e) { throw new java.io.UncheckedIOException(e); }
                            ui(() -> {
                                faultAction.setText(message);
                                operationEvent("motor",kind,message,null);
                                if(kind.equals("faults_read")||kind.equals("faults_unavailable")){faultResultsReady=true;inlineFinish("motor",message);}
                                if(kind.equals("clear_send"))faultReportIssue("motor","Effacement en cours · dernier relevé conservé");
                                if(kind.equals("clear_result")||kind.equals("clear_rejected")||kind.equals("clear_unknown")||kind.equals("clear_unverified")||kind.equals("faults_read")||kind.equals("faults_unavailable")) operationPending=false;
                                if(kind.equals("clear_unverified")||kind.equals("clear_unknown")||kind.equals("faults_unavailable")) {
                                    faultStamp.setText("Lecture complète non confirmée · relire les défauts");
                                    faultReportIssue("motor",message);renderMotorFaults();
                                }
                                controls();
                            });
                        }
                        @Override public void procedure(String id,String kind,String message)throws IOException {
                            log(log,"procedure","module","motor","id",id,"kind",kind,"message",message);
                            ui(()->{
                                otherOperations.put("motor:op:"+id,message);markReading("motor:op:"+id);
                                if(kind.equals("procedure_result")||kind.equals("procedure_rejected")||kind.equals("procedure_unverified")||kind.equals("procedure_cancelled"))operationPending=false;
                                setStatus(message,kind.equals("procedure_result")?LIME:AMBER);updateOtherReading();controls();
                                operationEvent("motor",kind,message,id);
                            });
                        }
                    },asset("mp70-coupler.bin"),asset("mp70-program.bin"));
                    session=current;current.setMeasurementGroups(MeasurementPages.groups());
                    if(scheduleOutside)current.setAuxiliaryRead(new PeriodicRead(3600000L,SystemClock::elapsedRealtime,()->readMotorOutside(body->exchangeLive(protocol,body),log)));

                    // If the user left during the brief USB-open stage, do not start.
                    if(!resumed || stopRequested) current.stop();
                    current.run(0);
                } catch(Exception e) { log(log,"error","message",e.toString()); throw e; }
                finally { log(log,"end","message","handle closed"); }
            }
        } catch(Exception e) { outcome=readInterruption(e); }
        finally {
            session=null; running=false;outsidePolling=false;
            String finalOutcome=outcome;
            ui(() -> {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                operationPending=false;
                inlineFinish("motor",finalOutcome);
                operationSessionEnded("motor",finalOutcome);
                setStatus(finalOutcome,AMBER); controls(); renderValues(); refreshDevice();
            });
        }
    }
    private void readOutsideSeparately(){
        if(running||DiagnosticLease.held()||operationUi.open||permissionPending){setStatus("Lecture moteur conservée · arrêter explicitement avant une lecture extérieure séparée",AMBER);return;}
        if(linkResetRequired){confirmLinkReset(this::readOutsideSeparately);return;}
        try{UsbDevice device=findDevice();if(device==null){setStatus("Branche le Lexia pour lire la température extérieure",AMBER);return;}
            pendingGlobal=false;pendingOutsideRead=true;if(manager.hasPermission(device)){pendingOutsideRead=false;beginOutsideRead(device);}else requestUsbPermission(device);
        }catch(IOException e){setStatus("Température extérieure : "+e.getMessage(),AMBER);}
    }
    private boolean readMotorOutside(Mp70Session.Transport transport,PrintWriter journal)throws IOException{
        if(stopRequested||!resumed)throw new IOException("Arrêt demandé");
        outsidePolling=true;ui(()->{setStatus("Lecture de la température extérieure · reprise moteur automatique",AMBER);controls();});
        OtherEcuSession reader=new OtherEcuSession("climate",transport::exchange,new OtherEcuSession.Listener(){
            public void record(String name,byte[] q,byte[] r,int status,byte[] d)throws IOException{log(journal,"outside_command","name",name,"status",status,"data",VciUsbProtocol.hex(d));}
            public void state(String s){}public void identified(String r,String raw){}public void values(String s){}public void faults(String s){}
            public void measurements(Map<String,Double> sample){retainClimateSample(sample);}
        },asset("climate-coupler.bin"),asset("climate-program.bin"));otherSession=reader;
        try{reader.readClimateSnapshot(()->stopRequested||!resumed);outsideReadIssue="";return true;}
        catch(IOException e){outsideReadIssue=e.getMessage();log(journal,"outside_unavailable","message",e.toString());if(!reader.mayTryAnotherProfile())throw e;return false;}
        finally{otherSession=null;outsidePolling=false;}
    }
    private void beginOutsideRead(UsbDevice device){
        if(running||!resumed)return;final DiagnosticLease.Token lease=DiagnosticLease.acquire();if(lease==null)return;
        running=true;stopRequested=false;activeModule="outside_read";outsidePolling=true;inlineStart("outside_read","Lecture séparée de la température extérieure…");controls();
        new Thread(()->{
            String outcome="Température extérieure lue · relevé conservé";
            try(SessionJournal journal=openJournal("usb-outside");AndroidLexiaUsb link=new AndroidLexiaUsb(manager,device,(direction,data)->log(journal,"usb","direction",direction,"hex",VciUsbProtocol.hex(data)))){
                VciUsbProtocol protocol=liveProtocol(link,"climate");
                OtherEcuSession reader=new OtherEcuSession("climate",body->exchangeLive(protocol,body),new OtherEcuSession.Listener(){
                    public void record(String name,byte[] q,byte[] r,int status,byte[] d)throws IOException{log(journal,"command","name",name,"status",status,"data",VciUsbProtocol.hex(d));}
                    public void state(String s){}public void identified(String r,String raw){}public void values(String s){}public void faults(String s){}
                    public void measurements(Map<String,Double> sample){retainClimateSample(sample);}
                },asset("climate-coupler.bin"),asset("climate-program.bin"));otherSession=reader;
                try{reader.readClimateSnapshot(()->stopRequested||!resumed);}finally{if(!reader.mayTryAnotherProfile())ui(()->markLinkResetRequired("Fin de lecture extérieure non confirmée"));}
            }catch(Exception e){outcome="Température extérieure non lue : "+e.getMessage();}
            finally{otherSession=null;running=false;outsidePolling=false;lease.close();final String result=outcome;ui(()->{inlineFinish("outside_read",result);setStatus(result,AMBER);controls();});}
        },"PP406-outside-separate").start();
    }
    private SessionJournal openJournal(String prefix) {
        SessionJournal journal=SessionJournal.open(getFilesDir(),getCacheDir(),prefix,message->ui(()->logInfo.setText(message)));
        ui(()->logInfo.setText(journal.description()));
        return journal;
    }
    private static void log(PrintWriter writer,String event,Object...pairs) throws IOException {
        try {
            JSONObject json=new JSONObject(); json.put("event",event); json.put("time_ms",System.currentTimeMillis()); json.put("elapsed_ms",SystemClock.elapsedRealtime());
            for(int i=0;i<pairs.length;i+=2) json.put(pairs[i].toString(),pairs[i+1]);
            writer.println(json.toString()); writer.flush();
        } catch(org.json.JSONException e) { throw new IOException(e); }
    }
    private static String[][] allDisplaySignals(){
        String[][] all=new String[DISPLAY_SIGNALS.length+ClimateDecoder.SIGNALS.length][];
        System.arraycopy(DISPLAY_SIGNALS,0,all,0,DISPLAY_SIGNALS.length);System.arraycopy(ClimateDecoder.SIGNALS,0,all,DISPLAY_SIGNALS.length,ClimateDecoder.SIGNALS.length);return all;
    }
    private void retainClimateSample(Map<String,Double> sample){
        final Map<String,Double> copy=new LinkedHashMap<>(sample);final long now=SystemClock.elapsedRealtime(),wall=System.currentTimeMillis();
        ui(()->{climateMeasurements.putAll(copy);for(String key:copy.keySet())climateAges.put(key,now);climateSampleWallTime=wall;});
    }
    private void renderClimateValues(long now){
        boolean active=running&&!stopRequested&&activeModule.equals("climate")&&otherSession!=null&&otherSession.isReady()&&!otherSession.isFaultReadPending();long latest=0;
        for(String[] signal:ClimateDecoder.SIGNALS){
            String key=signal[0];TextView field=fields.get(key),guide=valueGuides.get(key);Double number=climateMeasurements.get(key);Long stamp=climateAges.get(key);
            boolean fresh=active&&stamp!=null&&stamp>=climateSessionStart&&stamp<=now&&now-stamp<3000;
            if(stamp!=null)latest=Math.max(latest,stamp);
            updateNumber(signal,number,field);
            ValueAssessment assessment=ValueAssessment.range(number,fresh,lowAlerts.get(key),highAlerts.get(key));
            smoothAppearance(field,assessment.color(WHITE),number==null?.4f:1f,fresh);
            smoothAppearance(guide,assessment.color(MUTED),1f,fresh);
            updateText(guide,number==null?"Aucune mesure":!fresh?"Ancienne mesure · non évaluée":assessment.label+" · toucher");
        }
        updateText(age,latest==0?"Aucune mesure climatisation reçue":ReadingTime.label(climateSampleWallTime,latest,now,active));
    }
    private void renderOutsideValue(){
        String[] signal=ClimateDecoder.SIGNALS[0];Double value=climateMeasurements.get("outside_c");
        TextView field=fields.get("outside_c"),guide=valueGuides.get("outside_c");updateNumber(signal,value,field);
        ValueAssessment assessment=assessOutside();
        smoothAppearance(field,assessment.color(WHITE),value==null?.4f:1f,!outsidePolling);
        smoothAppearance(guide,assessment.color(MUTED),1f,!outsidePolling);
        updateText(guide,outsidePolling?"Lecture extérieure en cours…":value==null?"Lecture séparée · toucher":"Relevé "+ReadingTime.clock(climateSampleWallTime)+" · "+assessment.label);
    }
    private ValueAssessment assessOutside(){
        long now=SystemClock.elapsedRealtime();Long stamp=climateAges.get("outside_c");
        // This is the last hourly snapshot, not a simultaneous engine measurement.
        boolean recent=!outsidePolling&&climateSampleWallTime>0&&stamp!=null&&stamp<=now&&now-stamp<=3660000L;
        return ValueAssessment.range(climateMeasurements.get("outside_c"),recent,lowAlerts.get("outside_c"),highAlerts.get("outside_c"));
    }
    private void addValueField(LinearLayout parent,String[] signal){
        TextView value=text("—",32,WHITE);value.setTypeface(Typeface.DEFAULT,Typeface.BOLD);value.setGravity(Gravity.CENTER_VERTICAL);value.setSingleLine(true);
        fields.put(signal[0],value);parent.addView(value,new LinearLayout.LayoutParams(-1,0,1));parent.addView(text(signal[2],11,MUTED));
        TextView guide=text("—",10,MUTED);guide.setMaxLines(1);guide.setEllipsize(android.text.TextUtils.TruncateAt.END);valueGuides.put(signal[0],guide);parent.addView(guide);
    }
    private void showMeasurements(){
        if(measurementGrid==null)return;
        for(View card:valueCards.values())if(card.getParent() instanceof ViewGroup)((ViewGroup)card.getParent()).removeView(card);
        for(View dial:new View[]{rpmDial,speedDial})if(dial.getParent() instanceof ViewGroup)((ViewGroup)dial.getParent()).removeView(dial);
        measurementGrid.removeAllViews();chronoLapButton=null;boolean climate=selectedModule.equals("climate");
        boolean compact=needleMode&&!climate;
        for(View card:valueCards.values())card.setPadding(dp(12),dp(compact?6:8),dp(8),dp(compact?4:6));
        for(String[] signal:allDisplaySignals()){
            fields.get(signal[0]).setTextSize(compact?36:32);
            valueGuides.get(signal[0]).setVisibility(compact?View.GONE:View.VISIBLE);
        }
        String[] keys=climate?new String[]{"outside_c","evaporator_c","climate_coolant_c","sunlight_raw","climate_speed_kmh","climate_battery_v"}:MeasurementPages.keys();
        if(!climate&&needleMode){
            LinearLayout dials=new LinearLayout(this);measurementGrid.addView(dials,new LinearLayout.LayoutParams(-1,0,1));
            for(View dial:new View[]{rpmDial,speedDial}){
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-1,1);cell.setMargins(0,dp(5),dp(5),0);
                FrameLayout frame=new FrameLayout(this);dials.addView(frame,cell);frame.addView(dial,new FrameLayout.LayoutParams(-1,-1));
                if(dial==rpmDial){
                    LinearLayout chronoControls=new LinearLayout(this);chronoControls.setGravity(Gravity.CENTER_VERTICAL);
                    ChronoIcon chronometer=new ChronoIcon(this);
                    chronometer.setOnClickListener(v->showChronoMenu());chronoControls.addView(chronometer,new LinearLayout.LayoutParams(dp(46),dp(46)));
                    chronoLapButton=button("Chrono",v->{if(chrono.mode()==DriveChrono.Mode.LAP&&chrono.state()==DriveChrono.State.RUNNING)finishChronoLap();else showChronoMenu();});chronoLapButton.setTextSize(12);
                    chronoControls.addView(chronoLapButton,new LinearLayout.LayoutParams(dp(110),dp(46)));
                    FrameLayout.LayoutParams controls=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.RIGHT);frame.addView(chronoControls,controls);
                    chronoLapButton.setVisibility(chrono.state()==DriveChrono.State.RUNNING||chrono.state()==DriveChrono.State.WAITING?View.VISIBLE:View.GONE);
                }
            }
            keys=MeasurementPages.needleKeys();
        }
        int columns=climate?3:MeasurementPages.COLUMNS;
        for(int i=0;i<keys.length;i+=columns){
            LinearLayout row=new LinearLayout(this);
            measurementGrid.addView(row,compact?new LinearLayout.LayoutParams(-1,dp(MeasurementPages.NEEDLE_STRIP_HEIGHT_DP)):new LinearLayout.LayoutParams(-1,0,1));
            for(int j=i;j<Math.min(i+columns,keys.length);j++){
                LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-1,1);cell.setMargins(0,dp(5),dp(5),0);row.addView(valueCards.get(keys[j]),cell);
            }
        }
        // Layout changes only: no session, polling group, USB handle or recording is modified.
    }
    private static String chronoTime(long elapsed){return String.format(Locale.FRANCE,"%d:%02d.%02d",elapsed/60000,(elapsed/1000)%60,(elapsed/10)%100);}
    private void showChronoMenu(){
        if(chrono.state()==DriveChrono.State.RUNNING||chrono.state()==DriveChrono.State.WAITING){
            String state=chrono.state()==DriveChrono.State.WAITING?"En attente du départ":"En cours · "+chronoTime(chrono.elapsedMs(SystemClock.elapsedRealtime()));
            new AlertDialog.Builder(this).setTitle("Chrono · "+chrono.mode().label).setMessage(state)
                .setNegativeButton("Fermer",null).setPositiveButton("Annuler le chrono",(d,w)->{chrono.cancel();refreshChrono();}).show();return;
        }
        DriveChrono.Mode[] modes=DriveChrono.Mode.values();String[] choices=new String[modes.length];
        for(int i=0;i<modes.length;i++)choices[i]=modes[i].label;
        new AlertDialog.Builder(this).setTitle("Chronomètre · circuit fermé").setItems(choices,(d,index)->armChrono(modes[index])).setNegativeButton("Fermer",null).show();
    }
    private void armChrono(DriveChrono.Mode mode){
        long now=SystemClock.elapsedRealtime();Long stamp=ages.get("speed_kmh");Double speed=SpeedDisplayCalibration.display(values.get("speed_kmh"));
        if(!motorLive()||stamp==null||stamp>now||now-stamp>DriveChrono.MAX_GAP_MS||speed==null||!Double.isFinite(speed)){
            new AlertDialog.Builder(this).setTitle("Chrono indisponible").setMessage("Lance la lecture moteur et attends une vitesse récente avant de préparer le chrono.").setPositiveButton("Fermer",null).show();return;
        }
        chrono.arm(mode);chrono.observe(speed,stamp,now,true);
        String start=mode==DriveChrono.Mode.HUNDRED_200?"Le chrono démarre au passage de 100 km/h, après être redescendu sous 100 si nécessaire.":"Le chrono démarre dès que la vitesse dépasse 1 km/h. Arrête-toi avant le départ.";
        new AlertDialog.Builder(this).setTitle("Chrono prêt · "+mode.label).setMessage(start+"\n\nUtilise un circuit fermé. Vitesse du compteur PP406 : résultat indicatif, dépendant de la cadence des mesures. Au-dessus de 120 km/h, la correction de vitesse est extrapolée.").setPositiveButton("Fermer",null).show();
    }
    private void finishChronoLap(){
        long result=chrono.finishLap(SystemClock.elapsedRealtime());if(result<0)return;
        showChronoResult("Tour",result,"Le tour suivant continue automatiquement.");
    }
    private void showChronoResult(String title,long time,String details){
        if(!resumed||destroyed)return;
        new AlertDialog.Builder(this).setTitle(title+" · terminé").setMessage(chronoTime(time)+"\n\n"+details).setPositiveButton("Fermer",null).show();
    }
    private void refreshChrono(){
        DriveChrono.State before=chrono.state();
        if(before==DriveChrono.State.OFF||before==DriveChrono.State.COMPLETE||before==DriveChrono.State.LOST)return;
        long now=SystemClock.elapsedRealtime();Long stamp=ages.get("speed_kmh");boolean live=resumed&&motorLive();
        chrono.observe(SpeedDisplayCalibration.display(values.get("speed_kmh")),stamp==null?-1:stamp,now,live);chrono.tick(now,live);
        if(chronoLapButton!=null){
            boolean visible=chrono.state()==DriveChrono.State.RUNNING||chrono.state()==DriveChrono.State.WAITING;
            chronoLapButton.setVisibility(visible?View.VISIBLE:View.GONE);
            if(visible){String label=chrono.state()==DriveChrono.State.WAITING?"Prêt · "+chrono.mode().label:chrono.mode()==DriveChrono.Mode.LAP?"Fin du tour":chrono.mode().label;
                chronoLapButton.setText(label+"\n"+chronoTime(chrono.elapsedMs(now)));}
        }
        if(chrono.state()==DriveChrono.State.COMPLETE){
            String label=chrono.mode().label;long result=chrono.resultMs(),gap=chrono.maxGapMs();
            showChronoResult(label,result,"Temps indicatif, calculé à partir de la vitesse affichée par PP406. Intervalle maximal entre mesures : "+gap+" ms. Correction au-delà de 120 km/h extrapolée.");
        }else if(chrono.state()==DriveChrono.State.LOST&&before!=DriveChrono.State.LOST){
            if(resumed&&!destroyed)new AlertDialog.Builder(this).setTitle("Chrono interrompu").setMessage("Vitesse indisponible ou trop ancienne : aucun résultat calculé.").setPositiveButton("Fermer",null).show();
        }
    }
    private void showMainMenu(){
        new AlertDialog.Builder(this).setTitle("PP406 · diagnostique · "+BuildInfo.VERSION)
            .setItems(new String[]{"‹ Retour","Choisir le modèle à rechercher","Véhicule du garage · VIN / OPR","Documents techniques Club","Voir les derniers défauts","Valeurs · lecture, couleurs et aide","Connexion et journal","Compatibilité","Retour au cockpit","Calculateurs mémorisés pour ce VIN","Surveillance des défauts · Club","Mises à jour · sans USB","Affichage · chiffres ou compteurs à aiguilles","Signaler un problème","Analyse d’une capture VAN · test"},(d,index)->{
                switch(index){case 0:navigateBack();break;case 1:showVehicleFallback();break;case 2:showGarageChooser();break;case 3:showClubDocuments();break;case 4:showFaultResults();break;case 5:showMeasurementMenu();break;case 6:showConnectionDetails();break;case 7:showCompatibility();break;case 8:requestStop();Intent launch=getPackageManager().getLaunchIntentForPackage("com.novalysapps.coupe406launcher");if(launch!=null)startActivity(launch);else finish();break;case 9:new AlertDialog.Builder(this).setTitle("Calculateurs mémorisés par VIN").setMessage(VehicleEcuMemory.validVin(vehicleVin)?VehicleEcuMemory.description(VehicleEcuMemory.load(this,vehicleVin)):"Sélectionne une voiture du garage ou saisis son VIN, puis effectue un scan.").setPositiveButton("Fermer",null).show();break;case 10:showFaultMonitor();break;case 11:AppUpdates.show(this,()->running||globalRunning||operationUi.open||permissionPending||DiagnosticLease.held()||FaultMonitorService.active());break;case 12:showDisplayChoice();break;case 13:showReportProblem();break;case 14:showVanCaptureAnalysis();break;}
            }).show();
    }
    private void showVanCaptureAnalysis(){
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(12));
        body.addView(text("Trame VAN CONF 824 · versions équipées du réseau VAN. Colle uniquement les 7 octets de données d’une capture. Analyse manuelle : aucune connexion VAN ni commande n’est lancée.",15,WHITE));
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(WHITE);input.setHintTextColor(MUTED);
        input.setHint("Exemple de format : 1F 40 13 88 00 00 00");body.addView(input);
        body.addView(text("Sources : PSA-RE / OpenLeo (Apache 2.0) et VanLiveConnect (MIT).",12,MUTED));
        TextView result=text("Résultat en attente d’une capture.",17,MUTED);result.setPadding(0,dp(10),0,0);body.addView(result);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Analyser une trame VAN · essai")
            .setView(body).setNegativeButton("Fermer",null).setPositiveButton("Analyser",null).create();
        dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                VanDashboardFrame decoded=VanDashboardFrame.decode(0x824,VanDashboardFrame.parsePayload(input.getText().toString()));
                String rpm=decoded.rpm==null?"indisponible":String.format(Locale.FRANCE,"%.1f tr/min",decoded.rpm);
                String speed=decoded.speedKmh==null?"indisponible":String.format(Locale.FRANCE,"%.2f km/h",decoded.speedKmh);
                result.setText("Régime : "+rpm+"\nVitesse : "+speed+"\n\nRésultat calculé à partir de la saisie ; présence sur le véhicule et précision à confirmer.");result.setTextColor(WHITE);
            }catch(IllegalArgumentException error){result.setText(error.getMessage());result.setTextColor(AMBER);}
        }));
        dialog.show();
    }
    private void showFaultMonitor(){
        if(FaultMonitorService.active()){
            new AlertDialog.Builder(this).setTitle("Surveillance des défauts · Club").setMessage(FaultMonitorService.status()+"\n\nLa lecture continue quand PP406 est en arrière-plan. Le bouton Arrêter ferme la session avant de libérer le Lexia.").setNegativeButton("Fermer",null).setPositiveButton("Arrêter la surveillance",(d,w)->FaultMonitorService.stop(this)).show();return;
        }
        if(operationPending||operationUi.open||globalRunning||permissionPending||monitorStartQueued){setStatus("Termine l’opération en cours avant la surveillance",AMBER);return;}
        if(linkResetRequired||getSharedPreferences("lexia_link",0).getBoolean("resetRequired",false)||contactCycleRequired){setStatus("Rétablis la liaison et le contact avant la surveillance",AMBER);return;}
        if(selectedCarId.isEmpty()||!VehicleEcuMemory.validVin(vehicleVin)){
            new AlertDialog.Builder(this).setTitle("Choisir la voiture du garage").setMessage("La surveillance associe chaque défaut au carId et au VIN de la voiture choisie dans 406 Coupé Club. Choisis cette voiture dans le garage, puis effectue un premier scan.").setPositiveButton("Garage",(d,w)->showGarageChooser()).setNegativeButton("Fermer",null).show();return;
        }
        JSONObject modules=VehicleEcuMemory.load(this,vehicleVin).optJSONObject("modules");int count=0;
        if(modules!=null){java.util.Iterator<String> keys=modules.keys();while(keys.hasNext()){JSONObject ecu=modules.optJSONObject(keys.next());if(ecu!=null&&ecu.optBoolean("monitorable")&&MonitorReadSession.supported(ecu.optString("profile")))count++;}}
        if(count==0){new AlertDialog.Builder(this).setTitle("Premier scan nécessaire").setMessage("Aucun calculateur avec une lecture complète des défauts intégrée à la surveillance n’est encore mémorisé pour ce VIN. Lance Connecter et lire sur cette voiture. Les autres profils restent accessibles au diagnostic manuel.").setPositiveButton("Fermer",null).show();return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},17406);setStatus("Autorise les notifications, puis rouvre Surveillance des défauts",AMBER);return;}
        final String vin=vehicleVin,carId=selectedCarId;
        new AlertDialog.Builder(this).setTitle("Surveiller les défauts · "+count+" calculateur(s)")
            .setMessage("Lecture seule en arrière-plan, avec notification PP406 de fonctionnement. Un nouveau tour commence 60 secondes après la fin du précédent.\n\nLe premier relevé complet sert de référence. Les défauts déjà présents restent dans l’historique ; seules les nouvelles apparitions sont proposées en alerte. Chaque identité est vérifiée à nouveau.\n\nLe Lexia doit rester alimenté, relié à la voiture et en USB à la tablette. Aucun effacement ni test n’est lancé.\n\nLes alertes sont disponibles pour 406 Coupé Club sans Internet. Leur notification dans le Club nécessite la mise à jour de cette application sur le PC fixe.")
            .setNegativeButton("Annuler",null).setPositiveButton("Démarrer",(d,w)->{monitorStartQueued=true;requestStop();startMonitorWhenReady(vin,carId);}).show();
    }
    private void startMonitorWhenReady(String vin,String carId){
        if(!monitorStartQueued)return;
        if(destroyed||!resumed){monitorStartQueued=false;return;}
        if(running||DiagnosticLease.held()){setStatus("Préparation de la surveillance · fermeture de la lecture",AMBER);handler.postDelayed(()->startMonitorWhenReady(vin,carId),300);return;}
        monitorStartQueued=false;
        try{
            UsbDevice device=findDevice();if(device==null||!manager.hasPermission(device))throw new IOException("Connecte d’abord le Lexia et accorde son accès USB avec Connecter et lire");
            Intent intent=new Intent(this,FaultMonitorService.class).putExtra("vin",vin).putExtra("carId",carId);
            if(Build.VERSION.SDK_INT>=26)startForegroundService(intent);else startService(intent);
            handler.postDelayed(()->{refreshDevice();controls();},300);
        }catch(Exception e){setStatus("Surveillance non démarrée : "+e.getMessage(),AMBER);}
    }
    private void showCompatibility(){
        new AlertDialog.Builder(this).setTitle("Compatibilité de PP406")
            .setMessage("59 branches Peugeot répertoriées. Les profils 406 sont prioritaires en automatique. Les nouveaux lecteurs restent à vérifier sur chaque véhicule. Une absence de réponse ne prouve pas l’absence du calculateur.\n\nLes fonctions avancées restent limitées aux références et aux procédures intégrées. Clés, codages, cartographie, CAN et liaison sans fil restent à intégrer.\n\nLes résultats obtenus restent accessibles après un échec. Après la lecture Kiekert, couper puis remettre le contact avant une nouvelle session.")
            .setNeutralButton("Véhicules Peugeot",(d,w)->VehicleCatalogue.show(this,null)).setPositiveButton("Fermer",null).show();
    }
    private void acceptGarageIntent(Intent intent){
        android.net.Uri uri=intent==null?null:intent.getData();
        if(uri!=null&&"pp406".equals(uri.getScheme())&&"diagnostic".equals(uri.getHost()))requestedClubCarId=uri.getQueryParameter("carId");
    }
    private void refreshClub(Runnable ready){
        if(clubLoading){if(ready!=null)handler.postDelayed(()->refreshClub(ready),200);return;}
        clubLoading=true;
        new Thread(()->{
            ClubBridge.Catalog loaded=null;String issue="";
            try{loaded=ClubBridge.read(this);}catch(Exception e){issue=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}
            final ClubBridge.Catalog result=loaded;final String error=issue;
            ui(()->{
                clubLoading=false;clubCatalog=result;
                clubStatus=result==null?"Club indisponible : "+error:result.vehicles.length()+" voiture(s), "+result.documents.length()+" document(s)";
                if(result==null||result.car(selectedCarId)==null)selectedCarId="";
                if(requestedClubCarId!=null){
                    JSONObject car=result==null?null:result.car(requestedClubCarId);requestedClubCarId=null;
                    if(car==null){garageIntentUnresolved=true;setStatus("Voiture du garage introuvable · ouvre le Club et vérifie le compte",AMBER);}
                    else if(running||DiagnosticLease.held())setStatus("Session en cours · arrête la lecture avant de changer de voiture du garage",AMBER);
                    else {selectGarageCar(car);setStatus("Garage associé · "+garageLabel()+" · prêt à connecter",LIME);}
                }else if(selectedCarId.isEmpty()&&result!=null){JSONObject car=result.uniqueVin(vehicleVin);if(car!=null)selectedCarId=car.optString("carId");}
                if(ready!=null)ready.run();
            });
        },"PP406-Club-catalogue").start();
    }
    private String garageLabel(){
        JSONObject car=clubCatalog==null?null:clubCatalog.car(selectedCarId);
        return car==null?"Garage : sans association":carLabel(car);
    }
    private static String carLabel(JSONObject car){
        String label=car.optString("engineCode",car.optString("motorId","406 Coupé"))+" · "+car.optString("year","")+" · "+car.optString("gearbox","");
        String plate=car.optString("plate","");return label+(plate.isEmpty()?"":" · "+plate);
    }
    private void selectGarageCar(JSONObject car){
        garageIntentUnresolved=false;
        String previousVin=vehicleVin;
        selectedCarId=car.optString("carId","");vehicleVin=ClubBridge.normalizeVin(car.optString("vin",""));vehicleDam=car.optString("dam","");saveVehicleIdentity();
        rememberedEcus=VehicleEcuMemory.load(this,vehicleVin);
        if(!previousVin.equals(vehicleVin))vehicleChanged();
        if(vehicleContextReady())applyArchitecturePlan();
    }
    private void saveVehicleIdentity(){getSharedPreferences("vehicle_identity",MODE_PRIVATE).edit().putString("vin",vehicleVin).putString("dam",vehicleDam).apply();}
    private String vehicleArchitecture(){return VehicleEcuMemory.validVin(vehicleVin)?getSharedPreferences("vehicle_architecture",MODE_PRIVATE).getString(vehicleVin,""):"";}
    private void restoreVehiclePlan(){
        String key=VehicleArchitecture.restoredPlan(rememberedEcus.optString("plan","auto"),vehicleArchitecture());
        vehiclePlan=vehiclePlans.models.containsKey(key)?vehiclePlans.models.get(key):vehiclePlans.automatic;
    }
    private void vehicleChanged(){
        restoreVehiclePlan();scanContinuation=null;selectedModule="motor";selectedFuel="";
        clearScanReadings();restoreRememberedProfiles();restoreScanEquipment();rebuildModuleButtons();showTab(0);
    }
    private boolean vehicleContextReady(){return VehicleArchitecture.ready(vehicleVin,vehicleArchitecture());}
    private void requestVehicleContext(Runnable next){
        if(!VehicleEcuMemory.validVin(vehicleVin)){showVehicleIdentity(()->requestVehicleContext(next));return;}
        if(!VehicleArchitecture.ready(vehicleVin,vehicleArchitecture())){showVehicleArchitecture(next);return;}
        applyArchitecturePlan();if(next!=null)next.run();
    }
    private void showVehicleArchitecture(Runnable next){
        if(running||DiagnosticLease.held()||permissionPending){setStatus("Arrête la lecture avant de changer le type du véhicule",AMBER);return;}
        if(!VehicleEcuMemory.validVin(vehicleVin)){showVehicleIdentity(()->showVehicleArchitecture(next));return;}
        String[] labels={"Non multiplexée","Semi-multiplexée","Full multiplexée","Je ne sais pas"};
        String[] keys={"nonmux","semi","full","unknown"};
        new AlertDialog.Builder(this).setTitle("Type de la 406 Coupé · à confirmer")
            .setItems(labels,(d,index)->{getSharedPreferences("vehicle_architecture",MODE_PRIVATE).edit().putString(vehicleVin,keys[index]).apply();applyArchitecturePlan();if(next!=null)next.run();})
            .setNegativeButton("Annuler",null).show();
    }
    /** Returns true when the visible and scanned vehicle plan changed. */
    private boolean applyArchitecturePlan(){
        String kind=vehicleArchitecture(),key=VehicleArchitecture.plan(vehiclePlan.key,kind);
        if(key.equals(vehiclePlan.key)||!vehiclePlans.models.containsKey(key))return false;
        vehiclePlan=vehiclePlans.models.get(key);scanContinuation=null;selectedModule="motor";clearScanReadings();restoreScanEquipment();restoreRememberedProfiles();rebuildModuleButtons();showTab(0);return true;
    }
    private boolean scanIdentityReady(String vin,String dam){
        if(!VehicleEcuMemory.validVin(vin)){handler.post(()->showVehicleIdentity(this::connectGlobal));return false;}
        if(!ClubBridge.normalizeVin(vin).equals(vehicleVin)){
            applyVehicleIdentity(vin,dam);
            // The equipment checkboxes belonged to the preceding VIN and plan.
            handler.post(this::connectGlobal);return false;
        }
        if(!vehicleContextReady()){handler.post(()->requestVehicleContext(this::connectGlobal));return false;}
        if(applyArchitecturePlan()){handler.post(this::connectGlobal);return false;}
        return true;
    }
    private void applyVehicleIdentity(String vin,String dam){
        String previousVin=vehicleVin;
        vehicleVin=ClubBridge.normalizeVin(vin);vehicleDam=dam.trim().toUpperCase(Locale.ROOT);saveVehicleIdentity();
        rememberedEcus=VehicleEcuMemory.load(this,vehicleVin);
        if(!vehicleVin.equals(previousVin))vehicleChanged();
        if(!vehicleVin.isEmpty()){
            JSONObject matched=clubCatalog==null?null:clubCatalog.uniqueVin(vehicleVin);
            // An unmatched or ambiguous VIN never inherits a previous vehicle's association.
            selectedCarId=matched==null?"":matched.optString("carId","");
        }else if(clubCatalog==null||clubCatalog.car(selectedCarId)==null)selectedCarId="";
    }
    private void showGarageChooser(){showGarageChooser(null);}
    private void showGarageChooser(Runnable selected){
        if(running||DiagnosticLease.held()||permissionPending){new AlertDialog.Builder(this).setTitle("Voiture du garage").setMessage(garageLabel()+"\nArrête la lecture avant de changer de voiture.").setPositiveButton("Fermer",null).show();return;}
        refreshClub(()->{
            int count=clubCatalog==null?0:clubCatalog.vehicles.length();String[] labels=new String[count+2];
            labels[0]="Saisir VIN / OPR";labels[1]="Continuer sans association au Club";
            for(int i=0;i<count;i++)labels[i+2]=carLabel(clubCatalog.vehicles.optJSONObject(i));
            new AlertDialog.Builder(this).setTitle("Voiture du garage · "+clubStatus).setItems(labels,(d,index)->{
                if(index==0)showVehicleIdentity(selected);else {
                    if(index==1){applyVehicleIdentity("","");selectedCarId="";}else selectGarageCar(clubCatalog.vehicles.optJSONObject(index-2));
                    if(selected!=null)selected.run();
                }
            }).setNegativeButton("Fermer",null).show();
        });
    }
    private void showVehicleIdentity(Runnable selected){
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText vin=new EditText(this);vin.setSingleLine();vin.setHint("VIN obligatoire · 17 caractères");vin.setText(vehicleVin);body.addView(vin);
        EditText dam=new EditText(this);dam.setSingleLine();dam.setHint("OPR / DAM · conseillé, facultatif");dam.setText(vehicleDam);body.addView(dam);
        body.addView(text("Le VIN permet d’associer le diagnostic à la bonne voiture. L’OPR est recommandé ; il ne détermine pas seul les calculateurs.",14,MUTED));
        AlertDialog identityDialog=new AlertDialog.Builder(this).setTitle("Identifier la voiture").setView(body).setNegativeButton("Annuler",null).setNeutralButton("Type de multiplexage",(d,w)->showVehicleArchitecture(null)).setPositiveButton("Enregistrer",null).create();
        deferKeyboard(identityDialog,body);identityDialog.show();identityDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            if(!VehicleEcuMemory.validVin(vin.getText().toString())){vin.setError("Saisis un VIN valide de 17 caractères");return;}
            applyVehicleIdentity(vin.getText().toString(),dam.getText().toString());identityDialog.dismiss();setStatus(garageLabel(),selectedCarId.isEmpty()?AMBER:LIME);if(selected!=null)selected.run();
        });
    }
    private void showClubDocuments(){
        refreshClub(()->{
            if(clubCatalog==null||clubCatalog.documents.length()==0){new AlertDialog.Builder(this).setTitle("Documents Club").setMessage(clubStatus+"\nOuvre Coupé 406 Club et connecte-toi pour charger le catalogue.").setPositiveButton("Fermer",null).show();return;}
            List<JSONObject> documents=new ArrayList<>();JSONObject car=clubCatalog.car(selectedCarId);String motor=car==null?"":car.optString("motorId","");
            for(int i=0;i<clubCatalog.documents.length();i++){
                JSONObject doc=clubCatalog.documents.optJSONObject(i);if(doc==null)continue;
                android.net.Uri link=android.net.Uri.parse(doc.optString("url",""));if(!"https".equals(link.getScheme())||link.getHost()==null)continue;
                org.json.JSONArray motors=doc.optJSONArray("motorIds");boolean relevant=motor.isEmpty()||motors==null||motors.length()==0;
                if(motors!=null)for(int j=0;j<motors.length();j++)relevant|=motor.equals(motors.optString(j));
                if(relevant)documents.add(doc);
            }
            String[] labels=new String[documents.size()];for(int i=0;i<labels.length;i++){JSONObject doc=documents.get(i);labels[i]=doc.optString("category","Document")+" · "+doc.optString("title","Sans titre");}
            new AlertDialog.Builder(this).setTitle("Documents techniques · "+labels.length).setItems(labels,(d,index)->{
                JSONObject doc=documents.get(index);String url=doc.optString("url");
                new AlertDialog.Builder(this).setTitle(doc.optString("title")).setMessage(doc.optString("category")+"\n"+url)
                    .setNegativeButton("Retour",null).setPositiveButton(running?"Arrêter la lecture et ouvrir":"Ouvrir le document",(dialog,w)->openClubDocument(url)).show();
            }).setNegativeButton("Fermer",null).show();
        });
    }
    private void openClubDocument(String url){
        if(!FaultMonitorService.active()&&(running||DiagnosticLease.held())){requestStop();handler.postDelayed(()->{if(!destroyed&&resumed)openClubDocument(url);},200);return;}
        try{startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(url)));}catch(Exception e){setStatus("Aucune application pour ouvrir ce lien HTTPS",AMBER);}
    }
    private void showMeasurementMenu(){
        new AlertDialog.Builder(this).setTitle("Valeurs · "+selectedModule).setItems(new String[]{"Lire / reprendre les valeurs","Régler les alertes de couleur","Références et conditions","Détails de la connexion","Affichage · chiffres ou compteurs à aiguilles","Comparer la vitesse au GPS"},(d,index)->{
            if(index==0){readSelectedValues();}
            else if(index==1)showAlertSettings();else if(index==2)showGuideConditions();else if(index==3)showConnectionDetails();else if(index==4)showDisplayChoice();else showSpeedReference();
        }).show();
    }
    private void showDisplayChoice(){
        new AlertDialog.Builder(this).setTitle("Affichage des valeurs moteur")
            .setSingleChoiceItems(new String[]{"Chiffres · toutes les valeurs","Grands compteurs · températures et lambda"},needleMode?1:0,(dialog,index)->{
                needleMode=index==1;getSharedPreferences("measurement_display",MODE_PRIVATE).edit().putBoolean("needles",needleMode).apply();
                showMeasurements();renderValues();dialog.dismiss();
            }).setNegativeButton("Retour",null).show();
    }
    private void showSpeedReference(){
        new AlertDialog.Builder(this).setTitle("Comparaison vitesse calculateur / GPS")
            .setMessage(gpsSpeed.status()+"\n\nEnregistre côte à côte l’octet brut du moteur, sa vitesse décodée et la vitesse GPS horodatée de la tablette dans le trajet sauvegardé. Aucune coordonnée géographique n’est conservée. Le compteur PP406 applique uniquement à l’affichage la correspondance mesurée avec le compteur de la voiture ; les relevés GPS et moteur restent bruts.\n\nActiver à l’arrêt avant de lancer les valeurs. La localisation Android doit être autorisée et le GPS disponible. Une vitesse GPS manquante ou trop ancienne n’est jamais remplacée par zéro. La précision de vitesse n’est pas fournie par Android 7 : cette comparaison reste à vérifier sur plusieurs vitesses stabilisées.")
            .setNegativeButton("Retour",null).setNeutralButton("Désactiver",(d,w)->{getSharedPreferences("measurement_display",MODE_PRIVATE).edit().putBoolean("gps_comparison",false).apply();gpsSpeed.stop();})
            .setPositiveButton("Activer",(d,w)->{
                if(!gpsSpeed.permitted()){
                    if(running||DiagnosticLease.held()){setStatus("Autorise le GPS avant la prochaine lecture · la session en cours continue",AMBER);return;}
                    getSharedPreferences("measurement_display",MODE_PRIVATE).edit().putBoolean("gps_comparison",true).apply();
                    requestPermissions(new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION,android.Manifest.permission.ACCESS_COARSE_LOCATION},18406);
                }else{getSharedPreferences("measurement_display",MODE_PRIVATE).edit().putBoolean("gps_comparison",true).apply();gpsSpeed.start();setStatus(gpsSpeed.status(),AMBER);}
            }).show();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==18406&&gpsSpeed!=null){if(resumed)gpsSpeed.start();setStatus(gpsSpeed.status(),AMBER);}
    }
    private void showConnectionDetails(){
        String detail=deviceInfo.getText()+"\n\n"+globalSummary.getText()+"\n\n"+logInfo.getText()+"\n\n"+raw.getText()+"\n\n"+TripBackups.status+"\nEnvoi privé des trajets par HTTPS, avec reprise automatique. Sans réseau, les fichiers restent sur la tablette.\n\n"+DiagnosticBackups.status;
        TextView info=text(detail,15,WHITE);info.setPadding(dp(20),dp(12),dp(20),dp(12));info.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this);scroll.addView(info);
        new AlertDialog.Builder(this).setTitle("Connexion et journal").setView(scroll).setPositiveButton("Fermer",null)
            .setNeutralButton("Envoi technique",(d,w)->showDiagnosticPrivacy())
            .setNegativeButton("Signaler un problème",(d,w)->showReportProblem()).show();
    }
    private void promptDiagnosticConsent(){
        new AlertDialog.Builder(this).setTitle("Aider à tester PP406")
            .setMessage("Autorises-tu l’envoi automatique au serveur privé du Club des journaux techniques, échanges Lexia, erreurs et plantages ? Ils peuvent contenir le VIN et des réponses de calculateurs. Les anciens journaux ne sont pas envoyés. Ton choix est mémorisé ; tu peux le changer dans « Connexion et journal ». Aucun effacement ni commande sur la voiture n’est lancé par cet envoi.")
            .setCancelable(false)
            .setNegativeButton("Non merci",(d,w)->DiagnosticBackups.decide(this,false))
            .setPositiveButton("J’autorise",(d,w)->DiagnosticBackups.decide(this,true)).show();
    }
    private void showDiagnosticPrivacy(){
        boolean active=DiagnosticBackups.enabled(this);
        new AlertDialog.Builder(this).setTitle("Envoi des journaux techniques")
            .setMessage((active?"Activé · ":"Désactivé · ")+DiagnosticBackups.status+"\n\nLes journaux futurs peuvent contenir le VIN, les trames Lexia et des informations sur la tablette. Tu peux arrêter l’envoi à tout moment. Les rapports déjà reçus restent sur le serveur privé.")
            .setNegativeButton("Fermer",null)
            .setNeutralButton("Assistant IA",(d,w)->showAiPrivacy())
            .setPositiveButton(active?"Désactiver":"Activer",(d,w)->DiagnosticBackups.decide(this,!active)).show();
    }
    private void showAiPrivacy(){
        android.content.SharedPreferences choices=getSharedPreferences("bug_assistant",MODE_PRIVATE);
        boolean active=choices.getBoolean("ai",false);
        new AlertDialog.Builder(this).setTitle("Analyse IA des signalements")
            .setMessage((active?"Autorisée":"Désactivée")+". Si une clé OpenAI est configurée sur le serveur, tes réponses au formulaire de problème lui seront transmises pour préparer la fiche. Les journaux Lexia suivent leur réglage séparé.")
            .setNegativeButton("Fermer",null)
            .setPositiveButton(active?"Désactiver":"Autoriser",(d,w)->choices.edit().putBoolean("asked",true).putBoolean("ai",!active).apply()).show();
    }
    private String reportPageContext(){
        String page=selectedTab>=0&&selectedTab<TAB_NAMES.length?TAB_NAMES[selectedTab]:"Diagnostic";
        VehicleReadPlans.Module module=vehiclePlan.module(selectedModule);
        StringBuilder context=new StringBuilder("Page : ").append(page).append("\nCalculateur : ")
            .append(module==null?selectedModule:module.title).append(" (").append(selectedModule).append(")");
        if(selectedTab==2&&selectedModule.equals("motor"))context.append("\nSection : ").append(MOTOR_TEST_SECTIONS[selectedMotorTestSection]);
        if(selectedTab>=2&&primaryFunction!=null)context.append("\nFonction affichée : ").append(primaryFunction.title).append(" (").append(primaryFunction.id).append(")");
        if(VehicleEcuMemory.validVin(vehicleVin)){
            JSONObject car=clubCatalog==null?null:clubCatalog.car(selectedCarId);
            context.append("\nVéhicule identifié par VIN : ")
                .append(car==null?"406 Coupé":car.optString("engineCode",car.optString("motorId","406 Coupé")))
                .append(" (VIN enregistré dans PP406, Lexia actuellement connecté ou non)");
        }
        return context.toString();
    }
    private void showReportProblem(){
        String pageContext=reportPageContext();
        android.content.SharedPreferences choices=getSharedPreferences("bug_assistant",MODE_PRIVATE);
        if(!choices.getBoolean("asked",false)){
            new AlertDialog.Builder(this).setTitle("Assistant de signalement")
                .setMessage("Jusqu’à quatre questions complémentaires si nécessaire, puis une fiche de bug à vérifier avant l’envoi. Si l’API OpenAI est configurée, acceptes-tu que tes réponses lui soient transmises pour formuler les questions ? Les journaux Lexia ne sont envoyés que selon ton réglage séparé.")
                .setNegativeButton("Sans analyse OpenAI",(d,w)->{choices.edit().putBoolean("asked",true).putBoolean("ai",false).apply();showReportStep(new ArrayList<>(),"Décris le problème ou la fonction inachevée.",pageContext);})
                .setPositiveButton("J’accepte",(d,w)->{choices.edit().putBoolean("asked",true).putBoolean("ai",true).apply();showReportStep(new ArrayList<>(),"Décris le problème ou la fonction inachevée.",pageContext);}).show();
        }else showReportStep(new ArrayList<>(),"Décris le problème ou la fonction inachevée.",pageContext);
    }
    private void showReportStep(ArrayList<String> answers,String question,String pageContext){
        EditText entry=new EditText(this);entry.setTextColor(WHITE);entry.setHintTextColor(MUTED);
        entry.setHint("Écris ici…");entry.setMinLines(3);entry.setMaxLines(5);
        entry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1500)});
        entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        new AlertDialog.Builder(this).setTitle("Signaler un problème · question "+(answers.size()+1)+"/5 max")
            .setMessage(pageContext.replace("\n"," · ")+"\n\n"+question).setView(entry).setNegativeButton("Annuler",null)
            .setPositiveButton("Continuer",(d,w)->{
                String answer=entry.getText().toString().trim();
                if(answer.length()<10){new AlertDialog.Builder(this).setMessage("Ajoute au moins quelques mots pour décrire le problème.").setPositiveButton("Réessayer",(x,y)->showReportStep(answers,question,pageContext)).show();return;}
                answers.add(answer);requestReportTriage(answers,pageContext);
            }).show();
    }
    private void requestReportTriage(ArrayList<String> answers,String pageContext){
        AlertDialog wait=new AlertDialog.Builder(this).setTitle("Préparation du signalement").setMessage("Analyse en cours…").setCancelable(false).create();wait.show();
        boolean ai=getSharedPreferences("bug_assistant",MODE_PRIVATE).getBoolean("ai",false);
        DiagnosticBackups.triage(this,new ArrayList<>(answers),pageContext,ai,(kind,message,usedAi)->ui(()->{
            wait.dismiss();
            if("question".equals(kind))showReportStep(answers,message,pageContext);
            else if("report".equals(kind)){
                String context="\n\nContexte PP406 :\n"+pageContext+
                    (selectedCarId.isEmpty()?"":" · voiture du garage "+selectedCarId)+
                    (vehicleVin.isEmpty()?"":" · VIN "+vehicleVin);
                confirmReport(message+context);
            }
            else new AlertDialog.Builder(this).setTitle("Connexion indisponible")
                .setMessage("L’assistant en ligne est inaccessible. Tu peux répondre à deux questions sur la tablette ; ton signalement sera enregistré puis envoyé automatiquement au retour d’Internet.")
                .setNegativeButton("Réessayer",(d,w)->requestReportTriage(answers,pageContext))
                .setPositiveButton("Continuer hors ligne",(d,w)->showOfflineReportStep(answers,pageContext)).show();
        }));
    }
    private void showOfflineReportStep(ArrayList<String> answers,String pageContext){
        if(answers.size()>=3){
            StringBuilder draft=new StringBuilder("Signalement recueilli hors connexion\n\n");
            for(int i=0;i<answers.size();i++)draft.append("Réponse ").append(i+1).append(" : ").append(answers.get(i)).append("\n\n");
            draft.append("Contexte PP406 :\n").append(pageContext);
            confirmReport(draft.toString(),new ArrayList<>(answers),pageContext);return;
        }
        EditText entry=new EditText(this);entry.setTextColor(WHITE);entry.setHintTextColor(MUTED);
        entry.setHint("Écris ici…");entry.setMinLines(3);entry.setMaxLines(5);
        entry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(1500)});
        entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        String question=answers.size()==1?"Quelles actions as-tu faites juste avant le problème ?":"Que devais-tu obtenir, que s’est-il passé et est-ce que cela se reproduit ?";
        new AlertDialog.Builder(this).setTitle("Signalement hors ligne · question "+(answers.size()+1)+"/3")
            .setMessage(question).setView(entry).setNegativeButton("Annuler",null).setPositiveButton("Continuer",(d,w)->{
                String answer=entry.getText().toString().trim();
                if(answer.length()<10){new AlertDialog.Builder(this).setMessage("Ajoute quelques mots pour préciser ta réponse.").setPositiveButton("Réessayer",(x,y)->showOfflineReportStep(answers,pageContext)).show();return;}
                answers.add(answer);showOfflineReportStep(answers,pageContext);
            }).show();
    }
    private void confirmReport(String draft){confirmReport(draft,null,"");}
    private void confirmReport(String draft,ArrayList<String> offlineAnswers,String offlineContext){

        EditText entry=new EditText(this);entry.setTextColor(WHITE);entry.setText(draft);entry.setMinLines(6);
        entry.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(14000)});
        entry.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        ScrollView scroll=new ScrollView(this);scroll.addView(entry);
        new AlertDialog.Builder(this).setTitle("Vérifie la fiche avant l’envoi")
            .setMessage("Tu peux corriger le texte. Le serveur ajoute automatiquement la version de PP406 et le modèle de tablette.")
            .setView(scroll).setNegativeButton("Annuler",null).setPositiveButton("Envoyer",(d,w)->{
                String report=entry.getText().toString().trim();if(report.length()<10||report.length()>14000){new AlertDialog.Builder(this).setMessage("La fiche doit contenir entre 10 et 14 000 caractères.").setPositiveButton("Modifier",(x,y)->confirmReport(report,offlineAnswers,offlineContext)).show();return;}
                DiagnosticBackups.queueFeedback(this,report,offlineAnswers,offlineContext,offlineAnswers!=null&&getSharedPreferences("bug_assistant",MODE_PRIVATE).getBoolean("ai",false),(success,message)->ui(()->{
                    AlertDialog.Builder result=new AlertDialog.Builder(this).setTitle(success?(message.startsWith("Signalement reçu")?"Signalement envoyé":"Signalement en attente"):"Enregistrement impossible").setMessage(message).setPositiveButton("Fermer",null);
                    if(!success)result.setNegativeButton("Réessayer",(x,y)->confirmReport(report,offlineAnswers,offlineContext));
                    result.show();
                    if(success)DiagnosticBackups.kick(this,null);
                }));
            }).show();
    }
    private void smoothAppearance(TextView field,int color,float alpha,boolean animate){
        Integer target=colorTargets.get(field);
        if(!animate||target==null||target!=color){
            android.animation.ValueAnimator previous=colorAnimations.remove(field);if(previous!=null)previous.cancel();
            colorTargets.put(field,color);
            if(animate&&target!=null&&field.getCurrentTextColor()!=color){
                android.animation.ValueAnimator animation=android.animation.ValueAnimator.ofArgb(field.getCurrentTextColor(),color);animation.setDuration(ColorMotion.DURATION);
                animation.addUpdateListener(a->field.setTextColor((Integer)a.getAnimatedValue()));colorAnimations.put(field,animation);animation.start();
            }else field.setTextColor(color);
        }
        if(field.getAlpha()!=alpha)field.setAlpha(alpha);
    }
    private double[] tierLimits(String key){
        double[] defaults=key.equals("rpm")?new double[]{4000,5000,ValueAlerts.DEFAULT_RPM_RED}:new double[]{130,140,160};
        for(int i=0;i<3;i++)try{defaults[i]=Double.parseDouble(getSharedPreferences("display_alerts",MODE_PRIVATE).getString(key+".tier"+i,Double.toString(defaults[i])));}catch(NumberFormatException ignored){}
        return defaults;
    }
    private void showTierSettings(String key,String label,String unit){
        double[] limits=tierLimits(key);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(12),dp(20),dp(12));
        body.addView(text("Seuils personnels d’affichage en "+unit+". Vert sous le jaune ; rouge fixe au seuil rouge. "+(key.equals("rpm")?"Aiguille rouge dès 6000 tr/min ; zone rouge du cadran de 6400 à 8000 tr/min ; clignotement rapide dès 6400 tr/min. ":"")+"Aucune modification du véhicule. Les valeurs constructeur restent dans l’aide.",15,MUTED));
        EditText[] inputs=new EditText[3];String[] colors={"Jaune dès","Orange dès","Rouge dès"};
        for(int i=0;i<3;i++){body.addView(text(colors[i],14,WHITE));inputs[i]=new EditText(this);inputs[i].setSingleLine();inputs[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);inputs[i].setText(String.format(Locale.ROOT,"%.0f",limits[i]));body.addView(inputs[i]);}
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(label).setView(body).setNegativeButton("Retour",null).setNeutralButton("Désactiver",(d,w)->getSharedPreferences("display_alerts",MODE_PRIVATE).edit().putBoolean(key+".tiers",false).apply()).setPositiveButton("Enregistrer",null).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{double[] chosen=new double[3];for(int i=0;i<3;i++){chosen[i]=Double.parseDouble(inputs[i].getText().toString().replace(',','.'));if(!ValueAlerts.validLimit(chosen[i])||(i>0&&chosen[i]<=chosen[i-1]))throw new NumberFormatException();}
            android.content.SharedPreferences.Editor prefs=getSharedPreferences("display_alerts",MODE_PRIVATE).edit().putBoolean(key+".tiers",true);for(int i=0;i<3;i++)prefs.putString(key+".tier"+i,Double.toString(chosen[i]));prefs.apply();dialog.dismiss();
        }catch(NumberFormatException e){inputs[2].setError("Trois seuils positifs et croissants");}});
    }
    private void showAlertSettings(){
        String[][] signals=selectedModule.equals("climate")?ClimateDecoder.SIGNALS:java.util.Arrays.copyOf(DISPLAY_SIGNALS,DISPLAY_SIGNALS.length+1);
        if(!selectedModule.equals("climate"))signals[DISPLAY_SIGNALS.length]=ClimateDecoder.SIGNALS[0];
        String[] labels=new String[signals.length];
        for(int i=0;i<signals.length;i++){
            String key=signals[i][0];labels[i]=signals[i][1]+" · "+(lowAlerts.containsKey(key)?"min "+lowAlerts.get(key):"min non renseigné")+" / "+(highAlerts.containsKey(key)?"max "+highAlerts.get(key):"max non renseigné");
            if(key.equals("oxygen_mv")&&getSharedPreferences("display_alerts",MODE_PRIVATE).getBoolean("lambda_context",true))labels[i]=signals[i][1]+" · seuil bas en charge / plage électrique";
            if(key.equals("rpm")||key.equals("speed_kmh")){double[] t=tierLimits(key);labels[i]=signals[i][1]+" · jaune "+(int)t[0]+" / orange "+(int)t[1]+" / rouge "+(int)t[2];}
        }
        new AlertDialog.Builder(this).setTitle("Alertes · minimum et maximum").setItems(labels,(d,index)->{
            String[] signal=signals[index];String key=signal[0];
            if(key.equals("rpm")||key.equals("speed_kmh")){showTierSettings(key,signal[1],signal[2]);return;}
            boolean oxygen=key.equals("oxygen_mv"),context=oxygen&&getSharedPreferences("display_alerts",MODE_PRIVATE).getBoolean("lambda_context",true);
            LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(20),dp(10),dp(20),dp(10));
            body.addView(text("Rouge sous le minimum ou au-dessus du maximum ; vert dans les limites renseignées. Une case vide laisse la limite correspondante non évaluée. Les températures peuvent être négatives. Ce sont des réglages personnels, pas des tolérances constructeur.",15,MUTED));
            EditText[] inputs=new EditText[2];String[] headings={"Minimum ("+signal[2]+")","Maximum ("+signal[2]+")"};Double[] bounds={lowAlerts.get(key),highAlerts.get(key)};
            if(context){if(bounds[0]==null)bounds[0]=LambdaSignalGuide.DEFAULT_LOW_MV;if(bounds[1]==null)bounds[1]=LambdaSignalGuide.DEFAULT_HIGH_MV;}
            for(int i=0;i<2;i++){body.addView(text(headings[i],14,WHITE));inputs[i]=new EditText(this);inputs[i].setSingleLine();inputs[i].setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL|android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);inputs[i].setText(bounds[i]==null?"":Double.toString(bounds[i]));body.addView(inputs[i]);}
            CheckBox contextual=new CheckBox(this);contextual.setText("Lambda : appliquer le seuil bas seulement en charge (MP7.0 / V6 XFZ identifié)");contextual.setTextColor(WHITE);contextual.setChecked(context);
            if(oxygen){body.addView(contextual);body.addView(text(LambdaSignalGuide.POLICY+"\nDécoché : les limites personnelles sont appliquées à toute mesure récente, quel que soit le contexte.",14,MUTED));}
            ScrollView scroll=new ScrollView(this);scroll.addView(body);
            AlertDialog dialog=new AlertDialog.Builder(this).setTitle(signal[1]).setView(scroll).setNegativeButton("Retour",null).setNeutralButton("Désactiver",(v,w)->{
                highAlerts.remove(key);lowAlerts.remove(key);android.content.SharedPreferences.Editor prefs=getSharedPreferences("display_alerts",MODE_PRIVATE).edit().putString(key,"").putString(key+".low","");if(oxygen)prefs.putBoolean("lambda_context",false);prefs.apply();renderValues();
            }).setPositiveButton("Enregistrer",null).create();
            dialog.show();dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{try{
                Double low=inputs[0].getText().toString().trim().isEmpty()?null:Double.valueOf(inputs[0].getText().toString().replace(',','.'));
                Double high=inputs[1].getText().toString().trim().isEmpty()?null:Double.valueOf(inputs[1].getText().toString().replace(',','.'));
                if(!ValueAlerts.validRange(low,high)||(oxygen&&contextual.isChecked()&&!LambdaSignalGuide.validLimits(low,high)))throw new NumberFormatException();
                if(low==null)lowAlerts.remove(key);else lowAlerts.put(key,low);if(high==null)highAlerts.remove(key);else highAlerts.put(key,high);
                android.content.SharedPreferences.Editor prefs=getSharedPreferences("display_alerts",MODE_PRIVATE).edit().putString(key,high==null?"":high.toString()).putString(key+".low",low==null?"":low.toString());if(oxygen)prefs.putBoolean("lambda_context",contextual.isChecked());prefs.apply();dialog.dismiss();renderValues();
            }catch(NumberFormatException e){inputs[1].setError("Bornes numériques finies et minimum ≤ maximum requis");}});
        }).setNegativeButton("Fermer",null).show();
    }
    private boolean canPrepareTest(FunctionCatalog.Action action){
        boolean test=MotorOperations.plan(action.id)!=null||ClimateOperations.plan(action.id)!=null;
        if(!test||!action.implemented||operationUi.open||permissionPending||switchProfile!=null||globalRunning)return false;
        if(running&&FunctionCatalog.module(action).equals(activeModule)&&(stopRequested||
                (activeModule.equals("motor")?session==null||!session.isReady():otherSession==null||!otherSession.isReady())))return false;
        FunctionCatalog.Availability state=functionAvailability(action);
        return state==FunctionCatalog.Availability.IDENTIFICATION_REQUIRED||state==FunctionCatalog.Availability.FAULT_READ_REQUIRED;
    }
    private void prepareTest(FunctionCatalog.Action action){
        if(!canPrepareTest(action))return;
        if(functionAvailability(action)==FunctionCatalog.Availability.FAULT_READ_REQUIRED){
            boolean queued=selectedModule.equals("motor")?session!=null&&session.queueFaultRead():otherSession!=null&&otherSession.queueFaultRead();
            if(queued){beginOperation(selectedModule,"Préparation du test","Lecture des défauts avant activation · aucun test envoyé");}
        }else {inlineRequested=true;inlineStart(selectedModule,"Préparation du test · identification et lecture seules");connectSelected();}
    }
    private void renderValues() {
        long now=SystemClock.elapsedRealtime(),latest=0;
        boolean motorLive=motorLive();
        boolean sharingLive=motorLive&&resumed;
        if(lastSharedRevision!=measurementRevision||lastSharedLive!=sharingLive||!java.util.Objects.equals(lastSharedReference,recognizedReference)){
            DiagnosticDataHub.publish(sharingOwner,EngineValueGuide.REFERENCE.equals(recognizedReference)?EngineValueGuide.VEHICLE:"Véhicule à confirmer","MP7.0",recognizedReference,SIGNALS,values,ages,sharingLive,now,"Repères Peugeot de ralenti et de régime haut pour ES9J4 ; autres plages à vérifier");
            lastSharedRevision=measurementRevision;lastSharedLive=sharingLive;lastSharedReference=recognizedReference;
        }
        engineAssessment=engineGuide.evaluate(EngineValueGuide.v6Profile(recognizedReference,vehicleVin)?recognizedReference:null,values,ages,now,motorLive);
        lambdaAssessment=lambdaGuide.evaluate(recognizedReference,vehicleVin,values,ages,now,motorLive,getSharedPreferences("display_alerts",MODE_PRIVATE).getBoolean("lambda_context",true),lowAlerts.containsKey("oxygen_mv")?lowAlerts.get("oxygen_mv"):LambdaSignalGuide.DEFAULT_LOW_MV,highAlerts.containsKey("oxygen_mv")?highAlerts.get("oxygen_mv"):LambdaSignalGuide.DEFAULT_HIGH_MV);
        updateText(guideSummary,"Vert : dans la plage appliquée · rouge : sous le minimum ou au-dessus du maximum · neutre : plage non évaluée. Régime et vitesse conservent les paliers demandés. Touchez une valeur pour le contexte.");
        for(Long measured:ages.values())latest=Math.max(latest,measured);
        boolean motorPage=selectedModule.equals("motor")&&!genericPage();
        if(motorPage&&selectedTab==0&&needleMode){
            renderDial(rpmDial,"rpm",motorLive,now);renderDial(speedDial,"speed_kmh",motorLive,now);
        }
        if(motorPage&&selectedTab==0)for(String[] signal:DISPLAY_SIGNALS) {
            if(valueCards.get(signal[0]).getParent()==null)continue;
            TextView field=fields.get(signal[0]); Double value=values.get(signal[0]); Long measured=ages.get(signal[0]);
            TextView guide=valueGuides.get(signal[0]);
            int fieldColor=WHITE,guideColor=MUTED;
            if(value==null||measured==null) { updateNumber(signal,null,field);smoothAppearance(field,WHITE,.4f,false);updateAppearance(guide,MUTED,1f);updateText(guide,"Aucune mesure · toucher pour l’aide");continue; }
            boolean fresh=motorLive&&measured>=motorSessionStart&&measured<=now&&now-measured<3000;
            Double shown=signal[0].equals("speed_kmh")?speedForDisplay(value):value;
            updateNumber(signal,shown,field);
            ValueAssessment assessment=assessValue(signal[0],shown,fresh);
            fieldColor=assessment.color(WHITE);guideColor=assessment.color(MUTED);
            updateText(guide,fresh?assessment.label+" · toucher":"Ancienne mesure · non évaluée");
            smoothAppearance(field,fieldColor,fresh?assessment.alpha(now):1f,fresh);
            smoothAppearance(guide,guideColor,1f,fresh);
        }
        if(motorPage&&latest==0) updateText(age,"Aucun échantillon reçu · les valeurs apparaîtront après une vraie réponse moteur");
        else if(motorPage) {
            long elapsed=now-latest;
            boolean motorReading=motorLive;
            updateText(age,ReadingTime.label(motorSampleWallTime,latest,now,motorReading)+" · "+received+" réponses");
            if(motorReading && elapsed>=3000 && !operationPending) setStatus("Valeurs anciennes · attente de réponse moteur",AMBER);
        }
        if(motorPage&&selectedTab==0)renderOutsideValue();
        if(selectedModule.equals("climate")&&selectedTab==0)renderClimateValues(now);
        if(now-lastControls>=250){lastControls=now;controls();}
    }
    private boolean motorLive(){Long speed=ages.get("speed_kmh"),rpm=ages.get("rpm");return running&&!stopRequested&&activeModule.equals("motor")&&session!=null&&session.isReady()&&!operationPending&&!coreOperationBusy()&&speed!=null&&rpm!=null&&speed>=motorSessionStart&&rpm>=motorSessionStart;}
    private Double speedForDisplay(Double raw){
        Double shown=SpeedDisplayCalibration.display(raw);
        return "P028".equalsIgnoreCase(android.os.Build.MODEL)&&EngineValueGuide.v6Profile(recognizedReference,vehicleVin)
            ?SpeedDisplayCalibration.ownerFineCorrection(shown):shown;
    }
    private void renderDial(NeedleDialView dial,String key,boolean motorLive,long now){
        Long measured=ages.get(key);Double value=values.get(key);
        if(key.equals("speed_kmh"))value=speedForDisplay(value);
        boolean fresh=motorLive&&measured!=null&&measured>=motorSessionStart&&measured<=now&&now-measured<3000;
        ValueAssessment assessment=assessValue(key,value,fresh);
        boolean rpmRed=key.equals("rpm")&&fresh&&value!=null&&Double.isFinite(value)&&value>=ValueAlerts.DEFAULT_RPM_RED;
        dial.setReading(measured==null?null:value,fresh,rpmRed?ValueAssessment.RED:assessment.color(WHITE),rpmRed?1f:assessment.alpha(now),measured==null?-1:measured);
    }
    private ValueAssessment assessValue(String key,Double value,boolean fresh){
        if(key.equals("rpm")||key.equals("speed_kmh")){
            ValueAssessment tier=ValueAssessment.tiers(key,value,fresh,tierLimits(key),getSharedPreferences("display_alerts",MODE_PRIVATE).getBoolean(key+".tiers",true));
            if(key.equals("rpm")&&engineAssessment.level!=EngineValueGuide.Level.NEUTRAL&&value!=null&&value<tierLimits(key)[0])return ValueAssessment.combine(value,fresh,null,engineAssessment);
            return tier;
        }
        if(key.equals("oxygen_mv")&&getSharedPreferences("display_alerts",MODE_PRIVATE).getBoolean("lambda_context",true))return ValueAssessment.guide(value,fresh,lambdaAssessment);
        return ValueAssessment.range(value,fresh,lowAlerts.get(key),highAlerts.get(key));
    }
    private void showGuideConditions(){
        long now=SystemClock.elapsedRealtime();
        boolean live=motorLive();
        String issue=EngineValueGuide.v6Profile(recognizedReference,vehicleVin)?EngineValueGuide.contextIssue(recognizedReference,values,ages,now,live):"VIN de 406 Coupé XFZ et référence moteur à confirmer";
        LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(22),dp(12),dp(22),dp(12));
        TextView intro=text("406 Coupé V6 ES9J4 : couleurs du régime haut automatiques après identification et VIN correspondant. Le ralenti demande de confirmer ses conditions. Les autres motorisations ont leurs propres références.\n\n"+EngineValueGuide.HIGH_RPM_POLICY+"\n\n"+EngineValueGuide.POLICY,16,WHITE);body.addView(intro);
        CheckBox vehicle=new CheckBox(this);vehicle.setText("Mon véhicule : "+EngineValueGuide.VEHICLE);vehicle.setTextColor(WHITE);body.addView(vehicle);
        CheckBox conditions=new CheckBox(this);conditions.setText("Je confirme : "+EngineValueGuide.CONDITIONS);conditions.setTextColor(WHITE);body.addView(conditions);
        TextView state=text(issue==null?"Mesures compatibles avec le contrôle. Confirmer aussi les conditions ci-dessus.":issue+". Revenir ici pendant une lecture moteur réelle.",15,AMBER);body.addView(state);
        body.addView(text("Les contrôles automatiques de contexte (80–105 °C, papillon ≤1°, vitesse <1 km/h, tension ≥13 V) sont des filtres PP406. Ils ne sont pas des seuils de panne constructeur. Le contrôle se désactive après un arrêt, un test, une mesure ancienne ou un changement des conditions.",14,MUTED));
        ScrollView scroll=new ScrollView(this);scroll.addView(body);
        guideDialog=new AlertDialog.Builder(this).setTitle("Références et conditions").setView(scroll)
            .setPositiveButton("Activer le contrôle du ralenti",null).setNeutralButton("Désactiver",(d,w)->{engineGuide.disable("Contrôle désactivé");renderValues();})
            .setNegativeButton("Retour",null).create();guideDialog.show();
        Button activate=guideDialog.getButton(AlertDialog.BUTTON_POSITIVE);activate.setEnabled(false);
        android.widget.CompoundButton.OnCheckedChangeListener update=(b,checked)->activate.setEnabled(vehicle.isChecked()&&conditions.isChecked()&&issue==null);
        vehicle.setOnCheckedChangeListener(update);conditions.setOnCheckedChangeListener(update);
        activate.setOnClickListener(v->{
            boolean currentLive=motorLive();
            if(engineGuide.enable(recognizedReference,values,ages,SystemClock.elapsedRealtime(),currentLive,vehicle.isChecked()&&conditions.isChecked())){guideDialog.dismiss();renderValues();}
            else state.setText(engineGuide.evaluate(recognizedReference,values,ages,SystemClock.elapsedRealtime(),currentLive).explanation);
        });
    }
    private void showValueGuide(String[] signal){
        if(signal[0].equals("outside_c")){
            AlertDialog outsideDialog=new AlertDialog.Builder(this).setTitle("Température extérieure · sonde du rétroviseur")
                .setMessage("Le carré reste sur la page moteur. Pour le profil clim compatible, le relevé est effectué avant les valeurs moteur, puis toutes les heures avec pause et reprise automatiques. Les pauses prévues sont exclues du temps des graphiques. La date réelle du relevé reste conservée.\n\nCouleur du relevé : "+assessOutside().label+"\n"+assessOutside().explanation+"\nLa couleur évalue le dernier relevé, pas une nouvelle mesure en continu.\n\nUne lecture séparée est aussi possible après l’arrêt de la lecture en cours.")
                .setNegativeButton("Fermer",null).setPositiveButton("Lire séparément",(d,w)->readOutsideSeparately()).create();outsideDialog.show();
            outsideDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(!running&&!DiagnosticLease.held()&&!operationUi.open);return;
        }
        if(climateMeasurements.containsKey(signal[0])||signal[0].equals("outside_c")||signal[0].startsWith("climate_")||signal[0].equals("evaporator_c")||signal[0].equals("sunlight_raw")){
            Double value=climateMeasurements.get(signal[0]);
            new AlertDialog.Builder(this).setTitle(signal[1]).setMessage((value==null?"Aucune mesure":String.format(Locale.FRANCE,"%."+signal[3]+"f %s",value,signal[2]))+"\n\nValeur lue dans la climatisation Valeo 9630231877. Conversion issue du lecteur CAL5 et vérifiée sur deux trames enregistrées ; comparaison avec l’affichage du véhicule encore à faire, notamment sous 0 °C. Aucune plage de panne constructeur n’est appliquée. Les seuils du menu Alertes sont des réglages d’affichage.").setPositiveButton("Retour aux valeurs",null).show();return;
        }
        renderValues();long now=SystemClock.elapsedRealtime();String key=signal[0];Double value=values.get(key);Long stamp=ages.get(key);
        boolean fresh=motorLive()&&stamp!=null&&stamp>=motorSessionStart&&stamp<=now&&now-stamp<3000;
        Double shown=key.equals("speed_kmh")?speedForDisplay(value):value;
        String reading=shown==null?"Aucune mesure":String.format(Locale.FRANCE,"%."+signal[3]+"f %s",shown,signal[2]);
        EngineValueGuide.Help help=EngineValueGuide.help(key);
        ValueAssessment result=assessValue(key,shown,fresh);
        String assessment=result.label+"\n"+result.explanation;
        boolean v6=EngineValueGuide.v6Profile(recognizedReference,vehicleVin);
        String scope=v6?EngineValueGuide.VEHICLE+" · MP7.0 "+recognizedReference:"Fiche de référence du V6 ES9J4 ; application au véhicule non confirmée par le VIN et le calculateur. Aucune couleur constructeur n’est appliquée.";
        Double personal=(key.equals("rpm")||key.equals("speed_kmh"))?null:highAlerts.get(key);
        String detail="Relevé à l’ouverture de la fiche : "+reading+(value==null?"":fresh?" · en direct":" · ancienne mesure")
            +(stamp==null?"":" · il y a "+Math.max(0,(now-stamp)/1000)+" s")+"\n\n"+assessment
            +"\n\nVéhicule et portée\n"+scope
            +(key.equals("speed_kmh")&&value!=null?"\n\nÉtalonnage de l’affichage\nValeur décodée non modifiée : "+String.format(Locale.FRANCE,"%.0f km/h",value)+". Le cadran et la vue chiffres suivent les dix relevés fournis entre 30 et 120 km/h au compteur de la voiture. Les journaux et contrôles de diagnostic gardent la valeur décodée.":"")
            +"\n\nCe que mesure ce paramètre\n"+help.meaning
            +"\n\nValeurs constructeur et conditions\n"+help.reference
            +(key.equals("rpm")?"\n\nConditions du contrôle du ralenti\n"+EngineValueGuide.CONDITIONS:"")
            +"\n\nCouleurs de cette case\nVert : dans la plage ou dans la règle indiquée. Rouge : sous le minimum ou au-dessus du maximum appliqué. Les paliers jaune/orange du régime et de la vitesse sont conservés. Régime : clignotement rapide dès 6400 tr/min, lorsque la couleur est rouge. Neutre : référence manquante, conditions non réunies ou mesure ancienne. Une couleur ne prouve pas à elle seule une panne ni l’absence de panne."
            +((personal==null&&!lowAlerts.containsKey(key))?"":"\n\nLimites personnelles réglables dans Valeurs → Alertes : minimum "+lowAlerts.get(key)+", maximum "+personal+" "+signal[2]+". La règle applicable est détaillée en haut de cette fiche.")
            +"\n\nPistes à vérifier\n"+help.checks
            +"\n\nCes pistes orientent les contrôles ; elles ne désignent pas automatiquement une pièce à remplacer.";
        TextView content=text(detail,17,WHITE);content.setPadding(dp(24),dp(14),dp(24),dp(18));content.setTextIsSelectable(true);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        guideDialog=new AlertDialog.Builder(this).setTitle(signal[1]+" · aide au diagnostic").setView(scroll)
            .setPositiveButton("Retour aux valeurs",null).create();guideDialog.show();
    }
}
