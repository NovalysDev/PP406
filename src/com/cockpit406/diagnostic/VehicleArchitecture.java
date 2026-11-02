package com.cockpit406.diagnostic;

import java.util.Locale;

/** Explicit user selection; a VIN alone is not a multiplexing classification. */
public final class VehicleArchitecture {
    public static boolean ready(String vin,String kind){
        String value=vin==null?"":vin.replaceAll("\\s","").toUpperCase(Locale.ROOT);
        return value.matches("[A-HJ-NPR-Z0-9]{17}")&&("nonmux".equals(kind)||"semi".equals(kind)||"full".equals(kind)||"unknown".equals(kind));
    }
    public static String plan(String current,String kind){
        if("nonmux".equals(kind))return "Peugeot:30";
        if(("semi".equals(kind)||"full".equals(kind))&&("auto".equals(current)||"Peugeot:30".equals(current)))return "family:406";
        return current;
    }
    /** Restore only the newly selected vehicle's plan, never the preceding vehicle's. */
    public static String restoredPlan(String remembered,String kind){
        String base=remembered==null||remembered.isEmpty()?"auto":remembered;
        return plan(base,kind);
    }
    private VehicleArchitecture(){}
}
