package com.cockpit406.diagnostic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;

/** Smooths the drawing at screen cadence; the number remains the actual sample. */
public final class NeedleDialView extends View {
    private final NeedleDial.Instrument instrument;
    private final NeedleDial.Motion motion=new NeedleDial.Motion();
    private final ColorMotion colors=new ColorMotion();
    private final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG);
    private NeedleDial.Reading reading;
    private Double lastValue;
    private long lastSampleStamp=-1;
    private long startupAt=-1;
    private double[] startupLimits;
    private boolean startupTiers;
    private boolean fastFlash;
    private Canvas canvas;
    private Bitmap face;
    private final NeedleDial.Surface surface=new NeedleDial.Surface(){
        private void style(int color,float width,boolean fill){brush.setColor(color);brush.setStrokeWidth(width);brush.setStyle(fill?Paint.Style.FILL:Paint.Style.STROKE);brush.setStrokeCap(Paint.Cap.ROUND);}
        public void line(float x1,float y1,float x2,float y2,int color,float width){style(color,width,false);canvas.drawLine(x1,y1,x2,y2,brush);}
        public void circle(float x,float y,float radius,int color,float width,boolean fill){style(color,width,fill);canvas.drawCircle(x,y,radius,brush);}
        public void arc(float x,float y,float radius,float start,float sweep,int color,float width){style(color,width,false);canvas.drawArc(x-radius,y-radius,x+radius,y+radius,start,sweep,false,brush);}
        public void text(String value,float x,float baseline,float size,int color,boolean bold){style(color,0,true);brush.setTextSize(size);brush.setTextAlign(Paint.Align.CENTER);brush.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);canvas.drawText(value,x,baseline,brush);}
        public NeedleDial.TextMetrics measureText(String value,float size,boolean bold){brush.setTextSize(size);brush.setTypeface(bold?Typeface.DEFAULT_BOLD:Typeface.DEFAULT);Paint.FontMetrics fm=brush.getFontMetrics();return new NeedleDial.TextMetrics(brush.measureText(value),-fm.ascent,fm.descent);}
    };
    public NeedleDialView(Context context,NeedleDial.Instrument instrument){
        super(context);this.instrument=instrument;reading=NeedleDial.reading(instrument,null,false,0xfff3f6f7,1);
        setFocusable(true);setContentDescription(reading.description(instrument));
    }
    public void setReading(Double value,boolean fresh,int color,float alpha,long sampleStamp){
        NeedleDial.Reading next=NeedleDial.reading(instrument,value,fresh,color,alpha);long now=SystemClock.uptimeMillis();
        lastValue=value;lastSampleStamp=sampleStamp;
        fastFlash=instrument==NeedleDial.Instrument.RPM&&fresh&&value!=null
            &&Double.isFinite(value)&&value>=ValueAlerts.RPM_FAST_FLASH;
        motion.update(next,now,sampleStamp);colors.update(next.color,next.fresh,now);
        boolean changed=!next.number.equals(reading.number)||!next.state.equals(reading.state)||next.color!=reading.color||next.alpha!=reading.alpha;
        reading=next;String description=reading.description(instrument);if(!description.contentEquals(getContentDescription()))setContentDescription(description);
        if(changed||motion.movingAt(now)||colors.moving(now))postInvalidateOnAnimation();
    }
    public void startSelfTest(long sharedStart,double[] limits,boolean tiers){startupAt=sharedStart;startupLimits=limits.clone();startupTiers=tiers;postInvalidateOnAnimation();}
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){super.onSizeChanged(width,height,oldWidth,oldHeight);face=null;}
    private void prepareFace(){
        if(face!=null||getWidth()<=0||getHeight()<=0)return;
        Bitmap next=Bitmap.createBitmap(getWidth(),getHeight(),Bitmap.Config.ARGB_8888);
        canvas=new Canvas(next);NeedleDial.paintFace(surface,instrument,getWidth(),getHeight());canvas=null;face=next;
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);prepareFace();if(face!=null)canvas.drawBitmap(face,0,0,null);
        this.canvas=canvas;long now=SystemClock.uptimeMillis();double angle=motion.angleAt(now);
        long elapsed=now-startupAt;
        boolean selfTest=startupAt>=0&&elapsed<NeedleDial.STARTUP_DURATION_MS;
        if(selfTest)angle=NeedleDial.startupAngle(elapsed,angle);
        boolean expired=reading.fresh&&lastSampleStamp>0&&now-lastSampleStamp>=3000;
        boolean flash=fastFlash&&!expired;
        NeedleDial.Reading shown=expired?NeedleDial.reading(instrument,lastValue,false,0xffa0b1bc,1)
            :flash?reading.withColor(ValueAssessment.RED).withAlpha((now/NeedleDial.FAST_FLASH_HALF_MS)%2==0?1f:.23f)
            :reading.withColor(colors.at(now));
        int needleColor=shown.color;float needleAlpha=shown.alpha;
        if(selfTest){
            needleColor=NeedleDial.startupColor(instrument,angle,startupLimits,startupTiers);
            needleAlpha=NeedleDial.startupAlpha(instrument,angle,now);
        }
        NeedleDial.paintReading(surface,instrument,shown,getWidth(),getHeight(),angle,needleColor,needleAlpha);this.canvas=null;
        if(flash)postInvalidateDelayed(NeedleDial.FAST_FLASH_HALF_MS);
        else if(reading.fresh&&lastSampleStamp>0&&!expired)postInvalidateDelayed(Math.max(1,3000-(now-lastSampleStamp)));
        if(selfTest||motion.movingAt(now)||colors.moving(now))postInvalidateOnAnimation();
    }
    @Override protected void onDetachedFromWindow(){motion.detach();face=null;super.onDetachedFromWindow();}
}
