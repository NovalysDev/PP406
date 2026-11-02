package com.cockpit406.diagnostic;

/** Presentation calibration from the owner's simultaneous PP406 / dashboard readings.
 * The MP7.0 decoded sample, GPS comparison, recordings and diagnostic guards stay raw.
 */
public final class SpeedDisplayCalibration {
    private static final double[] PP406={0,27,37,47,57,67,76,84,93,102,112};
    private static final double[] DASH={0,30,40,50,60,70,80,90,100,110,120};

    public static Double display(Double decodedKmh) {
        if(decodedKmh==null)return null;
        double value=decodedKmh;
        if(!Double.isFinite(value)||value<0)return Double.NaN;
        if(value==0)return 0d;
        for(int i=1;i<PP406.length;i++) {
            if(value<=PP406[i]) {
                double fraction=(value-PP406[i-1])/(PP406[i]-PP406[i-1]);
                return DASH[i-1]+fraction*(DASH[i]-DASH[i-1]);
            }
        }
        // Beyond the last observed point, keep the final measured offset.
        return value+(DASH[DASH.length-1]-PP406[PP406.length-1]);
    }

    /** Fine correction measured on the owner's Asus, applied only to the visible dial. */
    public static Double ownerFineCorrection(Double shown) {
        if(shown==null || !Double.isFinite(shown) || shown<=30 || shown>=80)return shown;
        if(shown<49)return shown+(shown-30)/19.0;
        if(shown<=69)return shown+1.0;
        return shown+(80-shown)/11.0;
    }

    private SpeedDisplayCalibration() {}
}
