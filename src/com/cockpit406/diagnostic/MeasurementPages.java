package com.cockpit406.diagnostic;

/** Primary MP7.0 measurements on one fixed landscape grid. */
public final class MeasurementPages {
    public static final int COLUMNS=4, ROWS=4;
    private static final String[] KEYS={
        "rpm","speed_kmh","battery_v","coolant_r_c",
        "map_mbar","throttle_deg","injection_ms","load_ms",
        "air_r_c","outside_c","throttle_mv","oxygen_mv",
        "idle_duty_pct","canister_duty_pct"
    };
    public static final int NEEDLE_STRIP_HEIGHT_DP=112;
    private static final String[] NEEDLE_KEYS={"coolant_r_c","oxygen_mv","outside_c","air_r_c"};
    public static String[] keys(){return KEYS.clone();}
    public static String[] needleKeys(){return NEEDLE_KEYS.clone();}
    public static boolean secondary(String key){return key.equals("air_s_c")||key.equals("coolant_s_c");}
    public static String[][] displayedSignals(String[][] signals){
        java.util.List<String[]> displayed=new java.util.ArrayList<>();
        for(String[] signal:signals)if(!secondary(signal[0]))displayed.add(signal);
        return displayed.toArray(new String[0][]);
    }
    public static int[] groups(){return new int[]{2,0};}
    private MeasurementPages(){}
}
