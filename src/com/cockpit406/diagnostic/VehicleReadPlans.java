package com.cockpit406.diagnostic;

import org.json.*;
import java.util.*;

/** Fixed, audited read plans. A model inventory is never executable by itself. */
public final class VehicleReadPlans {
    public static final class Module {
        public final String id,title,unavailable;
        public final List<String> candidates;
        Module(JSONObject value)throws JSONException {
            id=value.getString("module");title=ScanEquipment.title(id,value.getString("title"));
            List<String> list=new ArrayList<>();JSONArray a=value.getJSONArray("candidates");
            for(int i=0;i<a.length();i++)list.add(a.getString(i));candidates=Collections.unmodifiableList(list);
            StringBuilder reason=new StringBuilder();a=value.getJSONArray("unavailable");
            for(int i=0;i<a.length();i++){JSONObject item=a.getJSONObject(i);if(reason.length()>0)reason.append('\n');reason.append(item.getString("name")).append(" : ").append(item.getString("reason"));}
            unavailable=reason.toString();
        }
    }
    public static final class Plan {
        public final String key,label;public final List<Module> modules;
        Plan(JSONObject value)throws JSONException {
            key=value.getString("key");label=value.getString("label");List<Module> list=new ArrayList<>();JSONArray a=value.getJSONArray("modules");
            for(int i=0;i<a.length();i++)list.add(new Module(a.getJSONObject(i)));modules=Collections.unmodifiableList(list);
        }
        public Module module(String id){for(Module m:modules)if(m.id.equals(id))return m;return null;}
        public List<GlobalEcuScan.Row> rows(){List<GlobalEcuScan.Row> out=new ArrayList<>();for(Module m:modules)out.add(new GlobalEcuScan.Row("set:"+m.id,m.id,m.title));return out;}
    }
    public final Map<String,Plan> models=new LinkedHashMap<>();
    public final Plan automatic;
    private final Map<String,JSONObject> readers=new LinkedHashMap<>();
    private final JSONObject namedTypes;
    public VehicleReadPlans(JSONObject value)throws JSONException {
        if(value.getInt("schema")!=1)throw new JSONException("Version de catalogue incompatible");
        namedTypes=value.getJSONObject("named_types");
        JSONObject definitions=value.getJSONObject("readers");Iterator<String> keys=definitions.keys();
        while(keys.hasNext()){String key=keys.next();readers.put(key,definitions.getJSONObject(key));}
        JSONArray a=value.getJSONArray("models");for(int i=0;i<a.length();i++){Plan p=new Plan(a.getJSONObject(i));models.put(p.key,p);}
        automatic=new Plan(value.getJSONObject("automatic"));validate(automatic);for(Plan p:models.values())validate(p);
    }
    private void validate(Plan p)throws JSONException {
        Set<String> ids=new HashSet<>();
        for(Module m:p.modules){if(!ids.add(m.id))throw new JSONException("Module dupliqué");for(String key:m.candidates)
            if(!key.equals("motor")&&OtherEcuSession.profile(key)==null&&!readers.containsKey(key))throw new JSONException("Lecteur manquant : "+key);}
    }
    public boolean generic(String key){return readers.containsKey(key);}
    public boolean prepared(int type,int route){return generic("read:"+type+":"+route)||namedTypes.has(type+":"+route);}
    public JSONObject definition(String key){return readers.get(key);}
    public String title(String key){JSONObject p=readers.get(key);if(p!=null)return p.optString("title");OtherEcuSession.Profile other=OtherEcuSession.profile(key);return other==null?"Bosch MP7.0":other.title==null?other.module:other.title;}
    public static EngineReadSession.Profile parse(JSONObject p)throws JSONException {
        JSONArray sha=p.getJSONArray("sha256");EngineReadSession.Profile out=new EngineReadSession.Profile(p.getInt("id"),p.getString("title"),p.getBoolean("kwp"),p.getInt("init_length"),sha.getString(0),sha.getString(1));
        if(p.optBoolean("identity_only",false))out.identityOnly();
        out.faultPrefix(p.getInt("fault_prefix"));JSONArray c=p.getJSONArray("commands");
        for(int i=0;i<c.length();i++){JSONObject v=c.getJSONObject(i);out.read(v.getString("name"),v.getInt("family"),v.getInt("op"),v.getString("data"));}return out;
    }
}
