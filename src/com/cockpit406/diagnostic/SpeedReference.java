package com.cockpit406.diagnostic;

/** Optional GPS comparison. It never calibrates or substitutes an ECU measurement. */
public final class SpeedReference {
    public static final class Fix {
        public final double kmh,accuracyMeters,speedAccuracyMps;
        public final long elapsedMs;
        public final boolean hasSpeed,hasAccuracy,gps,mock;
        public Fix(double kmh,double accuracyMeters,double speedAccuracyMps,long elapsedMs,boolean hasSpeed,boolean hasAccuracy,boolean gps,boolean mock){
            this.kmh=kmh;this.accuracyMeters=accuracyMeters;this.speedAccuracyMps=speedAccuracyMps;this.elapsedMs=elapsedMs;
            this.hasSpeed=hasSpeed;this.hasAccuracy=hasAccuracy;this.gps=gps;this.mock=mock;
        }
        public boolean usable(long now){
            return gps&&!mock&&hasSpeed&&hasAccuracy&&elapsedMs>0&&now>=elapsedMs&&now-elapsedMs<=2000
                &&ValueAlerts.finite(kmh)&&kmh>=0&&kmh<=400&&ValueAlerts.finite(accuracyMeters)&&accuracyMeters>=0&&accuracyMeters<=25
                &&(Double.isNaN(speedAccuracyMps)||(ValueAlerts.finite(speedAccuracyMps)&&speedAccuracyMps>=0&&speedAccuracyMps<=2));
        }
    }
    private SpeedReference(){}
}
