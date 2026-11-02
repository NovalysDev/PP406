package com.cockpit406.diagnostic;

/** Presentation only: does not construct or send diagnostic requests. */
public final class KeyReadingPresentation {
    public static String select(String action,String readings) {
        if(readings==null)return null;
        String[] prefixes;
        if("keys_configuration_values".equals(action)) prefixes=new String[]{"Configuration ","Nombre de clés mémorisées :"};
        else if("keys_inputs_values".equals(action)) prefixes=new String[]{"Contact après contact :","Porte conducteur ","Code de clé ","Batterie "};
        else if("keys_engine_values".equals(action)) prefixes=new String[]{"Calculateur moteur "};
        else return readings;
        StringBuilder out=new StringBuilder();
        for(String line:readings.split("\\n")) for(String prefix:prefixes) if(line.startsWith(prefix)) {
            if(out.length()>0)out.append('\n');out.append(line);break;
        }
        return out.length()==0?"En attente de la lecture correspondante.":out.toString();
    }
    private KeyReadingPresentation() {}
}
