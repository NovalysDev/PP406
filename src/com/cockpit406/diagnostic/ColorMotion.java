package com.cockpit406.diagnostic;
/** Presentation-only ARGB transition; repeated targets do not restart it. */
public final class ColorMotion {
    public static final long DURATION=280;
    private int from,target;private long start;private boolean initialized;
    public void update(int color,boolean animate,long now){
        if(!initialized||!animate){from=target=color;start=now-DURATION;initialized=true;}
        else if(target!=color){from=at(now);target=color;start=now;}
    }
    public boolean moving(long now){return initialized&&from!=target&&now-start<DURATION;}
    public int at(long now){
        double p=Math.max(0,Math.min(1,(now-start)/(double)DURATION));p=p*p*(3-2*p);int result=0;
        for(int shift=0;shift<=24;shift+=8){int a=(from>>>shift)&255,b=(target>>>shift)&255;result|=((int)Math.round(a+(b-a)*p))<<shift;}
        return result;
    }
}
