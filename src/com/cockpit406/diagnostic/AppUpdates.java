package com.cockpit406.diagnostic;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Explicit network updates. Downloads are data until digest, identity and signer checks pass. */
public final class AppUpdates {
    public static final String PACKAGE="com.cockpit406.diagnostic";
    public static final String AUTHORITY=PACKAGE+".updates";
    public static final String DEFAULT_SOURCE="https://406coupeclub.novalysdev.fr/pp406/updates/release.json";
    private static final long MAX_APK=160L*1024*1024;
    public interface Progress {void bytes(long current,long total);}
    public static final class Release {
        public final String version,sha,notes;public final int code;public final long bytes;public final URL apk;
        Release(JSONObject json,URL source)throws Exception{
            if(json.getInt("schema")!=1||!PACKAGE.equals(json.getString("package")))throw new IOException("Catalogue de mise à jour incompatible");
            version=json.getString("version");code=json.getInt("versionCode");sha=json.getString("sha256").toLowerCase(Locale.ROOT);bytes=json.getLong("bytes");notes=json.optString("notes","");
            if(version.isEmpty()||version.length()>40||code<1||!sha.matches("[a-f0-9]{64}")||bytes<1||bytes>MAX_APK||notes.length()>4000)throw new IOException("Catalogue incomplet ou incorrect");
            apk=checkedUrl(new URL(source,json.getString("apk")).toString());
            if(!source.getProtocol().equals(apk.getProtocol())||!source.getHost().equals(apk.getHost())||source.getPort()!=apk.getPort())throw new IOException("L’APK doit provenir du même serveur que le catalogue");
        }
    }
    public static URL checkedUrl(String value)throws IOException{
        URL url=new URL(value);
        if(url.getUserInfo()!=null||url.getRef()!=null)throw new IOException("Adresse sans identifiants ni fragment attendue");
        if(url.getProtocol().equals("https"))return url;
        if(url.getProtocol().equals("http")){
            // Plain HTTP is only allowed to a literal private address on the local network.
            String host=url.getHost();
            if(host.matches("[0-9.]+")){
                InetAddress address=InetAddress.getByName(host);
                if(address.isSiteLocalAddress()||address.isLoopbackAddress()||address.isLinkLocalAddress())return url;
            }
        }
        throw new IOException("Utilise HTTPS, ou http:// suivi d’une adresse IP privée du réseau local");
    }
    private static HttpURLConnection connection(URL url)throws IOException{
        checkedUrl(url.toString());HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(60000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("Accept-Encoding","identity");return c;
    }
    private static HttpURLConnection open(URL url)throws IOException{
        HttpURLConnection c=connection(url);
        try{int status=c.getResponseCode();if(status!=200)throw new IOException("Serveur HTTP "+status+" · vérifier l’adresse et le réseau");return c;}catch(IOException e){c.disconnect();throw e;}
    }
    public static Release check(String source)throws Exception{
        URL url=checkedUrl(source);HttpURLConnection c=open(url);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>32768)throw new IOException("Catalogue trop volumineux");out.write(buffer,0,n);}
            return new Release(new JSONObject(out.toString("UTF-8")),url);
        }finally{c.disconnect();}
    }
    public static void verifyArchive(Context context,File file,Release release)throws Exception{
        if(file.length()!=release.bytes)throw new IOException("Taille de téléchargement incorrecte");
        MessageDigest digest=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] b=new byte[32768];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        if(!hex(digest.digest()).equals(release.sha))throw new IOException("Empreinte du téléchargement incorrecte");
        PackageManager pm=context.getPackageManager();PackageInfo candidate=pm.getPackageArchiveInfo(file.getPath(),PackageManager.GET_SIGNATURES),current=pm.getPackageInfo(PACKAGE,PackageManager.GET_SIGNATURES);
        if(candidate==null||!PACKAGE.equals(candidate.packageName)||candidate.versionCode!=release.code||!release.version.equals(candidate.versionName))throw new IOException("Identité ou version de l’APK incorrecte");
        if(candidate.signatures==null||candidate.signatures.length!=1||current.signatures==null||current.signatures.length!=1||!candidate.signatures[0].equals(current.signatures[0]))throw new IOException("Signature différente de PP406 installée · mise à jour refusée");
    }
    private static String hex(byte[] b){StringBuilder out=new StringBuilder();for(byte v:b)out.append(String.format(Locale.ROOT,"%02x",v&255));return out.toString();}
    static File apkFile(Context context){return new File(new File(context.getCacheDir(),"app-updates"),"update.apk");}
    public static synchronized File download(Context context,Release release,Progress progress)throws Exception{
        File target=apkFile(context),directory=target.getParentFile();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Stockage de mise à jour inaccessible");
        File part=new File(directory,"update.part"),meta=new File(directory,"update.part.meta");
        String identity=release.sha+"\n"+release.bytes+"\n"+release.apk.toString();
        String stored="";
        if(meta.isFile())try(InputStream in=new FileInputStream(meta);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[256];int n;while((n=in.read(b))!=-1){if(out.size()+n>1024)break;out.write(b,0,n);}stored=out.toString("UTF-8");
        }
        if(!identity.equals(stored)||part.length()>release.bytes){if(part.exists()&&!part.delete())throw new IOException("Ancien téléchargement verrouillé");}
        try(FileOutputStream out=new FileOutputStream(meta)){out.write(identity.getBytes("UTF-8"));}
        Exception last=null;
        for(int attempt=0;attempt<5;attempt++){
            HttpURLConnection c=null;
            try{
                long offset=part.length();
                if(offset<release.bytes){
                    c=connection(release.apk);
                    if(offset>0)c.setRequestProperty("Range","bytes="+offset+"-");
                    int status=c.getResponseCode();
                    if(status==206){
                        String range=c.getHeaderField("Content-Range");
                        if(range==null||!range.matches("bytes "+offset+"-[0-9]+/"+release.bytes))throw new IOException("Reprise du téléchargement incorrecte");
                    }else if(status==200){offset=0;}
                    else throw new IOException("Serveur HTTP "+status+" pendant le téléchargement");
                    long total=offset;
                    try(InputStream in=c.getInputStream();OutputStream out=new FileOutputStream(part,offset>0)){
                        byte[] buffer=new byte[32768];int n;long lastProgress=0;
                        while((n=in.read(buffer))!=-1){total+=n;if(total>release.bytes)throw new IOException("Téléchargement plus grand qu’annoncé");out.write(buffer,0,n);long now=SystemClock.elapsedRealtime();if(now-lastProgress>200){progress.bytes(total,release.bytes);lastProgress=now;}}
                    }
                }
                if(part.length()!=release.bytes)throw new IOException("Téléchargement interrompu à "+part.length()+" octets");
                verifyArchive(context,part,release);
                if(target.exists()&&!target.delete())throw new IOException("Ancienne mise à jour verrouillée");
                if(!part.renameTo(target))throw new IOException("Impossible de préparer l’installation");
                meta.delete();progress.bytes(release.bytes,release.bytes);return target;
            }catch(Exception e){last=e;if(e.getMessage()!=null&&(e.getMessage().contains("Empreinte")||e.getMessage().contains("Signature")||e.getMessage().contains("Identité"))){part.delete();meta.delete();throw e;}}
            finally{if(c!=null)c.disconnect();}
            if(attempt<4)Thread.sleep(1500L*(attempt+1));
        }
        throw new IOException("Téléchargement impossible après plusieurs essais : "+last.getMessage(),last);
    }
    public static void setSource(Context context,String source)throws IOException{checkedUrl(source);context.getSharedPreferences("app_updates",0).edit().putString("source",source).apply();}
    public static String source(Context context){return DEFAULT_SOURCE;}
    public static void show(Activity activity,BooleanSupplier busy){new UpdateDialog(activity,busy).show();}
    private static final class UpdateDialog {
        final Activity activity;final BooleanSupplier busy;final AlertDialog dialog;final TextView status;final ProgressBar progress;final Button check,install;final Handler handler=new Handler();
        volatile boolean downloading;Release release;File ready;
        UpdateDialog(Activity activity,BooleanSupplier busy){
            this.activity=activity;this.busy=busy;
            LinearLayout body=new LinearLayout(activity);body.setOrientation(1);body.setPadding(26,12,26,8);body.setFocusableInTouchMode(true);
            TextView info=new TextView(activity);info.setText("Version installée : "+BuildInfo.VERSION+"\nConnecte la tablette à Internet, par Wi-Fi ou avec le partage de connexion du téléphone. Les mises à jour sont téléchargées directement depuis le serveur Novalys Apps. Tes données sont conservées.");body.addView(info);
            CheckBox automatic=new CheckBox(activity);automatic.setText("Installer automatiquement à distance (tablette rootée)");automatic.setChecked(RemoteUpdatesService.enabled(activity));body.addView(automatic);
            TextView remoteStatus=new TextView(activity);remoteStatus.setText(RemoteUpdatesService.status(activity));body.addView(remoteStatus);
            automatic.setOnCheckedChangeListener((button,enabled)->{
                if(!enabled){RemoteUpdatesService.disable(activity);remoteStatus.setText(RemoteUpdatesService.status(activity));return;}
                button.setEnabled(false);remoteStatus.setText("Autorisation root de PP406 en cours…");
                RemoteUpdatesService.enable(activity,message->{if(activity.isFinishing())return;button.setEnabled(true);button.setChecked(RemoteUpdatesService.enabled(activity));remoteStatus.setText(message);});
            });
            progress=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);body.addView(progress);
            status=new TextView(activity);status.setText("Vérifier, puis télécharger. L’installation ne démarre qu’après ton choix et hors diagnostic en cours.");body.addView(status);
            check=new Button(activity);check.setText("Vérifier les mises à jour");body.addView(check);
            install=new Button(activity);install.setText("Installer la mise à jour");install.setEnabled(false);body.addView(install);
            dialog=new AlertDialog.Builder(activity).setTitle("Mises à jour · sans USB").setView(body).setNegativeButton("Fermer",null).create();
            check.setOnClickListener(v->check());install.setOnClickListener(v->install());body.requestFocus();
            dialog.setOnDismissListener(d->handler.removeCallbacksAndMessages(null));
        }
        void show(){dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);dialog.show();check();handler.post(new Runnable(){public void run(){if(!dialog.isShowing())return;install.setEnabled(ready!=null&&!downloading&&!busy.getAsBoolean());handler.postDelayed(this,500);}});}
        void ui(Runnable task){activity.runOnUiThread(()->{if(dialog.isShowing()&&!activity.isFinishing())task.run();});}
        void check(){
            if(downloading)return;final String address=AppUpdates.source(activity);ready=null;release=null;install.setEnabled(false);check.setEnabled(false);downloading=true;progress.setIndeterminate(true);status.setText("Connexion au serveur de mise à jour…");
            new Thread(()->{
                try{Release r=AppUpdates.check(address);int installed=activity.getPackageManager().getPackageInfo(PACKAGE,0).versionCode;
                    ui(()->{release=r;progress.setIndeterminate(false);progress.setProgress(0);if(r.code<=installed){status.setText("PP406 "+BuildInfo.VERSION+" est à jour sur ce serveur.");check.setText("Vérifier à nouveau");check.setOnClickListener(v->check());}else{status.setText("Version "+r.version+" disponible · "+(r.bytes/1024)+" Ko\n"+r.notes);check.setText("Télécharger "+r.version);check.setOnClickListener(v->download());}});
                }catch(Exception e){ui(()->status.setText("Mise à jour indisponible : "+e.getMessage()));}
                finally{downloading=false;ui(()->{progress.setIndeterminate(false);check.setEnabled(true);});}
            },"PP406-update-check").start();
        }
        void download(){
            if(downloading||release==null)return;final Release chosen=release;downloading=true;check.setEnabled(false);status.setText("Téléchargement de PP406 "+chosen.version+"…");
            new Thread(()->{try{File file=AppUpdates.download(activity,chosen,(n,total)->ui(()->progress.setProgress((int)(n*100/total))));ui(()->{ready=file;status.setText("PP406 "+chosen.version+" téléchargée · empreinte, identité et signature vérifiées.\n"+(busy.getAsBoolean()?"Arrête le diagnostic pour installer.":"Prête à installer. Android demandera de confirmer la mise à jour."));});}
                catch(Exception e){ui(()->status.setText("Téléchargement refusé : "+e.getMessage()));}
                finally{downloading=false;ui(()->{check.setText("Vérifier à nouveau");check.setOnClickListener(v->check());check.setEnabled(true);});}
            },"PP406-update-download").start();
        }
        void install(){
            if(ready==null||busy.getAsBoolean()){status.setText("Arrête le diagnostic avant d’installer la mise à jour.");return;}
            try{if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()){activity.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+PACKAGE)));return;}
                Intent intent=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(Uri.parse("content://"+AUTHORITY+"/apk"),"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);activity.startActivity(intent);dialog.dismiss();
            }catch(Exception e){status.setText("Installation non lancée : "+e.getMessage()+"\nSur Android 7, autorise les sources inconnues dans les paramètres de sécurité si Android le demande.");}
        }
    }
}
