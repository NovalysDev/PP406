package com.cockpit406.diagnostic;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen, in-activity battery warning. The diagnostic activity keeps running underneath. */
final class BatteryOverlay extends FrameLayout {
    private static final int AMBER=Color.rgb(255,191,86),WHITE=Color.rgb(243,246,247);
    private final TextView percentText;
    private final Gauge gauge;

    BatteryOverlay(Activity activity){
        super(activity);
        setVisibility(GONE);
        setClickable(true);
        setBackgroundColor(Color.argb(237,7,12,18));
        setOnClickListener(v->hide());

        LinearLayout card=new LinearLayout(activity);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20),dp(14),dp(24),dp(14));
        GradientDrawable surround=new GradientDrawable();
        surround.setColor(Color.rgb(17,25,33));
        surround.setCornerRadius(dp(20));
        surround.setStroke(dp(2),AMBER);
        card.setBackground(surround);
        FrameLayout.LayoutParams centered=new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER);
        centered.setMargins(dp(20),dp(12),dp(20),dp(12));
        addView(card,centered);

        ImageView coupe=new ImageView(activity);
        coupe.setImageResource(R.drawable.coupe406_battery);
        coupe.setContentDescription("Peugeot 406 Coupé rouge");
        coupe.setScaleType(ImageView.ScaleType.FIT_CENTER);
        card.addView(coupe,new LinearLayout.LayoutParams(dp(220),dp(220)));

        LinearLayout details=new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams detailLayout=new LinearLayout.LayoutParams(dp(230),-2);
        detailLayout.setMargins(dp(16),0,0,0);
        card.addView(details,detailLayout);

        TextView title=label("406 Coupé",32,WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD_ITALIC));
        details.addView(title);
        TextView caption=label("BATTERIE TABLETTE",15,AMBER);
        caption.setLetterSpacing(0.12f);
        details.addView(caption);

        gauge=new Gauge(activity);
        LinearLayout.LayoutParams meterLayout=new LinearLayout.LayoutParams(dp(180),dp(72));
        meterLayout.setMargins(0,dp(11),0,0);
        details.addView(gauge,meterLayout);
        percentText=label("20 %",52,AMBER);
        percentText.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        details.addView(percentText);
        details.addView(label("Branche la tablette",16,WHITE));
        TextView timeout=label("Fermeture automatique dans 10 s",12,Color.rgb(160,177,188));
        LinearLayout.LayoutParams timeoutLayout=new LinearLayout.LayoutParams(-2,-2);
        timeoutLayout.setMargins(0,dp(10),0,0);
        details.addView(timeout,timeoutLayout);
    }

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextView label(String value,int size,int color){
        TextView text=new TextView(getContext());text.setText(value);text.setTextSize(size);text.setTextColor(color);
        text.setGravity(Gravity.CENTER);return text;
    }
    void show(int percent){
        gauge.setPercent(percent);
        percentText.setText(percent+" %");
        setAlpha(1f);
        setVisibility(VISIBLE);
    }
    void hide(){setVisibility(GONE);}

    private static final class Gauge extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect=new RectF();
        private int percent=20;
        Gauge(Activity activity){super(activity);}
        void setPercent(int value){percent=Math.max(0,Math.min(100,value));invalidate();}
        private float dp(float value){return value*getResources().getDisplayMetrics().density;}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float left=dp(4),top=dp(12),right=getWidth()-dp(19),bottom=getHeight()-dp(12),radius=dp(10);
            paint.setColor(AMBER);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(4));
            rect.set(left,top,right,bottom);canvas.drawRoundRect(rect,radius,radius,paint);
            paint.setStyle(Paint.Style.FILL);
            rect.set(right+dp(4),top+dp(13),getWidth()-dp(3),bottom-dp(13));canvas.drawRoundRect(rect,dp(3),dp(3),paint);
            float innerLeft=left+dp(8),innerRight=right-dp(8);
            float filled=innerLeft+(innerRight-innerLeft)*percent/100f;
            if(filled>innerLeft){rect.set(innerLeft,top+dp(8),filled,bottom-dp(8));canvas.drawRoundRect(rect,dp(5),dp(5),paint);}
        }
    }
}
