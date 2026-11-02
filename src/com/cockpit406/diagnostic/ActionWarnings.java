package com.cockpit406.diagnostic;

/** Precautions are information, never an authorization to send an ECU command. */
public final class ActionWarnings {
    public static final class Warning {
        public final String title, summary, precautions;
        Warning(String title,String summary,String precautions) {
            this.title=title; this.summary=summary; this.precautions=precautions;
        }
    }
    private static final String COMMON="Voiture immobilisée, frein de stationnement serré, zone ventilée. Tenir les personnes et objets à l’écart des pièces commandées. Respecter la procédure du calculateur identifié, sa durée et son arrêt. Une confirmation ne remplace pas ces conditions.";
    private static final Warning INJECTORS=new Warning("Injecteurs · carburant et risque d’incendie",
        "Une activation peut injecter de l’essence moteur arrêté. Retirer le fusible de pompe ne garantit pas la disparition de la pression résiduelle.",
        "Ne pas improviser le débranchement de la pompe, le choix d’un fusible ou une dépressurisation. Ne pas enchaîner avec un test d’allumage ni un démarrage pour évacuer le carburant : la remise en état doit suivre la procédure constructeur. Éviter les répétitions susceptibles d’accumuler du carburant. Arrêter en cas de fuite ou d’odeur d’essence.\n\nLa procédure MP7.0 (isolement du carburant, durée et conditions de reprise) n’est pas encore validée : ce test reste indisponible.");
    private static final Warning IGNITION=new Warning("Allumage · haute tension et étincelles",
        "Risque de choc électrique et d’inflammation du carburant. Ne pas toucher les bobines, fils ou bougies pendant une activation.",
        "Ne pas débrancher la haute tension pendant le test. Ne pas créer d’étincelle à l’air libre. Ne pas enchaîner après une activation d’injecteurs : un simple délai ou un démarrage intermédiaire ne constitue pas une validation. La mise hors service de l’injection et la remise en état doivent être définies pour le calculateur exact.\n\nLa procédure MP7.0 et les conditions de reprise ne sont pas encore validées : ce test reste indisponible.");
    public static Warning forAction(String id) {
        if(id.startsWith("injectors")) return INJECTORS;
        if(id.startsWith("ignition")) return IGNITION;
        MotorOperations.Plan motor=MotorOperations.plan(id);
        if(motor!=null)return new Warning(motor.title+" · essai ponctuel",
            id.equals("fuel_pump")?"Le circuit sera mis sous pression : aucune fuite, odeur d’essence, flamme ou étincelle ne doit être présente.":id.equals("oxygen_heater")?"La sonde et l’échappement peuvent devenir brûlants.":"Une sortie moteur sera commandée une seule fois ; vérifier son effet sans intervenir sur le mécanisme.",
            "Voiture garée, contact mis, moteur arrêté. Les mesures 0 tr/min et 0 km/h seront relues avec les défauts avant l’envoi. Ne pas démarrer pendant l’opération. Maintenir l’alimentation et le câble jusqu’à la fin.\n\nPP406 ne répète pas la commande. Après sa réponse, l’attente est plafonnée à "+(motor.durationMs/1000)+" s avant une demande d’arrêt explicite ; ce plafond logiciel n’est pas une durée constructeur ni une garantie d’arrêt en cas de câble débranché. Une commande USB bloquée peut aussi retarder la demande d’arrêt.\n\nRéponses d’activation et d’arrêt encore à confirmer sur véhicule. Une réponse inconnue interrompt la séquence et conserve le journal. La fin de séquence ne prouve pas le bon fonctionnement mécanique. Aucun test d’injection ou d’allumage n’est enchaîné.");
        ClimateOperations.Plan climate=ClimateOperations.plan(id);
        if(climate!=null) return new Warning(climate.title+" · "+(climate.durationMs/1000)+" secondes",
            climate.learning?"Les volets vont parcourir leurs butées. L’apprentissage peut modifier leurs réglages.":"Une commande électrique ou un volet sera actionné. Vérifier son effet sur le véhicule.",
            "Voiture garée, contact mis, moteur arrêté. Dégager le pulseur et les mécanismes de volets ; ne pas intervenir dans leur course. Batterie suffisamment chargée, Lexia et contact maintenus jusqu’à la fin. Ne pas démarrer pendant le test.\n\n"
            +(climate.learning?"L’attente de 30 secondes reste obligatoire si tu touches Arrêter après l’envoi. Le mode normal sera ensuite demandé ; ne pas débrancher pour interrompre la procédure.":"Arrêter demande la désactivation prévue puis le retour au mode normal. Une coupure de câble empêche de confirmer ce retour.")
            +"\n\nPremière validation physique à faire : si la réponse du calculateur n’est pas reconnue, la séquence s’arrête et le journal conserve la réponse. Aucun résultat mécanique n’est déduit du seul envoi USB.");
        switch(id) {
            case "fuel_pump": return new Warning("Pompe à essence · carburant sous pression","La pompe peut pressuriser le circuit. Arrêter en présence de fuite ou d’odeur d’essence.","Aucune flamme, cigarette ou source d’étincelles. Ne pas ouvrir le circuit de carburant. La durée et l’arrêt du test doivent être validés avant activation.");
            case "oxygen_heater": return new Warning("Sonde à oxygène · éléments chauds","Le chauffage peut rendre la sonde et son environnement brûlants.","Ne pas toucher la sonde ni l’échappement. Éloigner les matériaux inflammables. Durée et conditions électriques à valider.");
            case "seat_motors": case "mirror_motors": return new Warning("Moteurs électriques · pincement","Le siège ou le rétroviseur peut se déplacer sans commande manuelle.","Dégager la course et les butées ; personne ni objet dans la zone de déplacement. Prévoir l’arrêt avant de lancer le test.");
            case "abs_routines": return new Warning("ABS · circuit de freinage","Une routine peut commander la pompe et les électrovannes de freinage.","Procédure et variante ABS exactes obligatoires. Aucun essai en circulation. Ne pas improviser une purge ou une remise à zéro.");
            case "keys_learn": case "keys_replace": case "keys_config": return new Warning("Antidémarrage · risque de non-démarrage","Un apprentissage ou une configuration peut modifier les clés autorisées.","Identifier le module, disposer du code propriétaire et des clés requises, puis suivre la séquence complète et sa reprise après interruption. Aucun essai de code au hasard.");
            case "airbag_config": return new Warning("Airbags · configuration de sécurité","Un mauvais paramétrage peut rendre la protection inadaptée aux équipements du véhicule.","Variante, équipements et procédure constructeur à confirmer. Aucune commande de mise à feu n’est proposée.");
            case "map_write": case "auto_program": return new Warning("Programmation · risque d’immobilisation","Une interruption ou un fichier incompatible peut rendre le calculateur inutilisable.","Références exactes, image vérifiée, alimentation stabilisée et procédure de récupération requises. Aucune programmation exploratoire.");
            case "auto_adapt": case "auto_learn": case "motor_adapt": return new Warning("Apprentissage · réglages du calculateur","La fonction peut modifier les réglages et nécessiter une procédure de réapprentissage complète.","Établir les conditions, les valeurs modifiées et la reprise après interruption avant toute exécution.");
            case "steering_write_config": case "susp_config": case "cluster_coding": return new Warning("Configuration · comportement du véhicule","Une valeur incorrecte peut modifier le fonctionnement du système.","Lire et sauvegarder la configuration d’origine, vérifier les équipements et la procédure de restauration avant toute écriture.");
            default: return null;
        }
    }
    public static String fullText(Warning warning) { return warning.summary+"\n\n"+warning.precautions+"\n\n"+COMMON; }
    /** Availability is always checked again after any dialog; an acknowledgement never enables an unavailable action. */
    public static boolean mayDispatch(FunctionCatalog.Availability availability,String id,boolean acknowledged) {
        return availability==FunctionCatalog.Availability.AVAILABLE && (forAction(id)==null || acknowledged);
    }
    private ActionWarnings() {}
}
