package com.cockpit406.diagnostic;

import android.content.Context;
import android.os.Build;
import android.os.StatFs;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Small, private app/crash journal. Never blocks a diagnostic operation. */
public final class SupportLogs {
    private static File current;
    private static File directory(Context context){return new File(context.getFilesDir(),"sessions");}
    public static synchronized void rotate(){current=null;}
    public static synchronized void event(Context context,String event,Throwable failure){
        try{
            File dir=directory(context);if(!dir.isDirectory()&&!dir.mkdirs())return;
            if(current==null){
                SimpleDateFormat stamp=new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'",Locale.ROOT);
                stamp.setTimeZone(TimeZone.getTimeZone("UTC"));
                current=File.createTempFile("app-events-"+stamp.format(new Date())+"-",".jsonl",dir);
            }
            JSONObject data=new JSONObject();data.put("event",event);data.put("time_ms",System.currentTimeMillis());
            data.put("app",BuildInfo.VERSION);data.put("android_api",Build.VERSION.SDK_INT);
            data.put("manufacturer",Build.MANUFACTURER);data.put("model",Build.MODEL);
            if("start".equals(event)){
                StatFs storage=new StatFs(context.getFilesDir().getPath());
                data.put("storage_available_bytes",storage.getAvailableBytes());
                data.put("storage_total_bytes",storage.getTotalBytes());
            }
            if(failure!=null){
                data.put("exception",failure.getClass().getName());
                StringBuilder frames=new StringBuilder();
                for(StackTraceElement frame:failure.getStackTrace()){
                    if(frames.length()>12000)break;frames.append(frame.toString()).append('\n');
                }
                data.put("stack",frames.toString());
            }
            byte[] line=(data.toString()+"\n").getBytes(StandardCharsets.UTF_8);
            try(FileOutputStream output=new FileOutputStream(current,true)){output.write(line);output.flush();}
        }catch(Exception ignored){}
    }
    private SupportLogs(){}
}
