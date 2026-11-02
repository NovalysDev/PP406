package com.cockpit406.diagnostic;

import java.util.*;
import java.util.regex.*;

/** Immutable display snapshot for one ECU. This class never requests a diagnosis. */
public final class FaultReport {
    public static final class Entry {
        public final String code,description,context;
        Entry(String code,String description,String context){this.code=code;this.description=description;this.context=context;}
        public String preview(){int end=description.indexOf('\n');return end<0?description:description.substring(0,end);}
    }
    private static final Pattern CODE=Pattern.compile("(?i)(?:[0-9a-f]{4,8}|[PBCU][0-9a-f]{4}|Position [0-9]+|Compteur [0-9]+ · [0-9]+)");
    private static final Pattern CONTEXT=Pattern.compile("(?m)^Contexte ([0-9a-fA-F]{4,8}) : ([^\\r\\n]+)\\r?\\n?");
    public final String module,title,profile,text,notice,carId,observationId;
    public final long readAt;
    public final List<Entry> entries;
    public final List<String> notes;
    public FaultReport(String module,String title,String profile,long readAt,String text,String notice) {
        this(module,title,profile,readAt,text,notice,"",java.util.UUID.randomUUID().toString());
    }
    public FaultReport(String module,String title,String profile,long readAt,String text,String notice,String carId,String observationId) {
        if(module==null||module.isEmpty()||readAt<=0)throw new IllegalArgumentException("Calculateur et date requis");
        this.carId=carId==null?"":carId;this.observationId=observationId;
        this.module=module;this.title=title;this.profile=profile;this.readAt=readAt;this.text=text==null?"":text;this.notice=notice==null?"":notice;
        Map<String,String> contexts=new LinkedHashMap<>();Matcher matcher=CONTEXT.matcher(this.text);
        while(matcher.find())contexts.put(matcher.group(1).toUpperCase(Locale.ROOT),matcher.group(2));
        String cleaned=matcher.replaceAll("");
        List<Entry> items=new ArrayList<>();List<String> info=new ArrayList<>();
        for(String block:cleaned.split("\\r?\\n\\s*\\r?\\n")) {
            block=block.trim();if(block.isEmpty())continue;
            int end=block.indexOf('\n');String first=(end<0?block:block.substring(0,end)).trim();
            // A raw hex reply below a warning is a note, never a decoded fault.
            if(end>0&&CODE.matcher(first).matches()) {
                String ctx=contexts.remove(first.toUpperCase(Locale.ROOT));
                String description=block.substring(end+1).trim();
                int raw=description.indexOf("\nContexte brut : ");
                if(raw>=0){ctx=description.substring(raw+1);description=description.substring(0,raw);}
                items.add(new Entry(first,description,ctx==null?"":ctx));
            }else info.add(block);
        }
        for(Map.Entry<String,String> ctx:contexts.entrySet())info.add("Contexte "+ctx.getKey()+" : "+ctx.getValue()+"\nRattachement à un défaut non confirmé.");
        entries=Collections.unmodifiableList(items);notes=Collections.unmodifiableList(info);
    }
    public FaultReport withNotice(String message){return new FaultReport(module,title,profile,readAt,text,message,carId,observationId);}
    public String summary(){return !notice.isEmpty()?notice:entries.isEmpty()?"Résultat disponible · ouvrir le rapport":entries.size()+" entrée"+(entries.size()>1?"s":"")+" de défaut · ouvrir le rapport";}
}
