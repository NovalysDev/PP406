package com.cockpit406.diagnostic;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Small stopwatch matching the lime accents of the PP406 instruments. */
public final class ChronoIcon extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int lime=Color.rgb(215,245,45);
    public ChronoIcon(Context context){super(context);setFocusable(true);setContentDescription("Choisir un chronomètre");}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);float s=Math.min(getWidth(),getHeight()),cx=getWidth()/2f,cy=getHeight()*.55f,r=s*.29f;
        p.setStyle(Paint.Style.FILL);p.setColor(0xff101a20);canvas.drawCircle(cx,cy,r*1.27f,p);
        p.setColor(lime);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(Math.max(2,s*.045f));p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawCircle(cx,cy,r,p);
        canvas.drawLine(cx,cy-r*1.45f,cx,cy-r*1.08f,p);
        canvas.drawLine(cx-r*.24f,cy-r*1.45f,cx+r*.24f,cy-r*1.45f,p);
        canvas.drawLine(cx+r*.68f,cy-r*.89f,cx+r*.89f,cy-r*1.10f,p);
        canvas.drawLine(cx,cy,cx,cy-r*.57f,p);
        canvas.drawLine(cx,cy,cx+r*.40f,cy+r*.23f,p);
        p.setStyle(Paint.Style.FILL);canvas.drawCircle(cx,cy,Math.max(2,s*.04f),p);
    }
}
