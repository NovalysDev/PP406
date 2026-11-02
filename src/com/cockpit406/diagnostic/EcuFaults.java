package com.cockpit406.diagnostic;

import java.util.*;

/** Profile-scoped display only. Never dispatches diagnostic commands. */
public final class EcuFaults {
    private static final Map<String,Map<String,String>> LABELS=new HashMap<>(),CODES=new HashMap<>();
    private static synchronized Map<String,String> table(String module,boolean labels) {
        Map<String,Map<String,String>> cache=labels?LABELS:CODES;
        Map<String,String> result=cache.get(module);
        if(result==null) {
            result=new LinkedHashMap<>();
            for(String[] row:labels?FaultDefinitions.labels(module):FaultDefinitions.codes(module))result.put(row[0],row[1]);
            cache.put(module,result);
        }
        return result;
    }
    private static String raw(byte[] data,int begin,int end) {
        return VciUsbProtocol.hex(Arrays.copyOfRange(data,begin,end)).toUpperCase(Locale.ROOT);
    }
    private static String description(String module,String key) {
        String code=table(module,false).get(key);
        if(code!=null&&code.indexOf('|')>=0) {
            StringBuilder alternatives=new StringBuilder("Correspondance dépendante de la variante du calculateur · à confirmer :");
            Set<String> unique=new LinkedHashSet<>();
            for(String option:code.split("\\|")) {
                String text=table(module,true).get(option);
                unique.add(text==null?"Description non confirmée pour le code interne "+option:text);
            }
            for(String text:unique)alternatives.append("\n• ").append(text);
            return alternatives.append("\nAucun de ces libellés n’est confirmé pour la variante branchée.").toString();
        }
        String text=code==null?null:table(module,true).get(code);
        return text==null?"Description non confirmée pour ce code et ce profil." : text;
    }
    private static void append(StringBuilder out,String code,String description) {
        if(out.length()>0)out.append("\n\n");
        out.append(code).append('\n').append(description);
    }
    public static String motor(String reference,List<String> codes) {
        if(codes.isEmpty())return "Aucun défaut retourné par le calculateur.";
        StringBuilder result=new StringBuilder();
        for(String code:codes)append(result,code.toUpperCase(Locale.ROOT),
            "0261204407".equals(reference)?description("motor",code.toLowerCase(Locale.ROOT)):"Description indisponible pour cette référence moteur.");
        return result.toString();
    }
    public static boolean complete(String module,byte[] data) {
        if(data==null||data.length==0||data.length>129)return false;
        if(module.equals("seat"))return data.length==11&&data[0]==0x33;
        if(module.equals("autoal4"))return data.length>=2&&data[0]==0x57&&(data.length-2)%5==0&&(data[1]&255)==(data.length-2)/5;
        if(data[0]!=0x33)return false;
        int width=module.equals("abs5")||module.equals("abs53")?5:2;
        return (data.length-1)%width==0;
    }
    /** Absence is established from the wire layout, never from a translated label. */
    public static boolean empty(String module,byte[] data) {
        if(!complete(module,data))throw new IllegalArgumentException("Liste de défauts incomplète");
        if(module.equals("seat")){for(int i=1;i<=5;i++)if(data[i]!=0)return false;return true;}
        return module.equals("autoal4")?data.length==2:data.length==1;
    }
    public static String format(String module,byte[] data) {
        StringBuilder out=new StringBuilder();
        if(module.equals("seat")) {
            if(data.length!=11||data[0]!=0x33)return unknown(data);
            for(int bit=0;bit<40;bit++)if(((data[1+bit/8]&255)&(1<<(bit%8)))!=0) {
                String status=((data[6+bit/8]&255)&(1<<(bit%8)))!=0?"permanent":"fugitive";
                append(out,"Position "+(bit+1),description(module,bit+":"+status));
            }
        } else if(module.equals("locking")) {
            if(data.length!=17)return unknown(data);
            for(int i=0;i<15;i++) {
                int counter=data[2+i]&255;
                if(counter<=40)append(out,"Compteur "+(i+1)+" · "+counter,
                    description(module,i+":"+(counter==40?"permanent":"fugitive")));
                else if(counter!=255)append(out,"Compteur "+(i+1)+" · "+counter,"Valeur de compteur non interprétée.");
            }
        } else if(module.equals("autoal4")) {
            // CAL137 / C_TableDefautPSA2000: count + 2 code/status bytes +
            // three context bytes per entry for the legacy PSA2000 profile.
            if(data.length<2||data[0]!=0x57||(data.length-2)%5!=0||(data[1]&255)!=(data.length-2)/5)return unknown(data);
            for(int i=2;i<data.length;i+=5) {
                // mGetDef ignores bit zero of the status/characteristic byte.
                String key=String.format(Locale.ROOT,"%02x%02x",data[i]&0xfe,data[i+1]&255);
                append(out,raw(data,i,i+2),description(module,key)+"\nContexte brut : "+raw(data,i+2,i+5));
            }
        } else if(module.equals("abs5")||module.equals("abs53")) {
            if(data.length<1||data[0]!=0x33||(data.length-1)%5!=0)return unknown(data);
            for(int i=1;i<data.length;i+=5)append(out,raw(data,i,i+2),
                description(module,raw(data,i,i+2).toLowerCase(Locale.ROOT))+"\nContexte brut : "+raw(data,i+2,i+5));
        } else {
            if(data.length<1||data[0]!=0x33||(data.length-1)%2!=0)return unknown(data);
            for(int i=1;i<data.length;i+=2) {
                String key=raw(data,i,i+2).toLowerCase(Locale.ROOT);
                if(key.equals("0000")&&!module.equals("steering"))continue;
                String text=description(module,key);
                if(module.equals("steering")&&!text.startsWith("Description non confirmée")) {
                    text=text.replaceAll("(?iu)défaut permanent","Statut à confirmer par le contexte");
                }
                append(out,raw(data,i,i+2),text);
            }
        }
        return out.length()==0?"Aucune entrée de défaut décodée dans la réponse.":out.toString();
    }
    public static String climate(List<byte[]> pages) {
        // CAL5 removes the page marker and the trailing pagination byte from
        // the first two blocks, then concatenates the final block's data.
        if(pages.isEmpty())return "Aucun défaut retourné par ce calculateur.";
        if(pages.size()>3)return "Pagination non interprétée.\n"+pagesRaw(pages);
        java.io.ByteArrayOutputStream merged=new java.io.ByteArrayOutputStream();
        merged.write(0x33);
        for(int p=0;p<pages.size();p++) {
            byte[] block=pages.get(p);
            int end=p<2?block.length-1:block.length;
            if(end<1||(end-1)%2!=0)return "Pagination non interprétée.\n"+pagesRaw(pages);
            merged.write(block,1,end-1);
        }
        return format("climate",merged.toByteArray());
    }
    private static String pagesRaw(List<byte[]> pages) {
        StringBuilder text=new StringBuilder();for(byte[] page:pages)text.append(raw(page,0,page.length)).append('\n');return text.toString();
    }
    private static String unknown(byte[] data) {
        return "Format de défauts non interprété pour ce profil.\nRéponse réelle : "+raw(data,0,data.length)+"\nAucune conclusion sur l’absence de défaut.";
    }
    private EcuFaults() {}
}
