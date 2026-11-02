package com.cockpit406.diagnostic;

import android.content.Context;
import java.io.*;

/** Owner-enabled root installation of an already verified, same-signer APK only. */
final class RootUpdateInstaller {
    static String quote(String text){return "'"+text.replace("'","'\\''")+"'";}
    static String root(String command,int seconds)throws Exception{
        Process process=new ProcessBuilder("su","-c",command).redirectErrorStream(true).start();
        // Timed Process.waitFor() and destroyForcibly() are absent on Android 7.0.
        long deadline=android.os.SystemClock.elapsedRealtime()+seconds*1000L;
        while(true){
            try{process.exitValue();break;}catch(IllegalThreadStateException busy){
                if(android.os.SystemClock.elapsedRealtime()>=deadline){process.destroy();throw new IOException("Autorisation root sans réponse");}
                Thread.sleep(100);
            }
        }
        String output;try(InputStream in=process.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[2048];int count;while((count=in.read(buffer))!=-1){if(out.size()+count>16384)throw new IOException("Réponse root trop volumineuse");out.write(buffer,0,count);}output=out.toString("UTF-8").trim();
        }
        if(process.exitValue()!=0)throw new IOException("Accès root refusé ou commande indisponible");return output;
    }
    static void authorize()throws Exception{if(!root("id -u",25).equals("0"))throw new IOException("Accès root non accordé à PP406");}
    static File schedule(Context context,AppUpdates.Release release,File apk,boolean reopen)throws Exception{
        // Revalidate immediately before copying. The caller holds the diagnostic lease.
        AppUpdates.verifyArchive(context,apk,release);
        int current=context.getPackageManager().getPackageInfo(AppUpdates.PACKAGE,0).versionCode;
        if(release.code<=current)throw new IOException("Version déjà installée ou antérieure");
        File directory=new File(context.getFilesDir(),"app-updates");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Stockage de mise à jour inaccessible");
        File result=new File(directory,"install-result.txt"),script=new File(directory,"install.sh"),log=new File(directory,"install.log");
        try(FileOutputStream out=new FileOutputStream(result)){out.write("pending".getBytes("UTF-8"));}
        String stage="/data/local/tmp/pp406-"+release.code+"-"+release.sha.substring(0,16)+".apk";
        String body="#!/system/bin/sh\nsleep 2\n/system/bin/pm install -r "+quote(stage)+" > "+quote(log.getPath())+" 2>&1\nrc=$?\necho $rc > "+quote(result.getPath())+"\nrm -f "+quote(stage)+"\n";
        if(reopen)body+="if [ \"$rc\" = 0 ]; then /system/bin/am start -n com.cockpit406.diagnostic/.MainActivity >/dev/null 2>&1; fi\n";
        try(FileOutputStream out=new FileOutputStream(script)){out.write(body.getBytes("UTF-8"));}
        // Detach from the application process, which Android terminates while replacing its APK.
        String command="cp "+quote(apk.getPath())+" "+quote(stage)+" && chmod 0644 "+quote(stage)+" && (setsid /system/bin/sh "+quote(script.getPath())+" </dev/null >/dev/null 2>&1 &)";
        root(command,10);return result;
    }
    private RootUpdateInstaller(){}
}
