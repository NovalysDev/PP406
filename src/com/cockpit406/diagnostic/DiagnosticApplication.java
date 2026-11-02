package com.cockpit406.diagnostic;

import android.app.Application;

public final class DiagnosticApplication extends Application {
    @Override public void onCreate(){
        super.onCreate();
        SupportLogs.event(this,"start",null);
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            SupportLogs.event(this,"crash",error);
            if(previous!=null)previous.uncaughtException(thread,error);
            else android.os.Process.killProcess(android.os.Process.myPid());
        });
    }
}
