package com.cockpit406.diagnostic;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Full-screen report within the active Activity: closing it never stops USB. */
public final class FaultResultsPage extends Dialog {
    private static final int BG=Color.rgb(13,18,23),PANEL=Color.rgb(23,31,38),WHITE=Color.rgb(243,246,247),MUTED=Color.rgb(160,177,188),AMBER=Color.rgb(255,191,86);
    private final List<FaultReport> reports;
    private boolean detail;
    public FaultResultsPage(Context context,Collection<FaultReport> reports) {
        super(context,android.R.style.Theme_Material_NoActionBar);
        this.reports=new ArrayList<>(reports);setCanceledOnTouchOutside(false);renderList();
    }
    @Override public void show(){super.show();getWindow().setLayout(-1,-1);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_FULLSCREEN);}
    private int dp(int v){return Math.round(v*getContext().getResources().getDisplayMetrics().density);}
    private TextView label(String s,int size,int color){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(8),0,dp(8));return t;}
    private Button button(String s,View.OnClickListener action){Button b=new Button(getContext());b.setText(s);b.setTextColor(WHITE);b.setAllCaps(false);b.setOnClickListener(action);return b;}
    private LinearLayout page(String title,boolean isDetail) {
        detail=isDetail;LinearLayout root=new LinearLayout(getContext());root.setOrientation(1);root.setPadding(dp(20),dp(12),dp(20),dp(12));root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(getContext());header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header);
        header.addView(label(title,25,WHITE),new LinearLayout.LayoutParams(0,-2,1));
        if(isDetail)header.addView(button("‹ Défauts",v->renderList()),new LinearLayout.LayoutParams(dp(160),dp(56)));
        header.addView(button("Fermer",v->dismiss()),new LinearLayout.LayoutParams(dp(130),dp(56)));
        ScrollView scroll=new ScrollView(getContext());LinearLayout body=new LinearLayout(getContext());body.setOrientation(1);scroll.addView(body);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);return body;
    }
    private String stamp(FaultReport r){return "Relevé du "+new SimpleDateFormat("dd/MM/yyyy à HH:mm:ss",Locale.FRANCE).format(new Date(r.readAt));}
    private void renderList() {
        LinearLayout body=page("Rapport des défauts",false);
        body.addView(label("Résultats conservés par calculateur. Fermer cette page conserve le rapport ; sa réouverture ne lance aucune lecture.",15,MUTED));
        if(reports.isEmpty())body.addView(label("Aucun calculateur n’a encore retourné de résultat de défauts.",20,AMBER));
        for(FaultReport r:reports){
            LinearLayout group=new LinearLayout(getContext());group.setOrientation(1);group.setPadding(dp(18),dp(12),dp(18),dp(16));group.setBackgroundColor(PANEL);
            LinearLayout.LayoutParams margin=new LinearLayout.LayoutParams(-1,-2);margin.setMargins(0,dp(16),0,0);body.addView(group,margin);
            group.addView(label(r.title,24,WHITE));group.addView(label(stamp(r)+"\n"+r.profile,14,MUTED));
            if(!r.notice.isEmpty())group.addView(label(r.notice,17,AMBER));
            for(String note:r.notes)group.addView(label(note,16,MUTED));
            for(FaultReport.Entry entry:r.entries){
                Button item=button(entry.code+" · "+entry.preview()+"\nVoir le détail ›",v->renderDetail(r,entry));item.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);item.setPadding(dp(14),dp(12),dp(14),dp(12));
                group.addView(item,new LinearLayout.LayoutParams(-1,-2));
            }
        }
    }
    private void renderDetail(FaultReport r,FaultReport.Entry entry) {
        LinearLayout body=page(r.title+" · "+entry.code,true);
        body.addView(label(stamp(r)+"\n"+r.profile,15,MUTED));
        if(!r.notice.isEmpty())body.addView(label(r.notice,18,AMBER));
        body.addView(label("Description et état",21,WHITE));body.addView(label(entry.description,20,WHITE));
        body.addView(label("Contexte de lecture",21,WHITE));body.addView(label(entry.context.isEmpty()?"Aucun contexte supplémentaire décodé pour cette entrée.":entry.context,17,MUTED));
        body.addView(label("Contrôles",21,WHITE));body.addView(label("La description ci-dessus reprend les informations disponibles pour ce profil. Une description non confirmée reste signalée. Aucune procédure de contrôle supplémentaire n’est encore associée à cette entrée.",17,MUTED));
    }
    @Override public void onBackPressed(){if(detail)renderList();else dismiss();}
}
