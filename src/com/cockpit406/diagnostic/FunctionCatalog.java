package com.cockpit406.diagnostic;

/** UI catalogue only. An entry never authorizes or constructs a vehicle command. */
public final class FunctionCatalog {
    public static final String MP70_REFERENCE = "0261204407";
    public enum Presence { UNKNOWN, PRESENT, ABSENT }
    public enum Availability {
        AVAILABLE("Disponible"), IDENTIFICATION_REQUIRED("Calculateur à identifier"),
        ABSENT("Absent du véhicule"), UNSUPPORTED("Version non prise en charge"),
        NOT_IMPLEMENTED("Pas encore développé"), NOT_CONFIRMED("Compatibilité à vérifier"),
        CONDITIONS_REQUIRED("Contact mis · moteur arrêté · voiture garée"), FAULT_READ_REQUIRED("Lecture complète des défauts requise"), BUSY("Opération en cours");
        public final String label;
        Availability(String label) { this.label=label; }
    }
    public static final class Action {
        public final String id, title;
        public final boolean implemented, documented;
        Action(String id,String title,boolean implemented,boolean documented) {
            this.id=id; this.title=title; this.implemented=implemented; this.documented=documented;
        }
    }
    public static final class Group {
        public final String id,title,detail;
        public final Action[] actions;
        Group(String id,String title,String detail,Action...actions) {
            this.id=id; this.title=title; this.detail=detail; this.actions=actions;
        }
    }
    private static Action future(String id,String title) { return new Action(id,title,false,true); }
    private static Action uncertain(String id,String title) { return new Action(id,title,false,false); }
    private static Action motor(String id,String title) { return new Action(id,title,true,true); }
    private static Action reader(String id,String title) { return new Action(id,title,true,true); }
    public static final Group[] GROUPS = {
        new Group("motor","Moteur · diagnostic","Prise en charge actuelle : Bosch MP7.0 XFZ, référence 0261204407. Lectures indépendantes et accusé d’effacement observés ; contrôle sur véhicule après les derniers correctifs à faire.",
            motor("motor_values","Valeurs en direct"), motor("motor_faults","Lire les défauts et contextes"), motor("motor_clear","Effacer les défauts moteur…")),
        new Group("motor_tests","Moteur · tests d’actionneurs","Cinq commandes avec demande d’arrêt après une attente maximale PP406 de 5 s. Réponses attendues et effet physique à valider. Injecteurs et allumage : procédure carburant et reprise encore à établir.",
            future("injectors15","Injecteurs 1 / 5"),future("injectors26","Injecteurs 2 / 6"),future("injectors34","Injecteurs 3 / 4"),
            future("ignition15","Allumage 1 / 5"),future("ignition26","Allumage 2 / 6"),future("ignition34","Allumage 3 / 4"),
            reader("oxygen_heater","Chauffage de sonde à oxygène"),reader("fuel_pump","Pompe à essence"),reader("tach_signal","Signal compte-tours"),
            reader("idle_actuator","Commande de ralenti"),reader("canister","Purge canister")),
        new Group("cluster","Compteur · aiguilles et voyants","Tests de combiné trouvés pour d’autres versions de 406. Compatibilité du combiné branché à établir ; le test du signal compte-tours figure dans les tests moteur.",
            uncertain("cluster_values","Lire les informations du combiné"),uncertain("cluster_needles","Tester les aiguilles"),uncertain("cluster_lamps","Tester les voyants"),
            uncertain("cluster_buzzer","Tester l’avertisseur sonore"),uncertain("cluster_faults","Lire les défauts du combiné"),uncertain("cluster_clear","Effacer les défauts du combiné…"),
            uncertain("cluster_coding","Configurer le combiné…")),
        new Group("climate","Climatisation · périphériques","Valeo 9630231877. Tests séparés et retour au mode normal après arrêt ou erreur. Nouvelles séquences vérifiées en simulation ; réponses des commandes et effet physique à confirmer lors du premier essai.",
            reader("climate_values","Lire les groupes de climatisation"),reader("climate_faults","Lire les défauts (trames brutes)"),reader("climate_clear","Effacer les défauts…"),
            reader("climate_ac","Commande climatisation · 10 s"),reader("climate_ac_line","Ligne climatisation · 1 s"),
            reader("climate_blower","Pulseur · marche · 5 s"),reader("climate_blower_off","Pulseur · arrêt · 5 s"),
            reader("climate_mix","Mixage · 100 % · 10 s"),reader("climate_mix_cold","Mixage · 0 % · 10 s"),
            reader("climate_distribution","Distribution · 100 % · 10 s"),reader("climate_distribution_other","Distribution · 0 % · 10 s"),
            reader("climate_recycle","Air extérieur · 10 s"),reader("climate_recycle_inside","Recyclage intérieur · 10 s"),
            reader("climate_learn","Apprentissage des butées · 30 s…")),
        new Group("abs","ABS · freinage","Profils séparés Bosch 5 et 5.3. Lecture et effacement avec identifications concordantes, sauvegarde et relecture. L’effacement réinitialise la session ABS en conservant la liaison USB. Premiers essais physiques d’effacement à valider.",
            reader("abs_values","Valeurs et vitesses des roues"),reader("abs_faults","Lire les défauts"),reader("abs_clear","Effacer les défauts…"),
            uncertain("abs_routines","Procédures et tests selon variante…")),
        new Group("airbag","Airbags","Lecture autonome SAC_AKF 9628757180. Codes internes lus ; libellés et contextes à valider.",
            reader("airbag_identify","Identifier le calculateur"),reader("airbag_faults","Lire les défauts"),reader("airbag_clear","Effacer les défauts…"),
            future("airbag_config","Configurer les équipements…")),
        new Group("steering","Direction assistée","Lecture autonome ZF 9629846780 : identification et défauts. Mémoires et réglages à développer.",
            reader("steering_values","Lire l’identification"),reader("steering_faults","Lire les défauts"),reader("steering_clear","Effacer les défauts…"),
            future("steering_read_config","Lire la mémoire de configuration"),future("steering_write_config","Écrire la configuration…")),
        new Group("keys","Clés · antidémarrage · télécommande","Lecture autonome Valeo 9624799080 : états, nombre de clés et défauts. Apprentissage et télécommande à développer séparément.",
            reader("keys_identify","Identifier l’antidémarrage"),reader("keys_values","Tous les états des clés"),
            reader("keys_configuration_values","Configuration antidémarrage"),reader("keys_inputs_values","État des entrées"),
            reader("keys_engine_values","État calculateur moteur"),reader("keys_faults","Lire les défauts"),
            uncertain("keys_clear","Effacer les défauts…"),future("keys_learn","Apprendre les clés de démarrage…"),future("keys_replace","Procédures de remplacement…"),
            future("keys_config","Configurer l’antidémarrage…"),uncertain("remote_sync","Synchroniser les télécommandes…")),
        new Group("seat","Siège électrique · rétroviseurs","Lectures Bitron : deux groupes, positions internes et quarante bits de défauts. Effacement distinct, avec arrêt de demande et relecture. Aucun mouvement commandé par ces lectures. Formats et premier effacement physique à valider.",
            reader("seat_values","Lire les entrées et sorties"),reader("seat_faults","Lire les défauts"),reader("seat_clear","Effacer les défauts…"),
            future("seat_motors","Tester les moteurs du siège"),future("mirror_motors","Tester les moteurs des rétroviseurs")),
        new Group("locking","Verrouillage · supercondamnation","Lectures Kiekert intégrées : réponse de défauts et quinze compteurs. Ce profil ne renvoie pas de référence individuelle ; présence et formats à confirmer sur véhicule. Aucune synchronisation de télécommande déduite de ces lectures.",
            reader("lock_faults","Lire les défauts"),reader("lock_counters","Lire les compteurs de défauts"),future("lock_clear","Effacer les défauts…")),
        new Group("suspension","Suspension pilotée","Lecture Denso : identification, paramètres et défauts. Effacement distinct avec sauvegarde, accusé et relecture. Conversions physiques et premier effacement sur véhicule à valider.",
            reader("susp_values","Valeurs de suspension"),reader("susp_faults","Lire les défauts"),reader("susp_clear","Effacer les défauts…"),
            future("susp_test","Tester les états souple / ferme"),future("susp_config","Configurer la suspension…")),
        new Group("automatic","Boîte automatique · adaptations","Lectures et effacements ZF 4HP20 / AL4 séparés pour les véhicules équipés. AL4 : paramètre d’effacement fixe et attente de fin avant relecture ; aucune remise à zéro des adaptatifs. Premiers essais physiques d’effacement à valider.",
            reader("auto_values","Valeurs de boîte"),reader("auto_faults","Lire les défauts"),reader("auto_clear","Effacer les défauts…"),
            future("auto_adapt","Initialiser les adaptatifs…"),future("auto_learn","Apprentissages de boîte…"),future("auto_program","Programmation de boîte…")),
        new Group("programming","Cartographie · programmation · apprentissages","Un profil de programmation MP7.0 existe. Extraction complète et adaptation moteur non établies ; aucune écriture moteur disponible.",
            uncertain("map_read","Lire et sauvegarder la cartographie"),future("map_write","Programmer le calculateur moteur…"),
            uncertain("motor_adapt","Apprentissages et adaptations moteur…"))
    };
    public static Availability evaluate(Action action,Presence presence,boolean sessionReady,String reference,
                                         boolean engineOffFresh,boolean busy) {
        if(presence==Presence.ABSENT) return Availability.ABSENT;
        if(!action.implemented) return action.documented?Availability.NOT_IMPLEMENTED:Availability.NOT_CONFIRMED;
        if(!sessionReady || presence!=Presence.PRESENT) return Availability.IDENTIFICATION_REQUIRED;
        if(!referenceMatches(action,reference)) return Availability.UNSUPPORTED;
        if(busy) return Availability.BUSY;
        if((action.id.equals("motor_clear")||MotorOperations.plan(action.id)!=null) && !engineOffFresh) return Availability.CONDITIONS_REQUIRED;
        return Availability.AVAILABLE;
    }
    public static String module(Action action) {
        for(Group group:GROUPS) for(Action candidate:group.actions) if(candidate.id.equals(action.id)) return module(group);
        return "unknown";
    }
    public static boolean referenceMatches(Action action,String reference) {
        if(reference==null)return false;
        String module=module(action);
        if(module.equals("seat")&&reference.equals("profile:seat183"))return action.id.equals("seat_values")||action.id.equals("seat_faults");
        if(module.equals("abs"))return reference.equals("profile:abs5")||reference.equals("profile:abs53");
        if(module.equals("automatic"))return reference.equals("profile:auto4hp20")||reference.equals("profile:autoal4");
        return expectedReference(action).equals(reference);
    }
    /** Only reads supplied by the generic reader can be enabled through it. */
    public static Availability genericReadAvailability(Action action,boolean ready,boolean hasGroups,boolean busy){
        if(!action.implemented)return action.documented?Availability.NOT_IMPLEMENTED:Availability.NOT_CONFIRMED;
        boolean supported=action.id.endsWith("_faults")||action.id.endsWith("_identify")||(hasGroups&&action.id.endsWith("_values"));
        if(!supported)return Availability.UNSUPPORTED;
        if(!ready)return Availability.IDENTIFICATION_REQUIRED;
        return busy?Availability.BUSY:Availability.AVAILABLE;
    }
    public static String expectedReference(Action action) {
        switch(module(action)) {
            case "abs":return "profile:abs5";
            case "seat":return "profile:seat";
            case "locking":return "profile:locking";
            case "suspension":return "profile:suspension";
            case "automatic":return "profile:auto4hp20";
            case "climate":return "9630231877";
            case "airbag":return "9628757180";
            case "steering":return "9629846780";
            case "keys":return "9624799080";
            default:return MP70_REFERENCE;
        }
    }
    public static String module(Group group) {
        return group.id.equals("motor_tests")||group.id.equals("programming")?"motor":group.id;
    }
    /** Values, faults, actuator tests, learning, configuration, map/programming. */
    public static int tab(Action action) {
        String id=action.id;
        if(id.endsWith("_faults")||id.endsWith("_clear")||id.equals("lock_counters")) return 1;
        switch(id) {
            case "injectors15": case "injectors26": case "injectors34":
            case "ignition15": case "ignition26": case "ignition34":
            case "oxygen_heater": case "fuel_pump": case "tach_signal": case "idle_actuator": case "canister":
            case "cluster_needles": case "cluster_lamps": case "cluster_buzzer":
            case "climate_ac": case "climate_ac_line": case "climate_blower": case "climate_blower_off":
            case "climate_mix": case "climate_mix_cold": case "climate_distribution": case "climate_distribution_other":
            case "climate_recycle": case "climate_recycle_inside":
            case "abs_routines": case "seat_motors": case "mirror_motors": case "susp_test": return 2;
            case "climate_learn": case "keys_learn": case "keys_replace": case "remote_sync": case "auto_adapt": case "auto_learn": case "motor_adapt": return 3;
            case "cluster_coding": case "airbag_config": case "steering_read_config": case "steering_write_config":
            case "keys_config": case "susp_config": return 4;
            case "map_read": case "map_write": case "auto_program": return 5;
            default: return 0;
        }
    }
    private FunctionCatalog() {}
}
