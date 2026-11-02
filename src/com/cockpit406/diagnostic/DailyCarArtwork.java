package com.cockpit406.diagnostic;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import org.json.JSONArray;
import org.json.JSONObject;

/** Recolors only the photographed red bodywork, leaving Lexia, glass and lights intact. */
final class DailyCarArtwork {
    static final class Result {
        final Bitmap image;
        final String label;
        Result(Bitmap image,String label){this.image=image;this.label=label;}
    }

    static Result forSignedInDaily(Context context){
        try{
            JSONArray vehicles=ClubBridge.readGarage(context);
            JSONObject daily=null;
            for(int i=0;i<vehicles.length();i++){
                JSONObject car=vehicles.optJSONObject(i);
                if(car!=null&&"Daily".equals(car.optString("usage"))){daily=car;break;}
            }
            if(daily==null&&vehicles.length()==1)daily=vehicles.optJSONObject(0);
            if(daily!=null){
                String hex=daily.optString("paintHex","");
                if(hex.matches("#[0-9a-fA-F]{6}")){
                    Bitmap original=BitmapFactory.decodeResource(context.getResources(),R.drawable.pp406_start_realistic);
                    if(original!=null){
                        int color=Color.parseColor(hex);
                        String label=daily.optString("color","");
                        return new Result(recolor(original,color),label);
                    }
                }
            }
        }catch(Exception ignored){/* Club absent, signed out or older catalogue: neutral car. */}
        return new Result(BitmapFactory.decodeResource(context.getResources(),R.drawable.pp406_start_neutral),"");
    }

    static Bitmap recolor(Bitmap original,int color){
        if(color==Color.rgb(225,38,35))return original;
        int width=original.getWidth(),height=original.getHeight();
        int[] pixels=new int[width*height];
        original.getPixels(pixels,0,width,0,0,width,height);
        int tr=Color.red(color),tg=Color.green(color),tb=Color.blue(color);
        int peak=Math.max(tr,Math.max(tg,tb));
        if(peak==0)return original;
        double scale=Math.sqrt(peak/225.0);
        for(int y=0;y<height;y++){
            double fy=(double)y/height;
            if(fy<.164||fy>=.76)continue;
            for(int x=0;x<width;x++){
                double fx=(double)x/width;
                if(fy<.287&&(fx<=.136||fx>=.845))continue;
                if(fy>=.287&&fy<.367&&(fx<=.028||fx>=.973))continue;
                int pos=y*width+x,pixel=pixels[pos];
                int red=Color.red(pixel),green=Color.green(pixel),blue=Color.blue(pixel);
                int other=Math.max(green,blue);
                if(red<=25||red*10<=green*11||red*10<=blue*11||red-other<=6)continue;
                int neutral=Math.min(green,blue),chroma=Math.max(0,red-neutral);
                int nr=channel((chroma*(double)tr/peak+neutral)*scale);
                int ng=channel((chroma*(double)tg/peak+neutral)*scale);
                int nb=channel((chroma*(double)tb/peak+neutral)*scale);
                pixels[pos]=Color.argb(Color.alpha(pixel),nr,ng,nb);
            }
        }
        Bitmap result=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);
        result.setPixels(pixels,0,width,0,0,width,height);
        original.recycle();
        return result;
    }
    private static int channel(double value){return Math.max(0,Math.min(255,(int)Math.round(value)));}
    private DailyCarArtwork(){}
}
