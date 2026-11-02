package com.cockpit406.diagnostic;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** An inactive reading has an absolute timestamp, never a running timer. */
public final class ReadingTime {
    public static String label(long wallTime,long measuredElapsed,long nowElapsed,boolean acquiring) {
        String clock=new SimpleDateFormat("HH:mm:ss",Locale.FRANCE).format(new Date(wallTime));
        if(!acquiring)return "LECTURE ARRÊTÉE · dernier relevé à "+clock;
        double seconds=Math.max(0,nowElapsed-measuredElapsed)/1000.0;
        return (seconds<3?"DERNIÈRE RÉPONSE":"VALEURS ANCIENNES")+" · à "+clock+" · il y a "+String.format(Locale.FRANCE,"%.1f",seconds)+" s";
    }
    public static String clock(long wallTime) {
        return new SimpleDateFormat("HH:mm:ss",Locale.FRANCE).format(new Date(wallTime));
    }
    private ReadingTime() {}
}
