package com.cockpit406.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.*;
import android.os.*;
import android.graphics.Color;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Engine-only recovery, entered only when the global engine identification fails. */
public final class VehicleRecoveryActivity extends Activity {
    private static final String PERMISSION="com.cockpit406.diagnostic.RECOVERY_USB";
    private final String permissionAction=PERMISSION+"."+UUID.randomUUID();
    private final UsbPermissionFlow usbPermission=new UsbPermissionFlow();
    private final Handler handler=new Handler();
    private final Map<Integer,JSONObject> profiles=new LinkedHashMap<>();
    private JSONArray models;private String brand;private JSONObject model;
    private LinearLayout page,primary;private TextView status,report,readings,journalInfo;
    private String journalMessage="";
    private Button readButton,liveButton,stopReading;
    private int readingTab;
    private ProgressBar progress;
    private final List<Button> actions=new ArrayList<>();
    private UsbManager manager;private UsbDevice pending;
    private volatile EngineReadSession session;
    private volatile boolean running,stopped,destroyed,resumed;
    private boolean permissionPending,returnAfterClose,exitAfterClose,contactCycleRequired,returnToGlobalScan;
    private int foundId;private String scanText="",identityText="",valuesText="",faultsText="";
    private String mp72ConfirmedIdentity="";
    private int mode;private boolean pendingContinuous;
    private final Map<String,Double> mp72Values=new LinkedHashMap<>();
    private final Map<String,Long> mp72Times=new LinkedHashMap<>();
    private final Map<String,TextView> mp72Fields=new LinkedHashMap<>();
    private AlertDialog helpDialog;
    private final Object sharingOwner=new Object();
    private final Runnable valueTicker=new Runnable(){public void run(){renderMp72();continueUsbPermission();if(!destroyed)handler.postDelayed(this,500);}};
    private final BroadcastReceiver receiver=new BroadcastReceiver(){
        public void onReceive(Context c,Intent i){
            UsbDevice d=i.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if(permissionAction.equals(i.getAction())&&permissionPending&&pending!=null&&d!=null&&pending.getDeviceName().equals(d.getDeviceName())) {
                UsbPermissionFlow.Reply reply=usbPermission.reply(i.getLongExtra("requestToken",-1),d.getDeviceName(),manager.hasPermission(d));
                android.util.Log.i("PP406UsbPermission","recovery reply="+reply+" callbackGranted="+i.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,false)+" resumed="+resumed);
                if(reply==UsbPermissionFlow.Reply.IGNORED)return;
                if(reply==UsbPermissionFlow.Reply.GRANTED){status.setText("Accès USB accordé · reprise à la fermeture de la fenêtre Android");handler.post(VehicleRecoveryActivity.this::continueUsbPermission);}
                else{permissionPending=false;pending=null;status.setText("Accès USB non accordé par Android · relance la lecture puis touche Autoriser.");}
                controls();
            } else if(UsbManager.ACTION_USB_DEVICE_DETACHED.equals(i.getAction())&&d!=null&&AndroidLexiaUsb.matches(d)){stop();status.setText("Lexia débranché · résultats conservés");}
        }
    };
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        manager=(UsbManager)getSystemService(USB_SERVICE);contactCycleRequired=getIntent().getBooleanExtra("contactCycleRequired",false);
        returnToGlobalScan=getIntent().getBooleanExtra("resumeGlobalScan",false);
        IntentFilter f=new IntentFilter(permissionAction);f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(receiver,f,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(receiver,f);
        try {
            JSONObject catalog=new JSONObject(new String(asset("engine-recovery.json"),StandardCharsets.UTF_8));
            models=catalog.getJSONArray("models");JSONArray a=catalog.getJSONArray("profiles");
            for(int i=0;i<a.length();i++){JSONObject p=a.getJSONObject(i);profiles.put(p.getInt("id"),p);}
            showBrands();
            handler.post(valueTicker);
        }catch(Exception e){layout("Choix du véhicule");page.addView(text("Catalogue indisponible : "+e.getMessage(),19));}
    }
    @Override protected void onResume(){super.onResume();resumed=true;handler.post(this::continueUsbPermission);}
    @Override protected void onPause(){resumed=false;if(running)stop();super.onPause();}
    @Override protected void onDestroy(){destroyed=true;stop();if(helpDialog!=null)helpDialog.dismiss();unregisterReceiver(receiver);handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private void ui(Runnable r){handler.post(()->{if(!destroyed)r.run();});}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setPadding(dp(8),dp(7),dp(8),dp(7));return t;}
    private Button button(String s,View.OnClickListener click){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setOnClickListener(click);return b;}
    private void layout(String title){
        mp72Fields.clear();
        actions.clear();LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(10),dp(16),dp(10));root.setBackgroundColor(Color.rgb(13,18,23));setContentView(root);
        LinearLayout nav=new LinearLayout(this);root.addView(nav);
        nav.addView(button("‹ Retour",v->back(false)),new LinearLayout.LayoutParams(0,dp(52),1));
        nav.addView(button("Ignorer et continuer",v->back(true)),new LinearLayout.LayoutParams(0,dp(52),1));
        root.addView(text(title,25));
        status=text(returnToGlobalScan?"Recherche globale en pause · retour ou Ignorer reprend la recherche des autres calculateurs.":"Moteur non reconnu · les résultats des autres calculateurs sont conservés.",16);root.addView(status);
        progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(View.GONE);root.addView(progress);
        primary=new LinearLayout(this);root.addView(primary);
        ScrollView scroll=new ScrollView(this);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));page=new LinearLayout(this);page.setOrientation(LinearLayout.VERTICAL);scroll.addView(page);
    }
    private void add(String label,Runnable next){Button b=button(label,v->next.run());page.addView(b,new LinearLayout.LayoutParams(-1,dp(56)));actions.add(b);}
    private void showBrands(){mode=0;brand=null;model=null;layout("Choisir une marque ou ignorer");add("Peugeot",()->showModels("Peugeot"));add("Citroën",()->showModels("Citroën"));page.addView(text(returnToGlobalScan?"Ignorer et continuer reprend le scan normal à partir des airbags, sans recommencer la recherche moteur.":"Ignorer donne accès aux calculateurs déjà trouvés, sans nouvel essai de communication.",17));}
    private void showModels(String chosen){
        mode=1;brand=chosen;model=null;layout(chosen+" · choisir le modèle");
        if(getIntent().getBooleanExtra("fullScan",false)&&chosen.equals("Peugeot")){
            status.setText("Choisir un modèle lance la recherche de ses calculateurs · les résultats déjà lus restent accessibles après le scan.");
            add("406 · toutes versions · Coupé prioritaire",()->selectFullPlan("family:406"));
            add("306 · toutes versions · prise 16 voies",()->selectFullPlan("family:306"));
            add("Détection automatique · tous les profils préparés",()->selectFullPlan("auto"));
        }
        try {for(int i=0;i<models.length();i++){final JSONObject m=models.getJSONObject(i);if(chosen.equals(m.getString("brand")))add(m.getString("label"),()->selectModel(m));}}
        catch(JSONException e){status.setText(e.getMessage());}
    }
    private void selectFullPlan(String key){setResult(RESULT_OK,new Intent().putExtra("vehicleKey",key).putExtra("contactCycleRequired",contactCycleRequired));finish();}
    private void selectModel(JSONObject m){
        if(getIntent().getBooleanExtra("fullScan",false)&&"Peugeot".equals(m.optString("brand"))){
            String name=m.optString("name");selectFullPlan(name.equals("306")||name.equals("306REMODELAGE")?"family:306":m.optString("key"));return;
        }
        showModel(m);
    }
    private void showModel(JSONObject m){
        if(model!=null&&!model.optString("key").equals(m.optString("key"))){foundId=0;scanText="";identityText="";valuesText="";faultsText="";mp72ConfirmedIdentity="";mp72Values.clear();mp72Times.clear();}
        mode=2;model=m;layout(brand+" · "+m.optString("label"));
        JSONArray ids=orderedProfiles(m);
        readButton=button(foundId==0?"Valider et lire les défauts":"Actualiser les défauts",v->{readingTab=0;if(running&&session!=null)session.rereadFaults();else connect(false);});
        primary.addView(readButton,new LinearLayout.LayoutParams(0,dp(60),1));
        liveButton=button("Valeurs en direct",v->{readingTab=1;connect(true);});primary.addView(liveButton,new LinearLayout.LayoutParams(0,dp(60),1));
        stopReading=button("Arrêter",v->stop());primary.addView(stopReading,new LinearLayout.LayoutParams(0,dp(60),1));
        LinearLayout tabs=new LinearLayout(this);page.addView(tabs);
        String[] names={"Défauts","Valeurs","Profils"};for(int i=0;i<names.length;i++){final int tab=i;Button b=button(names[i],v->{readingTab=tab;showModel(m);});b.setTextColor(i==readingTab?Color.rgb(214,241,57):Color.WHITE);tabs.addView(b,new LinearLayout.LayoutParams(0,dp(52),1));}
        report=text(scanText,17);page.addView(report);
        readings=text("",17);page.addView(readings);publish();
        journalInfo=text(journalMessage,14);page.addView(journalInfo);
        if("Peugeot".equals(brand))page.addView(button("Calculateurs répertoriés pour ce modèle",v->VehicleCatalogue.show(this,Integer.parseInt(m.optString("key").split(":")[1]))),new LinearLayout.LayoutParams(-1,dp(56)));
        if(foundId!=0)status.setText("Communication moteur établie · le profil accepté ne prouve pas à lui seul la variante.");
        else {
            page.addView(text(ids.length()>0?"Touche Valider et lire les défauts pour rechercher le moteur de ce modèle. Lecture seule · nouveaux profils à valider sur véhicule.":"Cette version n’a pas encore de profil de lecture exécutable. Choisis une version préparée ci-dessous ou reviens à la liste.",17));
            page.addView(text(m.optString("connector"),16));
        }
        if(ids!=null&&ids.length()>0&&foundId==0){
            if(readingTab==2){StringBuilder list=new StringBuilder("Profils à essayer : ");for(int i=0;i<ids.length();i++){JSONObject p=profiles.get(ids.optInt(i));if(p!=null)list.append(p.optString("title")).append(" [").append(p.optInt("id")).append("] · ");}page.addView(text(list.toString(),16));}
        } else if(ids==null||ids.length()==0){
            for(int i=0;i<models.length();i++){JSONObject alternative=models.optJSONObject(i);if(alternative==null||!brand.equals(alternative.optString("brand"))||alternative.optString("key").equals(m.optString("key")))continue;
                boolean related=m.optString("name").contains("306")&&alternative.optString("name").contains("306")||m.optString("name").contains("406")&&(alternative.optString("name").contains("406")||alternative.optString("name").startsWith("D9"));
                if(related&&orderedProfiles(alternative).length()>0)add("Choisir "+alternative.optString("label"),()->showModel(alternative));
            }
            add("Choisir un autre modèle",()->showModels(brand));
        }
        JSONArray other=m.optJSONArray("other_engine_types");if(readingTab==2&&foundId==0&&other!=null&&other.length()>0)page.addView(text("Autres familles restant à intégrer : "+other.toString(),15));
        if(foundId!=0){
            JSONObject found=profiles.get(foundId);boolean groups=false;JSONArray commands=found.optJSONArray("commands");
            for(int i=0;i<commands.length();i++)if(commands.optJSONObject(i).optString("name").startsWith("group"))groups=true;
            if(!groups)page.addView(text("Identification et défauts bruts préparés pour ce profil. Les groupes de valeurs nécessitent encore leur intégration.",17));
            if(found.optJSONArray("candidate_names").length()>1)page.addView(text("Table commune à plusieurs variantes : "+found.optJSONArray("candidate_names")+". La table seule ne suffit pas à identifier la variante.",16));
            liveButton.setVisibility(groups?View.VISIBLE:View.GONE);
        }
        if(mp72CandidateFor306()&&!mp72For306()){
            page.addView(text("Une réponse KWP cohérente ne suffit pas à reconnaître un MP7.2. Les valeurs restent brutes jusqu’à la confirmation du calculateur exact.",17));
            page.addView(button("Confirmer le calculateur MP7.2 de cette 306",v->new AlertDialog.Builder(this)
                .setTitle("Calculateur MP7.2 à confirmer")
                .setMessage("Confirme uniquement si l’étiquette du calculateur ou son identification dans PP2000 indique Bosch MP7.2. Le modèle 306 et la réponse KWP seuls ne suffisent pas. Les conversions préparées restent à comparer sur véhicule.")
                .setNegativeButton("Garder les réponses brutes",null)
                .setPositiveButton("MP7.2 vérifié",(d,w)->{mp72ConfirmedIdentity=identityText;mp72Values.clear();mp72Times.clear();showModel(m);}).show()),new LinearLayout.LayoutParams(-1,dp(56)));
        }
        if(mp72For306()&&readingTab==1){
            page.addView(text("Valeurs MP7.2 · conversions à confirmer sur véhicule",21));
            page.addView(text("Les mesures ci-dessous proviennent des réponses reçues. Les conversions restent à comparer avec PP2000 sur cette 306. Touchez une valeur pour les pistes de contrôle. Les alertes chiffrées attendent les références exactes de sa motorisation.",16));
            page.addView(button("Références moteur · 306 MP7.2",v->showMp72Help(Mp72Decoder.SIGNALS[0])),new LinearLayout.LayoutParams(-1,dp(56)));
            for(int i=0;i<Mp72Decoder.SIGNALS.length;i+=2){
                LinearLayout row=new LinearLayout(this);page.addView(row);
                for(int j=i;j<Math.min(i+2,Mp72Decoder.SIGNALS.length);j++){
                    final String[] signal=Mp72Decoder.SIGNALS[j];LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(dp(12),dp(10),dp(12),dp(10));card.setBackgroundColor(Color.rgb(24,33,42));
                    LinearLayout.LayoutParams size=new LinearLayout.LayoutParams(0,dp(146),1);size.setMargins(dp(4),dp(4),dp(4),dp(4));row.addView(card,size);
                    card.addView(text(signal[1],18));TextView value=text("—",26);card.addView(value);mp72Fields.put(signal[0],value);
                    card.addView(text(signal[2]+" · toucher pour l’aide",15));card.setOnClickListener(v->showMp72Help(signal));
                    card.setContentDescription(signal[1]+" · aide au diagnostic MP7.2");
                }
            }
            page.addView(text("Réponses brutes et état de la lecture",20));renderMp72();
        }
        controls();
    }
    private JSONArray orderedProfiles(JSONObject m){
        JSONArray source=m.optJSONArray("profiles"),out=new JSONArray();if(source==null)return out;
        boolean mp72="Peugeot".equals(m.optString("brand"))&&"306REMODELAGE".equals(m.optString("name"));
        if(mp72)for(int i=0;i<source.length();i++)if(source.optInt(i)==13)out.put(13);
        for(int i=0;i<source.length();i++)if(!mp72||source.optInt(i)!=13)out.put(source.optInt(i));return out;
    }
    private void controls(){for(Button b:actions)b.setEnabled(!running&&!permissionPending);if(mode==2&&readButton!=null){boolean supported=orderedProfiles(model).length()>0;readButton.setEnabled(supported&&!running&&!permissionPending);readButton.setVisibility(supported?View.VISIBLE:View.GONE);liveButton.setEnabled(supported&&foundId!=0&&!running&&!permissionPending);liveButton.setVisibility(foundId!=0?View.VISIBLE:View.GONE);stopReading.setVisibility(running?View.VISIBLE:View.GONE);stopReading.setEnabled(running&&!stopped);}if(progress!=null){progress.setIndeterminate(true);progress.setVisibility(running||permissionPending?View.VISIBLE:View.GONE);}}
    private byte[] asset(String name)throws IOException{try(InputStream in=getAssets().open(name);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}}
    private EngineReadSession.Profile parse(JSONObject p)throws JSONException{
        return VehicleReadPlans.parse(p);
    }
    private void connect(boolean continuous){
        if(running||permissionPending||model==null||!resumed)return;
        if(orderedProfiles(model).length()==0){status.setText("Choisis une version disposant d’un profil de lecture.");return;}
        if(continuous&&foundId==0){status.setText("Lance d’abord Valider et lire les défauts pour identifier le moteur.");return;}
        if(contactCycleRequired){new AlertDialog.Builder(this).setTitle("Avant une nouvelle lecture").setMessage("Voiture garée, moteur arrêté : coupe puis remets le contact pour terminer la précédente session de verrouillage.").setNegativeButton("Retour",null).setPositiveButton("Contact coupé puis remis",(d,w)->{contactCycleRequired=false;connect(continuous);}).show();return;}
        if(getSharedPreferences("lexia_link",0).getBoolean("resetRequired",false)){
            new AlertDialog.Builder(this).setTitle("Rétablir la liaison Lexia").setMessage("La précédente réponse USB n’a pas été terminée. Voiture garée, moteur arrêté, contact coupé : débranche le Lexia de la voiture ET de l’USB pendant 10 secondes, puis rebranche-le et remets le contact.")
                .setNegativeButton("Retour",null).setPositiveButton("Lexia débranché puis rebranché",(d,w)->{getSharedPreferences("lexia_link",0).edit().putBoolean("resetRequired",false).remove("reason").apply();connect(continuous);}).show();return;
        }
        UsbDevice device=null;for(UsbDevice d:manager.getDeviceList().values())if(AndroidLexiaUsb.matches(d)){device=d;break;}
        if(device==null){status.setText("Lexia absent · branche-le à la tablette en OTG. Retour et Ignorer restent disponibles.");return;}
        if(manager.hasPermission(device))start(device,continuous);
        else{
            pending=device;pendingContinuous=continuous;permissionPending=true;controls();
            long token=usbPermission.request(device.getDeviceName());
            int flags=PendingIntent.FLAG_CANCEL_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0);
            manager.requestPermission(device,PendingIntent.getBroadcast(this,1,new Intent(permissionAction).setPackage(getPackageName()).putExtra("requestToken",token),flags));
        }
    }
    private void continueUsbPermission(){
        if(!resumed||destroyed||running||DiagnosticLease.held()||!usbPermission.approved())return;
        UsbDevice device=manager.getDeviceList().get(usbPermission.device());
        UsbPermissionFlow.Ready result=usbPermission.resume(resumed,device!=null,device!=null&&manager.hasPermission(device));
        if(result==UsbPermissionFlow.Ready.NONE)return;
        permissionPending=false;pending=null;
        android.util.Log.i("PP406UsbPermission","recovery resume="+result);
        if(result==UsbPermissionFlow.Ready.START)start(device,pendingContinuous);
        else{status.setText(result==UsbPermissionFlow.Ready.DETACHED?"Lexia débranché avant le début de la lecture · rebranche-le en OTG":"Accès USB retiré par Android · relance la lecture pour autoriser");controls();}
    }
    private void start(UsbDevice device,boolean continuous){
        if(running||destroyed||!resumed)return;DiagnosticLease.Token lease=DiagnosticLease.acquire();
        if(lease==null){status.setText("Attends la fermeture de la précédente session · Retour reste disponible.");return;}
        mp72Times.clear();DiagnosticDataHub.inactive(sharingOwner);
        running=true;stopped=false;returnAfterClose=false;exitAfterClose=false;controls();progress.setIndeterminate(true);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final JSONObject selected=model;final int selectedId=foundId;
        new Thread(()->{
            String outcome="Recherche terminée · résultats conservés";
            try(SessionJournal log=openJournal("engine-recovery");
                AndroidLexiaUsb link=new AndroidLexiaUsb(manager,device,(dir,data)->writeLog(log,"usb",dir,VciUsbProtocol.hex(data),""))) {
                writeLog(log,"start",BuildInfo.VERSION,selected.optString("label"),continuous?"live_read_only":"discovery_read_only");
                VciUsbProtocol protocol=new VciUsbProtocol(link,20000,(elapsed,limit)->ui(()->status.setText("Lexia occupé · "+elapsed/1000+" s · attente de la réponse avant de continuer")));
                JSONArray ids=selectedId!=0?new JSONArray().put(selectedId):orderedProfiles(selected);
                if(!continuous){scanText="";foundId=0;identityText="";valuesText="";faultsText="";ui(()->{mp72ConfirmedIdentity="";mp72Values.clear();mp72Times.clear();});}
                for(int i=0;i<ids.length()&&!stopped;i++) {
                    final int id=ids.getInt(i);final JSONObject p=profiles.get(id);if(p==null)continue;
                    final boolean[] identified={false};final String title=p.getString("title");
                    scanText+="… "+title+" ["+id+"] · recherche\n";publish();
                    EngineReadSession current=new EngineReadSession(parse(p),asset(p.getJSONArray("assets").getString(0)),asset(p.getJSONArray("assets").getString(1)),body->{
                        try{return protocol.exchange(body);}catch(IOException e){if(protocol.needsRecovery())markReset(e.getMessage());throw e;}
                    },new EngineReadSession.Listener(){
                        public void record(String name,byte[] req,byte[] res)throws IOException{writeLog(log,"command",name,VciUsbProtocol.hex(req),VciUsbProtocol.hex(res));}
                        public void state(String message){ui(()->status.setText((stopped?"Arrêt demandé · ":"")+message));}
                        public void identity(String raw){identified[0]=true;foundId=id;identityText=title+" · "+raw;ui(()->{if(!identityText.equals(mp72ConfirmedIdentity)){mp72ConfirmedIdentity="";mp72Values.clear();mp72Times.clear();}});scanText+="✓ Profil "+title+" · réponse d’identification cohérente, variante à confirmer\n";publish();}
                        public void values(String message){valuesText=message;publish();}
                        public void measurements(int profileId,Map<String,Double> values){
                            final long stamp=SystemClock.elapsedRealtime();final Map<String,Double> sample=new LinkedHashMap<>(values);
                            ui(()->{if(!mp72For306())return;for(Map.Entry<String,Double> e:sample.entrySet()){mp72Values.put(e.getKey(),e.getValue());mp72Times.put(e.getKey(),stamp);}renderMp72();});
                        }
                        public void faults(String message){faultsText=message;publish();}
                    },millis->{try{Thread.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IOException("Lecture interrompue",e);}});
                    session=current;if(stopped)current.stop();
                    try{current.run(continuous?0:1);}
                    catch(IOException e){scanText+="⚠ "+title+" · "+e.getMessage()+"\n";publish();if(!current.mayTryAnotherProfile()){markReset(e.getMessage());throw e;}}
                    finally{session=null;}
                    if(stopped||identified[0])break;
                }
                if(stopped)outcome="Lecture arrêtée · résultats conservés";
                else if(foundId!=0)outcome="Profil de communication accepté · variante du moteur à confirmer avant toute conversion chiffrée.";
                else outcome="Aucun moteur reconnu par les profils essayés · cela ne prouve pas un défaut de broche ou l’absence du calculateur.";
            }catch(Exception e){outcome="Lecture interrompue : "+e.getMessage()+" · résultats déjà reçus conservés";}
            finally{session=null;running=false;lease.close();final String result=outcome;ui(()->{getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);status.setText(result);showModel(selected);status.setText(result);if(returnAfterClose||exitAfterClose){boolean exit=exitAfterClose;returnAfterClose=false;exitAfterClose=false;back(exit);}});}
        },"PP406-engine-recovery").start();
    }
    private void publish(){final String r=scanText;ui(()->{if(report!=null){report.setText(r);report.setVisibility(readingTab==2?View.VISIBLE:View.GONE);}if(readings!=null)readings.setText("Identification : "+(identityText.isEmpty()?"en attente":identityText)+"\n\n"+(readingTab==1?(valuesText.isEmpty()?"Aucune valeur lue · lance la recherche puis Valeurs en direct.":valuesText):(faultsText.isEmpty()?"Aucun défaut lu · touche Valider et lire les défauts.":faultsText)));});}
    private void renderMp72(){
        long now=SystemClock.elapsedRealtime();
        for(String[] s:Mp72Decoder.SIGNALS){TextView field=mp72Fields.get(s[0]);if(field==null)continue;Double value=mp72Values.get(s[0]);Long stamp=mp72Times.get(s[0]);
            boolean fresh=running&&!stopped&&resumed&&stamp!=null&&stamp<=now&&now-stamp<3000;
            field.setText(value==null?"—":String.format(Locale.FRANCE,"%."+s[3]+"f",value)+(fresh?"":" · ancien"));field.setAlpha(fresh?1f:0.45f);
        }
        if(mp72For306())DiagnosticDataHub.publish(sharingOwner,"Peugeot · "+model.optString("label"),"MP7.2","",Mp72Decoder.SIGNALS,mp72Values,mp72Times,running&&!stopped&&resumed,now,"Conversions MP7.2 à confirmer sur véhicule ; plages officielles non intégrées");
    }
    private boolean mp72CandidateFor306(){return foundId==13&&model!=null&&"Peugeot".equals(model.optString("brand"))&&("306".equals(model.optString("name"))||"306REMODELAGE".equals(model.optString("name")));}
    private boolean mp72For306(){return mp72CandidateFor306()&&Mp72Decoder.confirmedIdentity(identityText,mp72ConfirmedIdentity);}
    private void showMp72Help(String[] signal){
        EngineValueGuide.Help help=Mp72Decoder.help(signal[0]);Double value=mp72Values.get(signal[0]);Long stamp=mp72Times.get(signal[0]);long now=SystemClock.elapsedRealtime();
        boolean fresh=running&&!stopped&&resumed&&stamp!=null&&stamp<=now&&now-stamp<3000;
        String reading=value==null?"Aucune mesure reçue":String.format(Locale.FRANCE,"%."+signal[3]+"f %s",value,signal[2])+(fresh?" · en direct":" · ancienne mesure");
        TextView body=text("Relevé à l’ouverture : "+reading+"\n\nCe que mesure ce paramètre\n"+help.meaning+"\n\nRéférences et limites\n"+help.reference+"\n\nPistes à vérifier\n"+help.checks,17);
        body.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(body);
        helpDialog=new AlertDialog.Builder(this).setTitle(signal[1]+" · 306 MP7.2").setView(scroll).setPositiveButton("Retour aux valeurs",null).create();helpDialog.show();
    }
    private SessionJournal openJournal(String prefix){
        SessionJournal journal=SessionJournal.open(getFilesDir(),getCacheDir(),prefix,message->ui(()->{journalMessage=message;if(journalInfo!=null)journalInfo.setText(message);}));
        ui(()->{journalMessage=journal.description();if(journalInfo!=null)journalInfo.setText(journalMessage);});
        return journal;
    }
    private static void writeLog(PrintWriter log,String kind,String name,String request,String response)throws IOException{
        try{JSONObject row=new JSONObject();row.put("time_ms",System.currentTimeMillis());row.put("kind",kind);row.put("name",name);row.put("request",request);row.put("response",response);log.println(row.toString());log.flush();}
        catch(JSONException e){throw new IOException("Création du journal impossible",e);}
    }
    private void markReset(String reason){getSharedPreferences("lexia_link",0).edit().putBoolean("resetRequired",true).putString("reason",reason).apply();}
    private void stop(){usbPermission.cancel();permissionPending=false;pending=null;stopped=true;DiagnosticDataHub.inactive(sharingOwner);EngineReadSession s=session;if(s!=null)s.stop();}
    @Override public void onBackPressed(){back(false);}
    private void back(boolean exit){
        if(running){returnAfterClose=true;exitAfterClose=exit;stop();status.setText("Retour demandé · attente de la réponse et de la fermeture propre");return;}
        if(permissionPending){status.setText("Ferme d’abord la demande d’autorisation USB pour revenir au menu.");return;}
        if(exit||mode==0){setResult(RESULT_OK,new Intent().putExtra("contactCycleRequired",contactCycleRequired));finish();return;}
        if(mode==2){scanText="";foundId=0;identityText="";valuesText="";faultsText="";showModels(brand);}else showBrands();
    }
}
