package com.cockpit406.diagnostic;

import java.util.Locale;

/** Drawing only. The numeric reading is never interpolated or written back to a sample. */
public final class NeedleDial {
    public static final double RPM_RED_ZONE_START=6400;
    public static final long FAST_FLASH_HALF_MS=80;
    public static final long STARTUP_HALF_MS=1400, STARTUP_HOLD_MS=2000, STARTUP_SETTLE_MS=400;
    public static final long STARTUP_DURATION_MS=2*STARTUP_HALF_MS+STARTUP_HOLD_MS+STARTUP_SETTLE_MS;
    public enum Instrument {
        RPM("Régime moteur", "tr/min", 8000, 1000, 250),
        SPEED("Vitesse véhicule", "km/h", 270, 20, 10);
        public final String title, unit;
        public final double maximum, majorStep, minorStep;
        Instrument(String title,String unit,double maximum,double majorStep,double minorStep){
            this.title=title;this.unit=unit;this.maximum=maximum;this.majorStep=majorStep;this.minorStep=minorStep;
        }
    }
    public static final class Reading {
        public final boolean available, fresh;
        public final double angle;
        public final String number, state;
        public final int color;
        public final float alpha;
        Reading(Instrument instrument,Double value,boolean live,int color,float alpha){
            available=value!=null&&!value.isNaN()&&!value.isInfinite()&&value>=0;
            fresh=available&&live;
            angle=available?angle(instrument,value):Double.NaN;
            number=available?String.format(Locale.FRANCE,"%.0f",value):"—";
            state=!available?"Aucune mesure":!fresh?"Ancienne mesure":value>instrument.maximum?"En direct · hors échelle":"En direct";
            this.color=fresh?color:0xffa0b1bc;
            this.alpha=fresh?Math.max(.2f,Math.min(1f,alpha)):available?.55f:1f;
        }
        public String description(Instrument instrument){return instrument.title+" : "+number+" "+instrument.unit+" · "+state+". Toucher pour l’aide.";}
        private Reading(Reading original,int color,float alpha){available=original.available;fresh=original.fresh;angle=original.angle;number=original.number;state=original.state;this.alpha=alpha;this.color=color;}
        public Reading withColor(int color){return new Reading(this,color,alpha);}
        public Reading withAlpha(float alpha){return new Reading(this,color,alpha);}
    }
    public static final class TextMetrics {
        public final float width,ascent,descent;
        public TextMetrics(float width,float ascent,float descent){this.width=width;this.ascent=ascent;this.descent=descent;}
        float height(){return ascent+descent;}
    }
    public interface Surface {
        void line(float x1,float y1,float x2,float y2,int color,float width);
        void circle(float x,float y,float radius,int color,float width,boolean fill);
        void arc(float x,float y,float radius,float start,float sweep,int color,float width);
        void text(String value,float x,float baseline,float size,int color,boolean bold);
        default TextMetrics measureText(String value,float size,boolean bold){return new TextMetrics(value.length()*size*.7f,size,size*.3f);}
    }
    /** Presentation clock only. A stopped or missing sample ends animation immediately. */
    public static final class Motion {
        public static final long DURATION_MS=160;
        private double from=135,target=135;
        private long started;
        private long duration=DURATION_MS,lastSample=-1;
        private double cadence=300;
        private double startVelocity;
        private boolean moving,wasFresh;
        public double angleAt(long now){
            if(!moving)return target;
            double p=Math.max(0,Math.min(1,(now-started)/(double)duration));
            return from+(target-from)*p*p*(3-2*p)+startVelocity*duration*p*(1-p)*(1-p);
        }
        public double velocityAt(long now){
            if(!moving||now<=started||now>=started+duration)return now==started?startVelocity:0;
            double p=(now-started)/(double)duration;
            return (target-from)*6*p*(1-p)/duration+startVelocity*(1-4*p+3*p*p);
        }
        public boolean movingAt(long now){return moving&&now-started<duration;}
        public void update(Reading next,long now,long sampleStamp){
            // A new timestamp can contain an unchanged value. It still measures the real cadence.
            long nextDuration=duration;
            if(next.fresh&&sampleStamp>=0&&sampleStamp!=lastSample){
                long interval=sampleStamp-lastSample;
                if(lastSample>=0&&interval>0&&interval<3000)cadence=.4*cadence+.6*interval;
                nextDuration=Math.max(100,Math.min(850,Math.round(cadence)));lastSample=sampleStamp;
            }
            double current=angleAt(now),velocity=velocityAt(now);boolean retarget=next.available&&next.fresh&&wasFresh&&next.angle!=target;
            update(next,now);
            if(retarget){
                from=current;duration=nextDuration;
                double delta=target-from;
                // Keep momentum on continuing movement, bounded to avoid invented overshoot.
                startVelocity=delta*velocity>0?Math.copySign(Math.min(Math.abs(velocity),3*Math.abs(delta)/duration),delta):0;
            }
            if(!next.fresh)lastSample=-1;
        }
        public void update(Reading next,long now){
            if(next.available){
                if(next.fresh&&wasFresh&&next.angle!=target){from=angleAt(now);target=next.angle;started=now;startVelocity=0;moving=true;}
                else if(!next.fresh||!wasFresh){from=target=next.angle;moving=false;startVelocity=0;}
            }else moving=false;
            wasFresh=next.fresh;
        }
        public void detach(){moving=false;wasFresh=false;lastSample=-1;}
    }
    private NeedleDial(){}
    public static Reading reading(Instrument instrument,Double value,boolean fresh,int color,float alpha){return new Reading(instrument,value,fresh,color,alpha);}
    public static double angle(Instrument instrument,double value){return 135+270*Math.max(0,Math.min(instrument.maximum,value))/instrument.maximum;}
    public static double easedAngle(double start,double target,double progress){
        double p=Math.max(0,Math.min(1,progress));return start+(target-start)*p*p*(3-2*p);
    }
    /** Visual self-test only: the measured value and the diagnostic data never change. */
    public static double startupAngle(long elapsed,double actualAngle){
        if(elapsed<0)return 135;
        if(elapsed<STARTUP_HALF_MS)return easedAngle(135,405,elapsed/(double)STARTUP_HALF_MS);
        if(elapsed<STARTUP_HALF_MS+STARTUP_HOLD_MS)return 405;
        if(elapsed<2*STARTUP_HALF_MS+STARTUP_HOLD_MS)return easedAngle(405,135,(elapsed-STARTUP_HALF_MS-STARTUP_HOLD_MS)/(double)STARTUP_HALF_MS);
        if(elapsed<STARTUP_DURATION_MS)return easedAngle(135,actualAngle,(elapsed-2*STARTUP_HALF_MS-STARTUP_HOLD_MS)/(double)STARTUP_SETTLE_MS);
        return actualAngle;
    }
    /** Colour corresponds only to the position drawn during the visual test. */
    public static int startupColor(Instrument instrument,double angle,double[] limits,boolean enabled){
        double position=Math.max(0,Math.min(1,(angle-135)/270));
        double value=position*instrument.maximum;
        if(instrument==Instrument.RPM&&value>=6000)return 0xffff5858;
        if(!enabled)return 0xfff3f6f7;
        return value>=limits[2]?0xffff5858:value>=limits[1]?0xffff9b40:value>=limits[0]?0xffffe45c:0xff50dc90;
    }
    public static float startupAlpha(Instrument instrument,double angle,long now){
        double value=(angle-135)/270*instrument.maximum;
        return instrument==Instrument.RPM&&value>=6400&&(now/FAST_FLASH_HALF_MS)%2!=0?.23f:1f;
    }
    private static float x(float cx,float radius,double angle){return cx+radius*(float)Math.cos(Math.toRadians(angle));}
    private static float y(float cy,float radius,double angle){return cy+radius*(float)Math.sin(Math.toRadians(angle));}
    private static int fade(int color,float alpha){return (Math.round(((color>>>24)&255)*alpha)<<24)|(color&0xffffff);}
    public static boolean majorTick(Instrument instrument,double tick){return instrument==Instrument.SPEED?tick>=30&&tick<=250&&(tick-30)%20==0:tick%instrument.majorStep==0;}
    public static String tickLabel(Instrument instrument,double tick){
        if(!majorTick(instrument,tick))return "";
        return Integer.toString((int)(instrument==Instrument.RPM?tick/1000:tick));
    }
    private static final class ScaleLabel {
        final String text;final float x,y;TextMetrics metrics;
        ScaleLabel(String text,float x,float y){this.text=text;this.x=x;this.y=y;}
    }
    private static boolean scaleLabelsFit(ScaleLabel[] labels,float limit,float gap){
        for(int i=0;i<labels.length;i++){
            ScaleLabel a=labels[i];
            float cornerX=Math.abs(a.x)+a.metrics.width/2,cornerY=Math.abs(a.y)+a.metrics.height()/2;
            if(Math.hypot(cornerX,cornerY)>limit)return false;
            for(int j=0;j<i;j++){
                ScaleLabel b=labels[j];
                if(Math.abs(a.x-b.x)<(a.metrics.width+b.metrics.width)/2+gap&&Math.abs(a.y-b.y)<(a.metrics.height()+b.metrics.height())/2+gap)return false;
            }
        }
        return true;
    }
    private static void paintSpeedLabels(Surface out,float cx,float cy,float radius,float height,int color){
        ScaleLabel[] labels=new ScaleLabel[12];
        for(int i=0;i<labels.length;i++){
            int tick=30+i*20;double a=angle(Instrument.SPEED,tick);
            labels[i]=new ScaleLabel(Integer.toString(tick),x(0,radius*.72f,a),y(0,radius*.72f,a));
        }
        float size=radius*.115f,gap=Math.max(1,radius*.02f);
        float innerLimit=radius*.86f-Math.max(1,height*.007f)/2-gap;
        // Native font metrics are evaluated once with the cached face, never per animation frame.
        for(int attempt=0;attempt<64;attempt++){
            for(ScaleLabel label:labels)label.metrics=out.measureText(label.text,size,false);
            if(scaleLabelsFit(labels,innerLimit,gap))break;
            if(attempt<63)size*=.95f;
        }
        for(ScaleLabel label:labels)out.text(label.text,cx+label.x,cy+label.y+(label.metrics.ascent-label.metrics.descent)/2,size,color,false);
    }
    /** The scale is a display choice, not a manufacturer fault limit or a speed limit. */
    public static void paint(Surface out,Instrument instrument,Reading reading,float width,float height,double needleAngle){
        paintFace(out,instrument,width,height);
        paintReading(out,instrument,reading,width,height,needleAngle);
    }
    /** Static face can be cached by the Android view instead of repainting labels at each frame. */
    public static void paintFace(Surface out,Instrument instrument,float width,float height){
        if(width<=0||height<=0)return;
        float cx=width/2,cy=height*.49f,r=Math.min(width*.43f,height*.36f);
        int muted=0xffa0b1bc,white=0xfff3f6f7;
        out.text(instrument.title,cx,height*.085f,Math.max(10,height*.069f),white,true);
        out.circle(cx,cy,r*1.04f,0xff111a22,0,true);
        out.arc(cx,cy,r,135,270,0xff34444f,Math.max(2,height*.012f));
        out.arc(cx,cy,r*1.06f,135,270,0xff416170,Math.max(1,height*.004f));
        if(instrument==Instrument.RPM){
            float start=(float)angle(instrument,RPM_RED_ZONE_START);
            out.arc(cx,cy,r,start,(float)angle(instrument,instrument.maximum)-start,0xffff5858,Math.max(3,height*.018f));
        }
        for(double tick=0;tick<=instrument.maximum;tick+=instrument.minorStep){
            boolean major=majorTick(instrument,tick);
            boolean redZone=instrument==Instrument.RPM&&tick>=RPM_RED_ZONE_START;
            double a=angle(instrument,tick);float inside=r*(major?.86f:.93f);
            out.line(x(cx,inside,a),y(cy,inside,a),x(cx,r,a),y(cy,r,a),redZone?0xffff5858:major?white:muted,Math.max(1,height*(major?.007f:.0035f)));
            String label=tickLabel(instrument,tick);
            if(!label.isEmpty()&&instrument!=Instrument.SPEED)out.text(label,x(cx,r*.79f,a),y(cy,r*.79f,a)+height*.021f,Math.max(8,height*.061f),redZone?0xffff5858:muted,false);
        }
        if(instrument==Instrument.SPEED)paintSpeedLabels(out,cx,cy,r,height,muted);
        if(instrument==Instrument.RPM)out.text("× 1000",cx,cy-r*.32f,Math.max(8,height*.046f),muted,false);
    }
    public static void paintReading(Surface out,Instrument instrument,Reading reading,float width,float height,double needleAngle){
        paintReading(out,instrument,reading,width,height,needleAngle,reading.color,reading.alpha);
    }
    public static void paintReading(Surface out,Instrument instrument,Reading reading,float width,float height,double needleAngle,int needleColor,float needleAlpha){
        if(width<=0||height<=0)return;
        float cx=width/2,cy=height*.49f,r=Math.min(width*.43f,height*.36f);
        int muted=0xffa0b1bc,white=0xfff3f6f7;
        // A resting needle is decorative when there is no measurement: the number stays "—".
        if(Double.isNaN(needleAngle)||Double.isInfinite(needleAngle))needleAngle=135;
        int needle=fade(needleColor,needleAlpha);
        out.line(x(cx,-r*.13f,needleAngle),y(cy,-r*.13f,needleAngle),x(cx,r*.84f,needleAngle),y(cy,r*.84f,needleAngle),needle,Math.max(2,height*.013f));
        out.circle(cx,cy,r*.075f,needle,0,true);
        out.circle(cx,cy,r*.026f,white,0,true);
        out.text(reading.number,cx,height*.825f,Math.max(16,height*.15f),fade(reading.color,reading.alpha),true);
        out.text(instrument.unit,cx,height*.895f,Math.max(9,height*.053f),muted,false);
        out.text(reading.state+" · toucher pour l’aide",cx,height*.978f,Math.max(8,height*.048f),muted,false);
    }
}
