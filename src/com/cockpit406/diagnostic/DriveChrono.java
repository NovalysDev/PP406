package com.cockpit406.diagnostic;

/** Timing from actual, timestamped ECU samples; never communicates with a vehicle. */
public final class DriveChrono {
    public enum Mode {
        ZERO_100("0–100 km/h",1,100), ZERO_200("0–200 km/h",1,200),
        ZERO_250("0–250 km/h",1,250), HUNDRED_200("100–200 km/h",100,200),
        LAP("Tour libre",1,Double.POSITIVE_INFINITY);
        public final String label;final double start,end;
        Mode(String label,double start,double end){this.label=label;this.start=start;this.end=end;}
    }
    public enum State { OFF,WAITING,RUNNING,COMPLETE,LOST }
    public static final long MAX_GAP_MS=3000;
    private Mode mode;
    private State state=State.OFF;
    private double lastValue;
    private long lastStamp=-1,start=-1,maxGap,result=-1;
    private boolean baseline;
    public void arm(Mode selected){mode=selected;state=State.WAITING;lastStamp=-1;start=-1;result=-1;maxGap=0;baseline=false;}
    public void cancel(){state=State.OFF;lastStamp=-1;baseline=false;}
    public State state(){return state;}
    public Mode mode(){return mode;}
    public long resultMs(){return result;}
    public long maxGapMs(){return maxGap;}
    public long elapsedMs(long now){return state==State.RUNNING?Math.max(0,now-start):0;}
    private static long cross(long before,double a,long after,double b,double threshold){
        if(b<=a)return after;
        double f=Math.max(0,Math.min(1,(threshold-a)/(b-a)));
        return before+Math.round((after-before)*f);
    }
    public void tick(long now,boolean connected){
        if((state==State.WAITING||state==State.RUNNING)&&(!connected||lastStamp>=0&&now-lastStamp>MAX_GAP_MS))state=State.LOST;
    }
    public void observe(Double value,long stamp,long now,boolean live){
        if(state!=State.WAITING&&state!=State.RUNNING)return;
        if(!live||value==null||!Double.isFinite(value)||value<0||stamp<0||stamp>now||now-stamp>MAX_GAP_MS){tick(now,live);return;}
        if(stamp==lastStamp)return;
        if(lastStamp>=0&&(stamp<lastStamp||stamp-lastStamp>MAX_GAP_MS)){state=State.LOST;return;}
        if(lastStamp<0){lastValue=value;lastStamp=stamp;baseline=value<mode.start;return;}
        long gap=stamp-lastStamp;
        if(state==State.WAITING){
            if(value<mode.start)baseline=true;
            if(baseline&&lastValue<mode.start&&value>=mode.start){
                start=cross(lastStamp,lastValue,stamp,value,mode.start);state=State.RUNNING;maxGap=gap;
            }
        }
        if(state==State.RUNNING){
            maxGap=Math.max(maxGap,gap);
            if(mode!=Mode.LAP&&lastValue<mode.end&&value>=mode.end){
                long end=cross(lastStamp,lastValue,stamp,value,mode.end);
                result=Math.max(0,end-start);state=State.COMPLETE;
            }
        }
        lastValue=value;lastStamp=stamp;
    }
    public long finishLap(long now){
        if(state!=State.RUNNING||mode!=Mode.LAP)return -1;
        long elapsed=Math.max(0,now-start);start=now;return elapsed;
    }
}
