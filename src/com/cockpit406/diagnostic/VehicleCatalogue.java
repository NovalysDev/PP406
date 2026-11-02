package com.cockpit406.diagnostic;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Offline preparation catalogue. No USB, session, permission or command access. */
final class VehicleCatalogue {
    private final Activity activity;
    private final JSONObject catalog,ecus;
    private final VehicleReadPlans plans;
    private VehicleCatalogue(Activity activity)throws Exception {
        this.activity=activity;
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=activity.getAssets().open("peugeot-catalogue.json")) {
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
        }
        catalog=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));ecus=catalog.getJSONObject("ecu_types");
        bytes.reset();try(InputStream in=activity.getAssets().open("vehicle-readers.json")){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)bytes.write(b,0,n);}
        plans=new VehicleReadPlans(new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8)));
    }
    static void show(Activity activity,Integer modelId){
        try{VehicleCatalogue c=new VehicleCatalogue(activity);if(modelId==null)c.models();else c.model(c.find(modelId));}
        catch(Exception e){new AlertDialog.Builder(activity).setTitle("Catalogue Peugeot").setMessage("Catalogue indisponible : "+e.getMessage()).setPositiveButton("Retour",null).show();}
    }
    private JSONObject find(int id)throws JSONException {
        JSONArray models=catalog.getJSONArray("models");for(int i=0;i<models.length();i++)if(models.getJSONObject(i).getInt("id")==id)return models.getJSONObject(i);
        throw new JSONException("Branche inconnue");
    }
    private int dp(int n){return Math.round(n*activity.getResources().getDisplayMetrics().density);}
    private TextView text(String message){TextView t=new TextView(activity);t.setText(message);t.setTextSize(16);t.setTextColor(Color.WHITE);t.setPadding(dp(12),dp(10),dp(12),dp(10));return t;}
    private void models()throws JSONException {
        LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);
        JSONObject summary=catalog.getJSONObject("summary");
        body.addView(text(summary.getInt("models")+" branches Peugeot · "+summary.getInt("ecu_types")+" types de calculateurs\nInventaire des montages possibles. La présence dans cette liste ne garantit pas la compatibilité de toutes les fonctions."));
        EditText search=new EditText(activity);search.setSingleLine(true);search.setHint("Rechercher un modèle ou une branche PP2000");body.addView(search);
        ListView list=new ListView(activity);body.addView(list,new LinearLayout.LayoutParams(-1,dp(280)));
        JSONArray all=catalog.getJSONArray("models");List<JSONObject> visible=new ArrayList<>();List<String> labels=new ArrayList<>();
        ArrayAdapter<String> adapter=new ArrayAdapter<>(activity,android.R.layout.simple_list_item_1,labels);list.setAdapter(adapter);
        Runnable filter=()->{
            visible.clear();labels.clear();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
            for(int i=0;i<all.length();i++){JSONObject m=all.optJSONObject(i);String label=m.optString("label");if((label+" "+m.optString("name")).toLowerCase(Locale.ROOT).contains(query)){
                visible.add(m);labels.add(label+" · "+m.optJSONArray("modules").length()+" familles de calculateurs");
            }}adapter.notifyDataSetChanged();
        };
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){filter.run();}public void afterTextChanged(Editable e){}});
        list.setOnItemClickListener((parent,view,position,id)->model(visible.get(position)));filter.run();
        new AlertDialog.Builder(activity).setTitle("Préparation des véhicules Peugeot").setView(body).setNegativeButton("Retour au diagnostic",null).show();
    }
    private void model(JSONObject m){
        JSONArray modules=m.optJSONArray("modules");String[] labels=new String[modules.length()];
        for(int i=0;i<labels.length;i++){JSONObject module=modules.optJSONObject(i);labels[i]=module.optString("name")+" · "+module.optJSONArray("candidates").length()+" montage(s) candidat(s)";}
        new AlertDialog.Builder(activity).setTitle(m.optString("label")+" · montages répertoriés")
            .setItems(labels,(dialog,which)->module(m,modules.optJSONObject(which)))
            .setNeutralButton("État de préparation",(dialog,which)->details("État de préparation · "+m.optString("label"),
                modelCoverage(m)+"\n\nLa présence réelle des calculateurs et la compatibilité des nouvelles lectures restent à vérifier sur voiture. Les fonctions avancées dépendent du profil exact.\n\nLes identifiants de routage ne constituent pas une mesure électrique des broches. Une absence de réponse ne prouve pas une broche défectueuse."))
            .setNegativeButton("Retour",null).show();
    }
    private String modelCoverage(JSONObject model){
        VehicleReadPlans.Plan plan=plans.models.get(model.optString("key"));int ready=0,profiles=0;
        if(plan!=null)for(VehicleReadPlans.Module module:plan.modules)if(!module.candidates.isEmpty()){ready++;profiles+=module.candidates.size();}
        return model.optInt("route_count")+" associations de montage répertoriées.\n"+ready+" familles avec un lecteur intégré · "+profiles+" profils de lecture candidats.\nLes autres variantes restent à intégrer ; leur inventaire ne les rend pas exécutables.";
    }
    private void module(JSONObject model,JSONObject module){
        JSONArray candidates=module.optJSONArray("candidates");String[] labels=new String[candidates.length()];
        for(int i=0;i<labels.length;i++){JSONObject route=candidates.optJSONObject(i),e=ecus.optJSONObject(String.valueOf(route.optInt("type_id")));
            labels[i]=e.optString("name")+" ["+e.optInt("id")+"] · "+e.optString("transport");}
        new AlertDialog.Builder(activity).setTitle(model.optString("label")+" · "+module.optString("name"))
            .setItems(labels,(dialog,which)->ecu(candidates.optJSONObject(which)))
            .setNegativeButton("Retour",null).show();
    }
    private void ecu(JSONObject route){
        JSONObject e=ecus.optJSONObject(String.valueOf(route.optInt("type_id")));JSONObject counts=e.optJSONObject("command_counts");
        boolean prepared=route.optInt("plug_type_id")==2&&route.optInt("multiplexed")==0&&route.optInt("pin16_18")==0&&plans.prepared(route.optInt("type_id"),route.optInt("route_id"));
        StringBuilder body=new StringBuilder(prepared?"Un lecteur est intégré pour ce routage. Identification, défauts et groupes disponibles selon le profil ; les nouvelles conversions et variantes restent à vérifier sur voiture. Cela n’active pas les fonctions avancées de l’inventaire.":e.optString("reason"));
        body.append("\n\nTransport : ").append(e.optString("transport")).append("\nRoutage documentaire PP2000 : ").append(route.optInt("route_id"));
        body.append("\nLecteur intégré pour ce routage : ").append(prepared?"oui, compatibilité à vérifier sur véhicule":"non");
        body.append("\n\nLectures moteur préparées : ").append(e.optInt("engine_read_groups")).append(" groupe(s) de valeurs brutes.");
        if(!e.isNull("numeric_decode_scope"))body.append("\nConversions : ").append(e.optString("numeric_decode_scope"));
        body.append("\n\nCommandes répertoriées dans PP2000 (inventaire, sans activation) :\n");
        String[][] categories={{"identification","Identification"},{"measurements","Mesures"},{"fault_read","Lecture des défauts"},{"fault_erase","Effacement"},{"actuators","Tests"},{"learning","Apprentissages"},{"coding","Codage"},{"keys","Clés"},{"programming_or_memory","Programmation / mémoire"},{"session","Session"},{"other","Autres"}};
        for(String[] category:categories)if(counts.optInt(category[0])>0)body.append(category[1]).append(" : ").append(counts.optInt(category[0])).append('\n');
        body.append("\nSymboles disponibles :\n");JSONArray names=e.optJSONArray("command_names");for(int i=0;i<names.length();i++)body.append(names.optString(i)).append('\n');
        details(e.optString("name")+" · type "+e.optInt("id"),body.toString());
    }
    private void details(String title,String message){
        ScrollView scroll=new ScrollView(activity);TextView content=text(message);content.setTextIsSelectable(true);scroll.addView(content);
        new AlertDialog.Builder(activity).setTitle(title).setView(scroll).setPositiveButton("Retour",null).show();
    }
}
