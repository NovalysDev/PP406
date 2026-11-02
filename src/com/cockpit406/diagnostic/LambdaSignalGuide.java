package com.cockpit406.diagnostic;

import java.util.Map;

/** Read-only indication of low oxygen voltage in a stated load context, never an AFR or DTC. */
public final class LambdaSignalGuide {
    public static final double DEFAULT_LOW_MV=100,DEFAULT_HIGH_MV=1000;
    public static final String POLICY="MP7.0 / V6 XFZ identifié : rouge si le signal dépasse le maximum électrique renseigné, ou s’il est au plus au seuil bas en charge. "
        +"Contexte PP406 : liquide ≥80 °C, régime ≥2500 tr/min, admission ≥850 mbar, papillon ≥35°, mesures fraîches issues du même groupe. "
        +"Par défaut : signal bas ≤100 mV, maximum 1000 mV. Les couleurs signalent une observation à vérifier, pas une panne de sonde ni un AFR mesuré. "
        +"En dehors du contexte de charge, la tension basse n’est pas signalée comme anormale. Une décélération peut produire un signal bas. "
        +"Les repères de richesse 100/800 mV viennent du document constructeur MP7.0 ; les filtres de charge et les durées sont des choix de surveillance PP406, sans tolérance de panne constructeur.";
    private long previous=-1,lowSince=-1;
    private int lowSamples;
    private void reset(){previous=-1;lowSince=-1;lowSamples=0;}
    public static boolean validLimits(Double low,Double high){
        double minimum=low==null?DEFAULT_LOW_MV:low,maximum=high==null?DEFAULT_HIGH_MV:high;
        return ValueAlerts.validRange(minimum,maximum)&&minimum>=0&&maximum>0;
    }
    public EngineValueGuide.Result evaluate(String reference,String vin,Map<String,Double> values,Map<String,Long> ages,long now,boolean live,boolean enabled,double low,double high){
        if(!enabled||!live||!EngineValueGuide.v6Profile(reference,vin)||!ValueAlerts.validRange(low,high)||low<0){reset();return EngineValueGuide.neutral("Surveillance lambda inactive ou profil non confirmé. "+POLICY);}
        for(String key:new String[]{"oxygen_mv","rpm","map_mbar","throttle_deg","coolant_r_c"})
            if(!EngineValueGuide.fresh(key,values,ages,now)){reset();return EngineValueGuide.neutral("Contexte lambda incomplet ou ancien : pas de couleur de diagnostic.");}
        long stamp=ages.get("oxygen_mv");
        // Context must come from this response, never from another ECU/group or a former run.
        for(String key:new String[]{"rpm","map_mbar","throttle_deg","coolant_r_c"})if(ages.get(key)!=stamp){reset();return EngineValueGuide.neutral("Contexte lambda non simultané.");}
        double oxygen=values.get("oxygen_mv");
        if(oxygen<0||oxygen>high){reset();return new EngineValueGuide.Result(EngineValueGuide.Level.RED,"Signal lambda hors plage","Signal électrique hors de 0 à "+high+" mV ; contrôler la lecture et le circuit. "+POLICY);}
        boolean load=values.get("coolant_r_c")>=80&&values.get("rpm")>=2500&&values.get("map_mbar")>=850&&values.get("throttle_deg")>=35;
        if(!load){reset();return EngineValueGuide.neutral("Hors du contexte de charge : signal bas non assimilé à une panne. "+POLICY);}
        if(oxygen<=low){
            if(previous<0||stamp<previous||stamp-previous>1500){lowSince=stamp;lowSamples=0;}
            if(stamp!=previous){lowSamples++;previous=stamp;}
            long duration=Math.max(0,stamp-lowSince);
            return new EngineValueGuide.Result(EngineValueGuide.Level.RED,duration>=2000?"Signal pauvre persistant en charge":"Signal pauvre en charge",
                "Signal ≤"+low+" mV en charge : "+lowSamples+" mesure(s), "+duration+" ms observées sans interruption. Vérifier le contexte, la richesse et le signal ; ce relevé ne localise pas une panne. "+POLICY);
        }
        reset();return new EngineValueGuide.Result(EngineValueGuide.Level.NORMAL,"Signal non bas en charge","Le signal est au-dessus du seuil bas et dans la plage électrique renseignée. Cela ne garantit ni l’AFR ni l’état des injecteurs. "+POLICY);
    }
}
