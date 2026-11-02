package com.cockpit406.diagnostic;

import java.util.Map;

/** Presentation only: evaluates existing samples and never sends diagnostic requests. */
public final class EngineValueGuide {
    public static final String VEHICLE = "Peugeot 406 Coupé · ES9J4 / XFZ · boîte manuelle";
    public static final String REFERENCE = "0261204407";
    public static final String SOURCE = "Peugeot · Le Coupé · mars 1997 · tableau moteur, page PDF 68";
    public static final String SOURCE_URL = "https://406coupeclub.org/media/documents/LeCoupe%20%28French%29%201997%20March.pdf";
    public static final double RPM_CUTOFF=6800;
    public static final String HIGH_RPM_POLICY="Peugeot indique une coupure d’injection au-dessus de 6800 tr/min pour le V6 ES9J4. Ce n’est pas une consigne à atteindre.\n\n"
        +"Les couleurs de la case suivent les seuils personnels réglés dans Valeurs → Alertes : par défaut jaune dès 4000, orange dès 5000, aiguille rouge dès 6000. Le cadran affiche une zone rouge de 6400 à 8000 tr/min et clignote rapidement dès 6400 tr/min. "
        +"Ces repères d’affichage ne changent ni la coupure constructeur ni les réglages moteur. La règle effectivement appliquée est détaillée en haut de cette fiche. "
        +"La comparaison du ralenti reste soumise aux conditions ci-dessous.";
    public static final String MP70_COMPONENT_SOURCE="Documentation constructeur Citroën · principe MP7.0, éléments du système · B1HA2BP0. "
        +"Repères de fonctionnement du système MP7.0 ; aucune tolérance de panne spécifique à la 406 n’est déduite de ces repères.\n"
        +"https://xantia.fr/docs/documentation-auto/Principe/injection%20mp7-0/b1ha2bp0.html";
    public static final String CONDITIONS = "Voiture garée, boîte au point mort, moteur chaud et stabilisé, pédale relâchée, climatisation coupée, volant au repos et consommateurs électriques coupés.";
    public static final String POLICY = "Référence Peugeot : 650 tr/min. Le document ne précise pas de tolérance de diagnostic.\n\n"
        + "Marges PP406, distinctes des données constructeur : ±70 tr/min, plus ±20 tr/min pour la résolution de lecture de 40 tr/min. "
        + "Zone proche de la référence : 560 à 740 tr/min. Orange au-delà ; rouge pour un écart de plus de 300 tr/min (moins de 350 ou plus de 950 tr/min). "
        + "Un écart doit durer au moins 5 secondes et être confirmé par 3 échantillons distincts. Le retour à une zone moins élevée attend 2 secondes et 3 échantillons.\n\n"
        + "Le rouge signifie un écart important dans ces conditions, pas un diagnostic de panne ni un seuil de danger Peugeot. "
        + "Des compensations de charge peuvent relever le ralenti : vérifier les conditions avant de conclure.";
    public enum Level { NEUTRAL, WAITING, NORMAL, ORANGE, RED }
    public static final class Result {
        public final Level level;
        public final String label, explanation;
        Result(Level level,String label,String explanation){this.level=level;this.label=label;this.explanation=explanation;}
    }
    public static final class Help {
        public final String meaning, reference, checks;
        Help(String meaning,String reference,String checks){this.meaning=meaning;this.reference=reference;this.checks=checks;}
    }
    private boolean armed;
    private String inactiveReason="Contrôle du ralenti non activé";
    private Level accepted=Level.NEUTRAL,candidate=Level.NEUTRAL;
    private long candidateFirst=-1,lastSample=-1;
    private int candidateSamples;

    public boolean isArmed(){return armed;}
    public void disable(String reason){armed=false;inactiveReason=reason;clearHistory();}
    private void clearHistory(){accepted=Level.NEUTRAL;candidate=Level.NEUTRAL;candidateFirst=-1;lastSample=-1;candidateSamples=0;}
    public boolean enable(String reference,Map<String,Double> values,Map<String,Long> ages,long now,boolean live,boolean confirmed) {
        String reason=confirmed?contextIssue(reference,values,ages,now,live):"Conditions et véhicule à confirmer";
        if(reason!=null){disable(reason);return false;}
        clearHistory();armed=true;return true;
    }
    public static boolean fresh(String key,Map<String,Double> values,Map<String,Long> ages,long now){
        Double v=values.get(key);Long stamp=ages.get(key);
        return v!=null&&!Double.isNaN(v)&&!Double.isInfinite(v)&&stamp!=null&&stamp>=0&&stamp<=now&&now-stamp<3000;
    }
    public static String contextIssue(String reference,Map<String,Double> values,Map<String,Long> ages,long now,boolean live){
        if(!REFERENCE.equals(reference))return "Référence calculateur non couverte par cette fiche";
        if(!live)return "Lecture moteur arrêtée ou opération en cours";
        for(String key:new String[]{"rpm","coolant_r_c","speed_kmh","throttle_deg","battery_v"})
            if(!fresh(key,values,ages,now))return "Contexte incomplet ou ancien · attendre de nouvelles mesures";
        if(values.get("rpm")<=0)return "Moteur arrêté · contrôle du ralenti suspendu";
        if(values.get("speed_kmh")<0||values.get("speed_kmh")>=1)return "Véhicule en mouvement · contrôle du ralenti suspendu";
        if(values.get("throttle_deg")<0||values.get("throttle_deg")>1)return "Papillon ouvert · contrôle du ralenti suspendu";
        double t=values.get("coolant_r_c");
        if(t<80||t>105)return "Température hors des conditions du contrôle au ralenti";
        if(values.get("battery_v")<13)return "Charge électrique à vérifier avant de comparer le ralenti";
        return null;
    }
    public Result evaluate(String reference,Map<String,Double> values,Map<String,Long> ages,long now,boolean live){
        if(!armed)return neutral(inactiveReason);
        String issue=contextIssue(reference,values,ages,now,live);
        if(issue!=null){disable(issue+" · réactiver après vérification");return neutral(inactiveReason);}
        double rpm=values.get("rpm");
        double difference=Math.abs(rpm-650);
        Level proposed=difference>300?Level.RED:difference>90?Level.ORANGE:Level.NORMAL;
        long sample=ages.get("rpm");
        if(proposed!=candidate){candidate=proposed;candidateFirst=sample;candidateSamples=0;lastSample=-1;}
        if(sample!=lastSample){candidateSamples++;lastSample=sample;}
        if(proposed==Level.NORMAL&&accepted==Level.NEUTRAL)accepted=Level.NORMAL;
        long delay=rank(proposed)>rank(accepted)?5000:2000;
        if(candidateSamples>=3&&sample-candidateFirst>=delay)accepted=proposed;
        if(accepted!=proposed){
            // Never keep a red/orange alarm on a newly normal sample while awaiting recovery.
            return new Result(Level.WAITING,"Stabilisation…","Évolution en cours · attendre plusieurs mesures cohérentes avant de conclure.");
        }
        if(accepted==Level.RED)return new Result(Level.RED,"Écart important","Écart persistant de plus de 300 tr/min par rapport à la référence, dans les conditions déclarées. Vérifier les compensations et les pistes ci-dessous.");
        if(accepted==Level.ORANGE)return new Result(Level.ORANGE,"À vérifier","Écart persistant au-delà de la marge PP406 de ±90 tr/min. Vérifier les conditions et comparer les autres mesures.");
        return new Result(Level.NORMAL,"Proche référence","Régime dans la marge PP406 autour de 650 tr/min. Ce résultat concerne seulement le ralenti ; il ne valide pas l’état de tout le moteur.");
    }
    private static int rank(Level value){return value==Level.RED?3:value==Level.ORANGE?2:value==Level.NORMAL?1:0;}
    public static Result neutral(String reason){return new Result(Level.NEUTRAL,"Non évalué",reason);}
    public static boolean v6Profile(String reference,String vin){
        return REFERENCE.equals(reference)&&vin!=null&&vin.matches("VF38CXFZ[A-HJ-NPR-Z0-9]{9}");
    }
    public static Result highRpm(String reference,String vin,Double rpm,boolean fresh){
        if(!fresh||rpm==null||Double.isNaN(rpm)||Double.isInfinite(rpm))return neutral("Attendre une mesure récente");
        if(!v6Profile(reference,vin))return neutral("Référence Peugeot V6 et VIN XFZ à confirmer");
        if(rpm<=0)return neutral("Moteur arrêté · comparaison du régime haut suspendue");
        if(rpm>=RPM_CUTOFF)return new Result(Level.RED,"Régime trop élevé","Repère de coupure Peugeot atteint ou dépassé. Réduire le régime. "+HIGH_RPM_POLICY);
        if(rpm>=RPM_CUTOFF*.9)return new Result(Level.ORANGE,"Régime élevé","Zone d’approche PP406 du repère de coupure Peugeot. "+HIGH_RPM_POLICY);
        return new Result(Level.NORMAL,"Sous la zone de régime haut","Régime sous la zone d’approche PP406 du repère Peugeot. Cela ne contrôle pas le ralenti ni les autres paramètres.");
    }
    public static Help help(String key){
        switch(key){
            case "rpm":return new Help("Vitesse de rotation du moteur. La consigne change à froid et avec certaines charges.",
                SOURCE+"\n"+SOURCE_URL+"\n\nValeurs constructeur : ralenti 650 tr/min ; coupure d’injection au-dessus de 6800 tr/min.\n\n"+HIGH_RPM_POLICY+"\n\n"+POLICY,
                "1. Confirmer la température, la pédale relâchée, la climatisation coupée et le volant au repos.\n2. Lire les défauts moteur avant toute intervention.\n3. Examiner l’admission et ses durites, le retour du papillon et la commande de ralenti.\n4. Recouper avec la tension d’alimentation, la température, la pression d’admission et les symptômes. Un ralenti élevé peut être une compensation normale de charge.");
            case "coolant_r_c":case "coolant_s_c":return new Help("Température du liquide de refroidissement rapportée par le calculateur moteur.",
                "Plage officielle de surveillance non encore vérifiée pour ce champ et cette variante. La fenêtre 80–105 °C sert uniquement à autoriser la comparaison du ralenti ; elle ne définit pas une température normale ni un seuil de surchauffe.",
                "1. Après un long arrêt à froid, comparer avec la température ambiante et l’air d’admission.\n2. Observer une montée progressive sans saut brutal.\n3. Recouper avec l’indication du tableau de bord et les défauts de sonde.\n4. À froid, contrôler le niveau, les fuites et les connexions. Ne pas ouvrir un circuit de refroidissement chaud.");
            case "air_r_c":case "air_s_c":return new Help("Température de l’air d’admission rapportée par le calculateur. Elle peut dépasser l’ambiante après un arrêt à chaud.",
                "Pas de plage officielle vérifiée pour cette variante. Aucune alerte de température d’air n’est activée.",
                "1. Comparer à l’ambiante après un refroidissement complet.\n2. Tenir compte de la chaleur du compartiment moteur au ralenti.\n3. Si la valeur saute ou reste figée, lire les défauts puis examiner le connecteur et le faisceau de la sonde.");
            case "battery_v":return new Help("Tension vue par le calculateur. Elle peut différer de celle mesurée directement à la batterie.",
                "Seuils constructeur de diagnostic non vérifiés pour cette variante. Le seuil de 13 V sert seulement à suspendre la comparaison du ralenti lorsque la compensation de charge pourrait intervenir.",
                "1. Comparer avec un multimètre aux bornes de la batterie, moteur arrêté puis tournant.\n2. Examiner les cosses, masses et connexions d’alimentation.\n3. Vérifier le circuit de charge si la tension baisse durablement. Ne pas débrancher la batterie moteur tournant.");
            case "map_mbar":return new Help("Pression absolue dans le collecteur. Elle dépend de la charge, du régime, de l’altitude et de l’ouverture du papillon.",
                "Repère constructeur MP7.0 : alimentation du capteur 5 V, signal électrique variable de 0 à 5 V. Ce signal n’est pas la pression affichée en mbar.\n\n"+MP70_COMPONENT_SOURCE+"\n\nPas de plage constructeur au ralenti vérifiée pour la 406. Une valeur observée sur une voiture ne devient pas une référence pour toutes les motorisations.",
                "1. Contact mis et moteur arrêté, comparer avec la pression atmosphérique locale absolue, sans correction au niveau de la mer.\n2. Au ralenti, vérifier que la pression réagit à l’ouverture du papillon.\n3. Examiner les durites, l’étanchéité d’admission et le connecteur du capteur. Une pression différente ne prouve pas à elle seule une prise d’air.");
            case "throttle_deg":case "throttle_mv":return new Help("Position ou tension du potentiomètre de papillon. Les deux unités ne s’interprètent pas avec la même échelle.",
                "Repère constructeur MP7.0 : alimentation 5 V ; signal variable de 0 à 5 V (0 à 5000 mV). Ce repère électrique ne définit pas la position normale pied levé ni une plage saine en degrés.\n\n"+MP70_COMPONENT_SOURCE+"\n\nPlage de diagnostic propre à cette conversion non encore vérifiée. Le seuil de 1° sert seulement à suspendre le contrôle du ralenti lorsque le papillon est ouvert.",
                "1. Moteur arrêté, vérifier un retour libre de la pédale et du papillon.\n2. Observer une évolution progressive du signal sans saut.\n3. Lire les défauts et examiner connecteur, faisceau et alimentation du capteur. Ne pas modifier la butée de papillon pour corriger une lecture.");
            case "speed_kmh":return new Help("Vitesse véhicule transmise au calculateur moteur.",
                "Repère constructeur MP7.0 : capteur à effet Hall, 5 impulsions par mètre et 8 par tour de capteur. Ces valeurs ne définissent pas une tolérance de vitesse affichée.\n\n"+MP70_COMPONENT_SOURCE+"\n\nLa cohérence à l’arrêt peut être observée, mais aucune plage officielle de diagnostic n’est intégrée. Le contrôle du ralenti exige une vitesse inférieure à 1 km/h.",
                "1. Voiture garée, vérifier la cohérence avec l’immobilité réelle.\n2. Si la valeur est incohérente, comparer avec le compteur et les autres calculateurs puis lire les défauts.\n3. Examiner le capteur et son faisceau selon l’équipement exact du véhicule.");
            case "injection_ms":return new Help("Durée d’ouverture commandée aux injecteurs. Elle varie notamment avec le régime, la charge, la température et la tension.",
                "Pas de plage officielle propre aux conditions de mesure vérifiée. Une coupure d’injection en décélération peut être normale.",
                "1. Recouper avec régime, pression d’admission, température et défauts moteur.\n2. Vérifier les symptômes et l’état des connexions.\n3. Si nécessaire, faire contrôler la pression de carburant et l’allumage avec la méthode adaptée. Cette valeur ne mesure pas le débit réel des injecteurs.");
            case "oxygen_mv":return new Help("Tension de la sonde à oxygène. À chaud en régulation, une sonde à bande étroite alterne entre mélange pauvre et riche.",
                "Repères constructeur MP7.0 : signal de 0 à 1 V (0 à 1000 mV), mélange pauvre autour de 0,1 V (100 mV), riche autour de 0,8 V (800 mV). La sonde fonctionne à chaud ; ce sont des états de richesse, pas des seuils de panne.\n\n"+MP70_COMPONENT_SOURCE+"\n\nUne mesure isolée proche d’un extrême n’est pas un défaut. La cadence actuelle ne garantit pas la capture des oscillations.\n\n"+LambdaSignalGuide.POLICY,
                "1. Vérifier que le moteur et la sonde sont chauds et que la régulation de richesse est active.\n2. Observer une série de mesures, en tenant compte des accélérations et coupures d’injection.\n3. Lire les défauts puis contrôler chauffage, connecteur, faisceau et éventuelles fuites d’échappement. Ne pas remplacer la sonde sur une seule valeur.");
            case "idle_duty_pct":return new Help("Commande de l’actuateur de ralenti, pas une mesure directe de l’ouverture ni du débit d’air.",
                "Pas de plage constructeur vérifiée pour ce rapport de commande et ces conditions.",
                "1. Comparer l’évolution de la commande avec le régime réel.\n2. Vérifier les charges qui peuvent modifier la consigne.\n3. En cas de ralenti instable, examiner l’admission, les durites et les connexions de l’actuateur, puis recouper avec les défauts.");
            case "canister_duty_pct":return new Help("Rapport de commande de la purge des vapeurs de carburant. La purge dépend des conditions de fonctionnement.",
                "Pas de plage constructeur de surveillance vérifiée. Une commande nulle n’est pas automatiquement un défaut.",
                "1. Recouper avec température, régime, charge et défauts de richesse.\n2. Examiner les durites de purge et les connexions de l’électrovanne.\n3. Une valeur de commande ne prouve pas que l’électrovanne s’ouvre réellement.");
            case "load_ms":return new Help("Champ de charge exprimé en millisecondes dans le relevé actuel. Il ne représente pas un pourcentage de charge normalisé.",
                "Signification et plage officielle à confirmer pour ce champ. Aucune alerte chiffrée n’est activée.",
                "Recouper avec le régime, la pression d’admission et le temps d’injection. Ne pas appliquer à ce champ des seuils de charge OBD exprimés en pourcentage.");
            default:return new Help("Paramètre non documenté.","Référence manquante.","Identifier le calculateur et la définition exacte du paramètre avant de l’interpréter.");
        }
    }
}
