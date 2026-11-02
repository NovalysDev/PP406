package com.cockpit406.diagnostic;

import android.app.job.*;
import android.content.*;
import android.os.Build;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in private diagnostic upload; independent of trip backups and vehicle I/O. */
public final class DiagnosticBackups {
    private static final String BASE="https://406coupeclub.novalysdev.fr/pp406/backups/v1/";
    private static final String PREFS="diagnostic_backups";
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY=new AtomicBoolean();
    public static volatile String status="Envoi des journaux désactivé";
    public interface FeedbackCallback { void complete(boolean success,String message); }
    public interface TriageCallback { void complete(String kind,String text,boolean ai); }
    private static SharedPreferences prefs(Context context){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static boolean asked(Context context){return prefs(context).getBoolean("asked",false);}
    public static boolean enabled(Context context){return prefs(context).getBoolean("enabled",false);}
    private static List<LogFile> files(Context context){
        ArrayList<LogFile> found=new ArrayList<>();
        File[] roots={context.getFilesDir(),context.getCacheDir()};String[] labels={"files","cache"};
        for(int i=0;i<roots.length;i++){
            File[] logs=new File(roots[i],"sessions").listFiles((dir,name)->name.matches("[A-Za-z0-9_.-]+\\.jsonl")&&!name.contains(".."));
            if(logs!=null)for(File log:logs)if(log.isFile()&&!log.isDirectory())found.add(new LogFile(log,"diag-"+labels[i]+"-"+log.getName()));
        }
        found.sort((a,b)->Long.compare(a.file.lastModified(),b.file.lastModified()));return found;
    }
    /** Called only after an explicit choice. Existing journals are never uploaded retroactively. */
    public static boolean decide(Context context,boolean accept){
        SharedPreferences storage=prefs(context);HashSet<String> excluded=new HashSet<>(storage.getStringSet("excluded",Collections.emptySet()));
        if(accept)for(LogFile file:files(context))excluded.add(file.name);
        boolean saved=storage.edit().putBoolean("asked",true).putBoolean("enabled",accept).putStringSet("excluded",excluded).commit();
        if(saved){status=accept?"Journaux techniques activés":"Envoi des journaux désactivé";if(accept){SupportLogs.rotate();SupportLogs.event(context,"consent",null);start(context);}}
        return saved;
    }
    private static void schedule(Context context){
        Context app=context.getApplicationContext();
        try{JobScheduler jobs=(JobScheduler)app.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            jobs.schedule(new JobInfo.Builder(40616,new ComponentName(app,DiagnosticBackupJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(15*60*1000L).setPersisted(true).build());
            if(hasPendingFeedback(app))jobs.schedule(new JobInfo.Builder(40617,new ComponentName(app,DiagnosticBackupJob.class)).setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setBackoffCriteria(30000,JobInfo.BACKOFF_POLICY_LINEAR).setPersisted(true).build());
        }catch(RuntimeException ignored){}
    }
    public static void start(Context context){
        if(enabled(context)||hasPendingFeedback(context)){schedule(context);kick(context,null);}
    }
    public static boolean hasPendingFeedback(Context context){return !prefs(context).getString("pending_feedback","[]").equals("[]");}
    public static void kick(Context context,Runnable completion){
        Context app=context.getApplicationContext();
        if(!enabled(app)&&!hasPendingFeedback(app)){if(completion!=null)completion.run();return;}
        if(!BUSY.compareAndSet(false,true)){if(completion!=null)completion.run();return;}
        WORKER.execute(()->{try{flushFeedback(app);if(enabled(app))upload(app);}catch(Exception e){status="Envoi en attente · conservé sur la tablette";}
            finally{BUSY.set(false);if(completion!=null)completion.run();}});
    }
    public static void sendFeedback(Context context,String message,FeedbackCallback callback){
        queueFeedback(context,message,null,"",false,callback);
    }
    /** Persist before network I/O; offline analysis occurs only after explicit consent. */
    public static void queueFeedback(Context context,String message,List<String> offlineAnswers,String pageContext,boolean ai,FeedbackCallback callback){
        Context app=context.getApplicationContext();
        WORKER.execute(()->{
            try{
                SharedPreferences storage=prefs(app);
                JSONArray queue=new JSONArray(storage.getString("pending_feedback","[]"));
                if(queue.length()>=50)throw new IOException("Trop de signalements en attente");
                JSONObject item=new JSONObject();item.put("id",UUID.randomUUID().toString());
                item.put("message",message);item.put("version",BuildInfo.VERSION);item.put("model",Build.MANUFACTURER+" "+Build.MODEL);
                item.put("page_context",pageContext==null?"":pageContext);item.put("ai",ai&&offlineAnswers!=null);
                if(offlineAnswers!=null)item.put("answers",new JSONArray(offlineAnswers));
                queue.put(item);
                if(!storage.edit().putString("pending_feedback",queue.toString()).commit())throw new IOException("Sauvegarde locale impossible");
                schedule(app);
                try{flushFeedback(app);}catch(Exception e){status="Signalement en attente d’Internet · conservé sur la tablette";}
                boolean sent=!containsFeedback(storage,item.getString("id"));
                callback.complete(true,sent?"Signalement reçu sur le serveur":"Signalement enregistré sur la tablette. Il sera envoyé automatiquement quand Internet reviendra.");
            }catch(Exception e){callback.complete(false,"Impossible d’enregistrer le signalement : "+e.getMessage());}
        });
    }
    private static boolean containsFeedback(SharedPreferences storage,String id)throws Exception{
        JSONArray q=new JSONArray(storage.getString("pending_feedback","[]"));
        for(int i=0;i<q.length();i++)if(id.equals(q.getJSONObject(i).optString("id")))return true;
        return false;
    }
    private static void flushFeedback(Context app)throws Exception{
        SharedPreferences storage=prefs(app);JSONArray queue=new JSONArray(storage.getString("pending_feedback","[]"));
        if(queue.length()==0)return;
        Identity identity=identity(storage);register(storage,identity);
        while(queue.length()>0){
            JSONObject item=queue.getJSONObject(0);String message=item.getString("message");
            if(item.optBoolean("ai")&&item.optJSONArray("answers")!=null){
                JSONObject triage=new JSONObject();triage.put("answers",item.getJSONArray("answers"));
                triage.put("version",item.getString("version"));triage.put("model",item.getString("model"));
                triage.put("page_context",item.optString("page_context"));triage.put("ai_opt_in",true);
                Reply analysis=post("triage",triage.toString().getBytes(StandardCharsets.UTF_8),identity,null,0,null);
                if(analysis.code!=200)throw new IOException("Analyse en attente");
                if("report".equals(analysis.body.optString("kind"))){String extra="\n\nAnalyse de l’assistant :\n"+analysis.body.optString("text");int room=14000-message.length();if(room>0)message+=extra.substring(0,Math.min(room,extra.length()));}
            }
            if(message.length()>14000)message=message.substring(0,14000);
            JSONObject body=new JSONObject();body.put("message",message);body.put("version",item.getString("version"));
            body.put("model",item.getString("model"));body.put("request_id",item.getString("id"));
            Reply result=post("feedback",body.toString().getBytes(StandardCharsets.UTF_8),identity,null,0,null);
            if(result.code==403){storage.edit().putBoolean("registered",false).commit();throw new IOException("Inscription à renouveler");}
            if(result.code!=200||!result.body.optBoolean("received"))throw new IOException("Signalement non confirmé");
            JSONArray remaining=new JSONArray();for(int i=1;i<queue.length();i++)remaining.put(queue.get(i));
            if(!storage.edit().putString("pending_feedback",remaining.toString()).commit())throw new IOException("Confirmation locale impossible");
            queue=remaining;SupportLogs.event(app,"feedback_sent",null);
        }
        status="Signalement reçu sur le serveur";
    }
    public static void triage(Context context,List<String> answers,String pageContext,boolean aiOptIn,TriageCallback callback){
        Context app=context.getApplicationContext();
        WORKER.execute(()->{
            try{
                SharedPreferences storage=prefs(app);Identity identity=identity(storage);register(storage,identity);
                JSONObject body=new JSONObject();body.put("answers",new JSONArray(answers));body.put("version",BuildInfo.VERSION);
                body.put("page_context",pageContext);
                body.put("model",Build.MANUFACTURER+" "+Build.MODEL);body.put("ai_opt_in",aiOptIn);
                Reply result=post("triage",body.toString().getBytes(StandardCharsets.UTF_8),identity,null,0,null);
                if(result.code!=200)throw new IOException("Assistance indisponible");
                String kind=result.body.optString("kind"),text=result.body.optString("text");
                if(!kind.equals("question")&&!kind.equals("report"))throw new IOException("Réponse invalide");
                callback.complete(kind,text,result.body.optBoolean("ai"));
            }catch(Exception e){callback.complete("error","Impossible de contacter le serveur. Réessaie quand la tablette a Internet.",false);}
        });
    }
    private static Identity identity(SharedPreferences storage)throws IOException{
        String id=storage.getString("installation",""),token=storage.getString("token","");
        if(id.isEmpty()||token.isEmpty()){
            id=UUID.randomUUID().toString();byte[] random=new byte[32];new SecureRandom().nextBytes(random);
            StringBuilder secret=new StringBuilder();for(byte b:random)secret.append(String.format(Locale.ROOT,"%02x",b&255));token=secret.toString();
            if(!storage.edit().putString("installation",id).putString("token",token).commit())throw new IOException("Identité locale non enregistrée");
        }
        return new Identity(id,token);
    }
    private static void register(SharedPreferences storage,Identity identity)throws Exception{
        if(storage.getBoolean("registered",false))return;
        JSONObject body=new JSONObject();body.put("installation",identity.id);body.put("token",identity.token);
        Reply result=post("register",body.toString().getBytes(StandardCharsets.UTF_8),null,null,0,null);
        if(result.code!=200||!result.body.optBoolean("registered"))throw new IOException("Inscription indisponible");
        if(!storage.edit().putBoolean("registered",true).commit())throw new IOException("Inscription locale non enregistrée");
    }
    private static void upload(Context context)throws Exception{
        SharedPreferences storage=prefs(context);Set<String> excluded=storage.getStringSet("excluded",Collections.emptySet());
        List<LogFile> pending=new ArrayList<>();for(LogFile file:files(context))if(!excluded.contains(file.name))pending.add(file);
        if(pending.isEmpty()){status="Aucun nouveau journal à envoyer";return;}
        Identity identity=identity(storage);register(storage,identity);
        long deadline=android.os.SystemClock.elapsedRealtime()+25000;int chunks=0;boolean more=false;
        for(LogFile file:pending){
            long offset=storage.getLong(file.name,0);if(offset>file.file.length())throw new IOException("Journal réduit");
            while(offset<file.file.length()){
                if(!enabled(context))return;
                if(android.os.SystemClock.elapsedRealtime()>deadline||chunks>=64){more=true;break;}
                byte[] data=BackupChunks.read(file.file,offset);if(data.length==0)break;
                status="Envoi privé des journaux en cours";String digest=BackupChunks.digest(data);
                Reply result=post("diagnostics",data,identity,file.name,offset,digest);
                if(result.code==403){storage.edit().putBoolean("registered",false).commit();throw new IOException("Accès à renouveler");}
                long next=result.body.optLong("nextOffset",-1);
                if((result.code!=200&&result.code!=409)||next<0||next>file.file.length()||(result.code==200&&next<offset+data.length)||next==offset)throw new IOException("Journal non confirmé");
                if(result.code==200&&result.body.has("sha256")&&!digest.equals(result.body.optString("sha256")))throw new IOException("Empreinte incorrecte");
                if(!storage.edit().putLong(file.name,next).putLong("confirmedAt",System.currentTimeMillis()).commit())throw new IOException("Position non enregistrée");
                offset=next;chunks++;
            }
            if(more)break;
        }
        status=more?"Envoi partiel · reprise automatique":"Journaux reçus sur le serveur";
    }
    private static Reply post(String path,byte[] data,Identity identity,String name,long offset,String digest)throws Exception{
        HttpsURLConnection connection=(HttpsURLConnection)new URL(BASE+path).openConnection();
        try{
            connection.setConnectTimeout(8000);connection.setReadTimeout(path.equals("triage")?45000:12000);connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setFixedLengthStreamingMode(data.length);
            connection.setRequestProperty("Content-Type",name==null?"application/json":"application/octet-stream");
            if(identity!=null){connection.setRequestProperty("Authorization","Bearer "+identity.token);connection.setRequestProperty("X-Installation",identity.id);connection.setRequestProperty("X-PP406-Version",BuildInfo.VERSION);}
            if(name!=null){connection.setRequestProperty("X-Log",name);connection.setRequestProperty("X-Offset",Long.toString(offset));connection.setRequestProperty("X-Chunk-SHA256",digest);}
            try(OutputStream out=connection.getOutputStream()){out.write(data);}
            int code=connection.getResponseCode();InputStream input=code<400?connection.getInputStream():connection.getErrorStream();
            if(input==null)throw new IOException("Aucune confirmation");ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try(InputStream in=input){byte[] buffer=new byte[1024];int n,maximum=path.equals("triage")?65536:8192;while((n=in.read(buffer))!=-1){bytes.write(buffer,0,n);if(bytes.size()>maximum)throw new IOException("Réponse trop longue");}}
            return new Reply(code,new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)));
        }finally{connection.disconnect();}
    }
    private static final class LogFile {final File file;final String name;LogFile(File file,String name){this.file=file;this.name=name;}}
    private static final class Identity {final String id,token;Identity(String id,String token){this.id=id;this.token=token;}}
    private static final class Reply {final int code;final JSONObject body;Reply(int code,JSONObject body){this.code=code;this.body=body;}}
    private DiagnosticBackups(){}
}
