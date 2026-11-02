package com.cockpit406.diagnostic;

/** Display alerts, distinct from manufacturer fault thresholds. No diagnostic IO. */
public final class ValueAlerts {
    public enum Level { NEUTRAL, NORMAL, YELLOW, AMBER, RED }
    public static final double DEFAULT_RPM_HIGH = 6000;
    public static final double DEFAULT_RPM_RED=6000, RPM_FAST_FLASH=6400;
    public static boolean upgradeDefaultRpmRed(double yellow,double orange,double red){
        return yellow==4000&&orange==5000&&(red==5500||red==6000||red==7000);
    }
    public static Level evaluate(Double value, boolean fresh, Double high) {
        if (!fresh || value == null) return Level.NEUTRAL;
        if (value.isNaN() || value.isInfinite()) return Level.RED;
        if (high == null || !validLimit(high)) return Level.NEUTRAL;
        if (value >= high) return Level.RED;
        if (value >= high * .9) return Level.AMBER;
        return Level.NORMAL;
    }
    public static boolean validLimit(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0;
    }
    public static boolean finite(Double value){return value!=null&&!value.isNaN()&&!value.isInfinite();}
    public static boolean validRange(Double low,Double high){
        return (low==null||finite(low))&&(high==null||finite(high))&&(low==null||high==null||low<=high);
    }
    public static float alpha(Level level, boolean fresh, long now) {
        if (!fresh) return .40f;
        return level == Level.RED && (now / 350) % 2 == 1 ? .48f : 1f;
    }
    private ValueAlerts() {}
}
