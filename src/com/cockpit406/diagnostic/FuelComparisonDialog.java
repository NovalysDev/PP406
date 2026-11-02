package com.cockpit406.diagnostic;

import android.app.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** A dialog keeps the USB-reading Activity resumed while graphs are consulted. */
public final class FuelComparisonDialog {
    private final Activity activity;
    private final Dialog dialog;
    private final TextView info,summary;
    private final Spinner first,second,metric,axis;
    private final Plot plot;
    private List<FuelRecording.Trip> trips=new ArrayList<>();
    private FuelRecording.Trip a,b;
    private int generation;
    private boolean ready;
    private FuelComparisonDialog(Activity activity){
        this.activity=activity;plot=new Plot(activity);dialog=new Dialog(activity);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root=new LinearLayout(activity);root.setOrientation(1);root.setPadding(16,8,16,8);root.setBackgroundColor(Color.rgb(13,18,23));
        LinearLayout bar=new LinearLayout(activity);root.addView(bar);TextView title=text("Comparatifs carburants · relevés réels",20);bar.addView(title,new LinearLayout.LayoutParams(0,52,1));
        Button close=button("Fermer",v->dialog.dismiss());bar.addView(close);
        info=text("Chargement des enregistrements…",13);root.addView(info);
        TextView backup=text(TripBackups.status+" · toucher pour relancer l’envoi",12);backup.setOnClickListener(v->{TripBackups.kick(activity,null,()->activity.runOnUiThread(()->backup.setText(TripBackups.status)));backup.setText("Sauvegarde réseau demandée…");});root.addView(backup);
        LinearLayout selectors=new LinearLayout(activity);root.addView(selectors);
        first=spinner();second=spinner();selectors.addView(first,new LinearLayout.LayoutParams(0,52,1));selectors.addView(second,new LinearLayout.LayoutParams(0,52,1));
        LinearLayout options=new LinearLayout(activity);root.addView(options);metric=spinner();axis=spinner();options.addView(metric,new LinearLayout.LayoutParams(0,52,1));options.addView(axis,new LinearLayout.LayoutParams(0,52,1));
        options.addView(button("−",v->plot.zoom(.5)));options.addView(button("+",v->plot.zoom(2)));options.addView(button("Tout",v->plot.reset()));
        String[] names=new String[FuelRecording.METRICS.length];for(int i=0;i<names.length;i++)names[i]=FuelRecording.METRICS[i][1]+" ("+FuelRecording.METRICS[i][2]+")";
        metric.setAdapter(adapter(names));axis.setAdapter(adapter(new String[]{"Temps de lecture (s)","Régime moteur (tr/min)","Vitesse (km/h)"}));
        summary=text("",13);root.addView(summary);root.addView(plot,new LinearLayout.LayoutParams(-1,0,1));
        root.addView(text("Temps de lecture : pauses prévues pour la température extérieure exclues. Dates réelles conservées ; aucune valeur créée pendant ces pauses. Les autres pertes de mesures restent visibles. Comparer à régime, charge et température proches : ces courbes ne mesurent ni la consommation ni la compatibilité E85. Glisser, zoomer, toucher pour le détail.",12));
        dialog.setContentView(root);dialog.show();dialog.getWindow().setLayout(-1,-1);
        AdapterView.OnItemSelectedListener listener=new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int i,long id){if(!ready)return;if(p==first||p==second)load();else draw();}};
        first.setOnItemSelectedListener(listener);second.setOnItemSelectedListener(listener);metric.setOnItemSelectedListener(listener);axis.setOnItemSelectedListener(listener);
        refreshList();
    }
    public static void show(Activity activity){new FuelComparisonDialog(activity);}
    private TextView text(String s,int size){TextView v=new TextView(activity);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);v.setPadding(6,5,6,5);return v;}
    private Button button(String text,View.OnClickListener listener){Button b=new Button(activity);b.setText(text);b.setOnClickListener(listener);return b;}
    private Spinner spinner(){return new Spinner(activity);}
    private ArrayAdapter<String> adapter(String[] s){ArrayAdapter<String> a=new ArrayAdapter<>(activity,android.R.layout.simple_spinner_dropdown_item,s);return a;}
    private void refreshList(){new Thread(()->{
        List<FuelRecording.Trip> loaded=FuelRecording.list(activity.getFilesDir());activity.runOnUiThread(()->{
            if(!dialog.isShowing())return;trips=loaded;String[] names=new String[trips.size()+1];names[0]="Aucun trajet";for(int i=0;i<trips.size();i++)names[i+1]=trips.get(i).label();
            first.setAdapter(adapter(names));second.setAdapter(adapter(names));ready=true;if(!trips.isEmpty())first.setSelection(1);
            info.setText(trips.isEmpty()?"Aucun trajet enregistré. Lance Lire les valeurs, choisis le carburant puis Arrêter à la fin du trajet.":"Courbe A cyan · courbe B orange · "+trips.size()+" enregistrement(s). La lecture moteur reste active derrière cette fenêtre.");
        });
    },"PP406-trip-list").start();}
    private void load(){
        final int token=++generation,ia=first.getSelectedItemPosition()-1,ib=second.getSelectedItemPosition()-1;
        summary.setText("Chargement des mesures enregistrées…");new Thread(()->{
            FuelRecording.Trip ra=null,rb=null;String issue="";
            try{if(ia>=0&&ia<trips.size())ra=FuelRecording.read(trips.get(ia).file,true);if(ib>=0&&ib<trips.size())rb=FuelRecording.read(trips.get(ib).file,true);}catch(Exception e){issue="Lecture du trajet impossible : "+e.getMessage();}
            final FuelRecording.Trip fa=ra,fb=rb;final String error=issue;
            activity.runOnUiThread(()->{if(token!=generation||!dialog.isShowing())return;a=fa;b=fb;if(!error.isEmpty()){summary.setText(error);return;}draw();});
        },"PP406-trip-read").start();
    }
    private void draw(){
        int index=metric.getSelectedItemPosition();if(index<0)return;String[] m=FuelRecording.METRICS[index];int x=axis.getSelectedItemPosition();
        if(a!=null&&b!=null&&!a.comparable(b)){plot.set(new ArrayList<>(),new ArrayList<>(),"","",false,null);summary.setText("Comparaison refusée : véhicule, profil ou identification calculateur différents. Choisis deux trajets de la même voiture.");return;}
        ComparisonRules.Rule ar=rule(a,m[0]),br=rule(b,m[0]);ComparisonRules.Rule active=ar!=null?ar:br;
        List<Point> pa=points(a,m[0],x),pb=points(b,m[0],x);plot.set(pa,pb,axis.getSelectedItem().toString(),m[1]+" ("+m[2]+")",x>0,active);
        String missingVin=a!=null&&a.header.optString("vin").isEmpty()?"VIN absent : vérifie toi-même qu’il s’agit de la même voiture.\n":"";
        String guidance=active==null?"Repère constructeur vérifié indisponible pour ce paramètre ou ce véhicule : aucun point rouge.":"Points rouges : repère constructeur atteint ou dépassé · "+active.description;
        summary.setText(missingVin+stats("A",a,pa)+"\n"+stats("B",b,pb)+"\n"+guidance);
    }
    private static ComparisonRules.Rule rule(FuelRecording.Trip trip,String metric){return trip==null?null:ComparisonRules.find(metric,trip.reference,trip.header.optString("vin"));}
    private String stats(String label,FuelRecording.Trip trip,List<Point> points){
        if(trip==null)return label+" : aucun trajet sélectionné";if(points.isEmpty())return label+" · "+trip.label()+" : aucune mesure de ce paramètre";
        double lo=Double.POSITIVE_INFINITY,hi=Double.NEGATIVE_INFINITY,total=0;int alerts=0;for(Point p:points){lo=Math.min(lo,p.y);hi=Math.max(hi,p.y);total+=p.y;if(p.flagged)alerts++;}
        return String.format(Locale.FRANCE,"%s · %s · %d mesures · min %.1f · max %.1f · moyenne %.1f%s",label,trip.label(),points.size(),lo,hi,total/points.size(),alerts>0?" · "+alerts+" point(s) à vérifier":"");
    }
    private static final class Point {final double x,y;final long time,wall;final boolean flagged;Point(double x,double y,long time,long wall,boolean flagged){this.x=x;this.y=y;this.time=time;this.wall=wall;this.flagged=flagged;}}
    private static List<Point> points(FuelRecording.Trip trip,String metric,int axis){
        List<Point> out=new ArrayList<>();if(trip==null)return out;Double lastX=null;long lastTime=Long.MIN_VALUE;ComparisonRules.Rule rule=rule(trip,metric);
        String xKey=axis==1?"rpm":"speed_kmh";
        for(JSONObject row:trip.samples){JSONObject values=row.optJSONObject("values");long realTime=row.optLong("elapsedMs",-1),time=row.optLong("readingMs",realTime);if(values==null||realTime<0||time<0)continue;
            if(values.has(xKey)){lastX=values.optDouble(xKey);lastTime=realTime;}
            double y=values.optDouble(metric,Double.NaN);if(Double.isNaN(y)||Double.isInfinite(y))continue;
            boolean flagged=rule!=null&&rule.flagged(y);
            if(axis==0)out.add(new Point(time/1000d,y,time,row.optLong("observedAt"),flagged));else if(lastX!=null&&realTime-lastTime>=0&&realTime-lastTime<1000&&!lastX.isNaN()&&!lastX.isInfinite())out.add(new Point(lastX,y,time,row.optLong("observedAt"),flagged));
        }return out;
    }
    private static final class Plot extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final List<Point>[] data=new List[]{new ArrayList<>(),new ArrayList<>()};
        private final int[] colors={Color.CYAN,Color.rgb(255,180,75)};private String xLabel="",yLabel="";private boolean scatter;private ComparisonRules.Rule rule;
        private double allLo=0,allHi=1,lo=0,hi=1,yLo=0,yHi=1;private float previousX,downX;private boolean moved;
        Plot(Activity context){super(context);setBackgroundColor(Color.rgb(19,27,34));}
        void set(List<Point> a,List<Point> b,String x,String y,boolean scatter,ComparisonRules.Rule rule){data[0]=a;data[1]=b;xLabel=x;yLabel=y;this.scatter=scatter;this.rule=rule;
            allLo=Double.POSITIVE_INFINITY;allHi=Double.NEGATIVE_INFINITY;yLo=Double.POSITIVE_INFINITY;yHi=Double.NEGATIVE_INFINITY;
            for(List<Point> series:data)for(Point p:series){allLo=Math.min(allLo,p.x);allHi=Math.max(allHi,p.x);yLo=Math.min(yLo,p.y);yHi=Math.max(yHi,p.y);}
            if(!Double.isFinite(allLo)){allLo=0;allHi=1;yLo=0;yHi=1;}if(allHi<=allLo)allHi=allLo+1;if(yHi<=yLo){yLo-=.5;yHi+=.5;}double padding=(yHi-yLo)*.05;yLo-=padding;yHi+=padding;reset();
        }
        void reset(){lo=allLo;hi=allHi;invalidate();}
        void zoom(double factor){double center=(hi+lo)/2,width=Math.min(allHi-allLo,(hi-lo)/factor);width=Math.max((allHi-allLo)/100,width);lo=Math.max(allLo,Math.min(allHi-width,center-width/2));hi=lo+width;invalidate();}
        protected void onDraw(Canvas canvas){super.onDraw(canvas);float left=90,top=38,right=getWidth()-25,bottom=getHeight()-48;if(right<=left||bottom<=top)return;
            paint.setTextSize(20);paint.setColor(Color.LTGRAY);canvas.drawText(yLabel,left,24,paint);
            for(int i=0;i<=5;i++){float x=left+(right-left)*i/5,y=bottom-(bottom-top)*i/5;paint.setColor(Color.rgb(55,68,80));paint.setStrokeWidth(1);canvas.drawLine(left,y,right,y,paint);canvas.drawLine(x,top,x,bottom,paint);paint.setColor(Color.LTGRAY);canvas.drawText(String.format(Locale.FRANCE,"%.1f",yLo+(yHi-yLo)*i/5),4,y+6,paint);canvas.drawText(String.format(Locale.FRANCE,"%.1f",lo+(hi-lo)*i/5),x-20,bottom+22,paint);}
            canvas.drawText(xLabel,Math.max(left,right-300),getHeight()-4,paint);canvas.save();canvas.clipRect(left,top,right,bottom);
            if(rule!=null){paint.setColor(Color.rgb(240,95,95));paint.setStrokeWidth(1.5f);paint.setPathEffect(new DashPathEffect(new float[]{8,6},0));
                for(Double limit:new Double[]{rule.lower,rule.upper})if(limit!=null&&limit>=yLo&&limit<=yHi){float y=(float)(bottom-(limit-yLo)/(yHi-yLo)*(bottom-top));canvas.drawLine(left,y,right,y,paint);}paint.setPathEffect(null);}
            for(int index=0;index<2;index++){paint.setColor(colors[index]);paint.setStrokeWidth(2);Point previous=null;float px=0,py=0;
                for(Point p:data[index]){float x=(float)(left+(p.x-lo)/(hi-lo)*(right-left)),y=(float)(bottom-(p.y-yLo)/(yHi-yLo)*(bottom-top));
                    paint.setColor(colors[index]);if(!scatter&&previous!=null&&p.time-previous.time>=0&&p.time-previous.time<=2000)canvas.drawLine(px,py,x,y,paint);
                    if(p.x>=lo&&p.x<=hi){paint.setColor(p.flagged?Color.rgb(255,70,75):colors[index]);canvas.drawCircle(x,y,p.flagged?4:scatter?2:1.6f,paint);}previous=p;px=x;py=y;
                }
            }canvas.restore();
        }
        public boolean onTouchEvent(MotionEvent e){
            if(e.getAction()==MotionEvent.ACTION_DOWN){previousX=downX=e.getX();moved=false;return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){if(Math.abs(e.getX()-downX)>8)moved=true;double delta=(previousX-e.getX())/Math.max(1,getWidth()-115)*(hi-lo),width=hi-lo;lo=Math.max(allLo,Math.min(allHi-width,lo+delta));hi=lo+width;previousX=e.getX();invalidate();return true;}
            if(e.getAction()==MotionEvent.ACTION_UP&&!moved){double x=lo+(e.getX()-90)/Math.max(1,getWidth()-115)*(hi-lo);StringBuilder detail=new StringBuilder();
                for(int i=0;i<2;i++){Point best=null;for(Point p:data[i])if(best==null||Math.abs(p.x-x)<Math.abs(best.x-x))best=p;if(best!=null)detail.append(String.format(Locale.FRANCE,"%s : %.2f · %s %.2f · %s%s\n",i==0?"A":"B",best.y,xLabel,best.x,best.wall>0?new java.text.SimpleDateFormat("HH:mm:ss",Locale.FRANCE).format(new Date(best.wall)):"",best.flagged?" · À vérifier":""));}
                if(detail.length()>0)Toast.makeText(getContext(),detail.toString().trim(),Toast.LENGTH_LONG).show();performClick();return true;
            }return true;
        }
        public boolean performClick(){super.performClick();return true;}
    }
}
