package com.cockpit406.diagnostic;

import android.app.job.*;
import android.content.*;
import org.json.JSONObject;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;

/** Private HTTPS uploads run on their own worker, never on the diagnostic thread. */
public final class TripBackups {
    private static final String BASE="https://406coupeclub.novalysdev.fr/pp406/backups/v1/";
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final java.util.concurrent.atomic.AtomicBoolean BUSY=new java.util.concurrent.atomic.AtomicBoolean();
    public static volatile String status="Sauvegarde réseau en attente";
    public static void start(Context context){
        Context app=context.getApplicationContext();
        try{JobScheduler jobs=(JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobs.schedule(new JobInfo.Builder(40615,new ComponentName(app,TripBackupJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());
        }catch(RuntimeException ignored){}
        kick(app,null,null);
    }
    public static void kick(Context context,FuelRecording recording,Runnable completion){
        Context app=context.getApplicationContext();
        if(!BUSY.compareAndSet(false,true)){if(completion!=null)completion.run();return;}
        WORKER.execute(()->{
            try{if(recording!=null)recording.checkpoint();upload(app);}
            catch(Exception e){status="Sauvegarde en attente de réseau · fichiers conservés sur la tablette";}
            finally{BUSY.set(false);if(completion!=null)completion.run();}
        });
    }
    private static void upload(Context context)throws Exception{
        File[] files=new File(context.getFilesDir(),"fuel-recordings").listFiles((dir,name)->name.matches("trip-[0-9]+-[A-Za-z0-9_-]+\\.jsonl"));
        if(files==null||files.length==0){status="Aucun trajet à sauvegarder";return;}
        Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
        SharedPreferences prefs=context.getSharedPreferences("trip_backups",Context.MODE_PRIVATE);
        String id=prefs.getString("installation",""),token=prefs.getString("token","");
        if(id.isEmpty()||token.isEmpty()){
            id=UUID.randomUUID().toString();byte[] random=new byte[32];new SecureRandom().nextBytes(random);
            StringBuilder secret=new StringBuilder();for(byte b:random)secret.append(String.format(Locale.ROOT,"%02x",b&255));token=secret.toString();
            if(!prefs.edit().putString("installation",id).putString("token",token).commit())throw new IOException("Identité locale non enregistrée");
        }
        if(!prefs.getBoolean("registered",false)){
            JSONObject registration=new JSONObject();registration.put("installation",id);registration.put("token",token);
            Reply reply=post("register",registration.toString().getBytes(StandardCharsets.UTF_8),null,null,null,0,null);
            if(reply.code!=200||!reply.body.optBoolean("registered"))throw new IOException("Inscription indisponible");
            if(!prefs.edit().putBoolean("registered",true).commit())throw new IOException("Inscription locale non enregistrée");
        }
        long deadline=android.os.SystemClock.elapsedRealtime()+25000;boolean pending=false;int chunks=0;
        for(File file:files){
            long offset=prefs.getLong(file.getName(),0);if(offset>file.length())throw new IOException("Journal réduit depuis la sauvegarde");
            while(offset<file.length()){
                if(android.os.SystemClock.elapsedRealtime()>deadline||chunks>=64){pending=true;break;}
                byte[] data=BackupChunks.read(file,offset);if(data.length==0)break;
                status="Sauvegarde des trajets en cours";String digest=BackupChunks.digest(data);
                Reply reply=post("chunks",data,id,token,file.getName(),offset,digest);
                if(reply.code==403){prefs.edit().putBoolean("registered",false).commit();throw new IOException("Accès à renouveler");}
                long next=reply.body.optLong("nextOffset",-1);
                if((reply.code!=200&&reply.code!=409)||next<0||next>file.length()||(reply.code==200&&next<offset+data.length))throw new IOException("Sauvegarde non confirmée");
                if(reply.code==200&&reply.body.has("sha256")&&!digest.equals(reply.body.optString("sha256")))throw new IOException("Empreinte non confirmée");
                if(next==offset)throw new IOException("Aucune progression");
                if(!prefs.edit().putLong(file.getName(),next).putLong("confirmedAt",System.currentTimeMillis()).commit())throw new IOException("Position non enregistrée");
                offset=next;chunks++;
            }
            if(pending)break;
        }
        long confirmed=prefs.getLong("confirmedAt",0);
        status=pending?"Sauvegarde partielle · suite automatique":confirmed==0?"Sauvegarde en attente":"Trajets sauvegardés · confirmation "+new java.text.SimpleDateFormat("HH:mm",Locale.FRANCE).format(new Date(confirmed));
    }
    private static final class Reply{final int code;final JSONObject body;Reply(int code,JSONObject body){this.code=code;this.body=body;}}
    private static Reply post(String path,byte[] data,String id,String token,String name,long offset,String digest)throws Exception{
        HttpsURLConnection connection=(HttpsURLConnection)new URL(BASE+path).openConnection();
        try{
            connection.setConnectTimeout(8000);connection.setReadTimeout(12000);connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(data.length);
            connection.setRequestProperty("Content-Type",id==null?"application/json":"application/octet-stream");
            if(id!=null){connection.setRequestProperty("Authorization","Bearer "+token);connection.setRequestProperty("X-Installation",id);connection.setRequestProperty("X-Trip",name);connection.setRequestProperty("X-Offset",Long.toString(offset));connection.setRequestProperty("X-Chunk-SHA256",digest);connection.setRequestProperty("X-PP406-Version",BuildInfo.VERSION);}
            try(OutputStream out=connection.getOutputStream()){out.write(data);}
            int code=connection.getResponseCode();InputStream input=code<400?connection.getInputStream():connection.getErrorStream();
            if(input==null)throw new IOException("Aucune confirmation");
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=input){byte[] buffer=new byte[1024];int count;while((count=in.read(buffer))!=-1){bytes.write(buffer,0,count);if(bytes.size()>8192)throw new IOException("Réponse trop longue");}}
            return new Reply(code,new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)));
        }finally{connection.disconnect();}
    }
    private TripBackups(){}
}
