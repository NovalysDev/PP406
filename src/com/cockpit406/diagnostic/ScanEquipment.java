package com.cockpit406.diagnostic;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Only an explicit user declaration can omit an optional module.
 * Unknown equipment is always searched; this never changes ECU presence. */
public final class ScanEquipment {
    public static final String[] MODULES={"automatic","seat","suspension","ecu_56","ecu_80","ecu_81"};
    public static final String[] TITLES={"Boîte automatique","Sièges électriques à mémoire","Suspension pilotée","Correction automatique des phares","Correcteur du projecteur droit","Correcteur du projecteur gauche"};
    public static final String[] DESCRIPTIONS={"Transmission automatique, avec son calculateur de boîte.","Réglage électrique et mémorisation des positions des sièges.","Amortissement réglé par un calculateur.","Règle automatiquement la hauteur du faisceau selon l’assiette de la voiture. Ce n’est pas la molette de réglage manuel.","Calculateur du correcteur de hauteur du phare droit, si cette version possède deux calculateurs séparés.","Calculateur du correcteur de hauteur du phare gauche, si cette version possède deux calculateurs séparés."};
    public static String title(String module,String fallback){
        if("ecu_29".equals(module)&&"SACLATD".equalsIgnoreCase(fallback))return "Airbag latéral droit";
        if("ecu_30".equals(module)&&"SACLATG".equalsIgnoreCase(fallback))return "Airbag latéral gauche";
        for(int i=0;i<MODULES.length;i++)if(MODULES[i].equals(module))return TITLES[i];return fallback;
    }
    private final Set<String> omitted;
    public ScanEquipment(Set<String> absent) {
        Set<String> copy=new LinkedHashSet<>();
        for(String id:absent) {
            boolean optional=false;for(String allowed:MODULES)if(allowed.equals(id))optional=true;
            if(!optional)throw new IllegalArgumentException("Équipement non facultatif : "+id);
            copy.add(id);
        }
        omitted=Collections.unmodifiableSet(copy);
    }
    public static ScanEquipment searchAll(){return new ScanEquipment(Collections.emptySet());}
    public boolean searches(String module){return !omitted.contains(module);}
    public Set<String> omitted(){return omitted;}
}
