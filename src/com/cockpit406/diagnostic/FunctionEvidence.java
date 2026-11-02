package com.cockpit406.diagnostic;

/** Per-operation findings for the 52 paths missing in 0.7.0.
 * This information grants no permission and constructs no ECU request.
 */
public final class FunctionEvidence {
    public static final class Entry {
        public final String finding,remaining;
        Entry(String finding,String remaining){this.finding=finding;this.remaining=remaining;}
    }
    private static Entry entry(String finding,String remaining){return new Entry(finding,remaining);}
    public static Entry get(String id) {
        switch(id) {
            case "oxygen_heater":case "fuel_pump":case "tach_signal":case "idle_actuator":case "canister":
                return entry("Commande fixe, demande d’arrêt et relecture des défauts intégrées. Aucun renouvellement automatique.",
                    "Premier essai sur voiture : vérifier les réponses d’activation et d’arrêt, l’effet réel et le retour aux lectures. L’attente PP406 de 5 s après réponse ne garantit pas la durée physique.");
            case "abs_values":
                return entry("Deux profils de lecture séparés, Bosch 5 et 5.3, et conversions distinctes des quatre vitesses de roues intégrés.",
                    "Identifier la variante réellement montée, contrôler les trames et comparer les vitesses à une mesure indépendante. Aucun essai de freinage commandé.");
            case "abs_faults":
                return entry("Lecture des réponses de défauts intégrée pour Bosch 5 et 5.3 ; octets conservés.",
                    "Valider la variante, le décodage des codes et les réponses avec et sans défaut. Une trame brute ne signifie pas absence de défaut.");
            case "seat_values":
                return entry("Deux groupes Bitron intégrés ; quatre positions de capteurs affichées en unités internes.",
                    "Confirmer présence, identité et formats ; comparer chaque position aux mouvements manuels du siège. Aucune commande de moteur envoyée.");
            case "seat_faults":
                return entry("Lecture de la réponse Bitron intégrée.","Valider son format et le décodage de la mémoire de défauts sur un calculateur présent.");
            case "lock_faults":
                return entry("Lecture Kiekert intégrée sans ordre d’effacement ni ordre de fin ambigu.",
                    "Valider la présence et le décodage ; couper le contact avant de changer de module. La commande de fin disponible émet les mêmes octets que l’effacement et reste exclue.");
            case "lock_counters":
                return entry("Lecture des quinze compteurs Kiekert intégrée ; valeurs non renseignées distinguées.",
                    "Confirmer format, interprétation et conservation des compteurs après lecture. Couper le contact avant de changer de module.");
            case "susp_values":
                return entry("Lecture Denso intégrée, avec vitesse calculée et états internes.",
                    "Confirmer présence, identité, format et conversion de vitesse sur un véhicule équipé.");
            case "susp_faults":
                return entry("Lecture de la réponse de défauts Denso intégrée.","Valider codes, nombre de défauts et réponse sans défaut ; aucun effacement autorisé par cette lecture brute.");
            case "auto_values":
                return entry("Profils distincts ZF 4HP20 et AL4 intégrés, trois groupes par variante. Choix explicite à la connexion.",
                    "Essai requis sur un véhicule automatique : identité, formats, conversions et reprise. Ces profils ne rendent pas une boîte automatique présente sur une voiture manuelle.");
            case "auto_faults":
                return entry("Réponses de défauts 4HP20 et AL4 lues séparément et conservées.",
                    "Valider la variante et son décodage ; ne pas interpréter un refus ou une trame inconnue comme une liste vide.");
            case "injectors15":case "injectors26":case "injectors34":
                return entry("Les trois commandes d’injecteurs et leurs paramètres supplémentaires sont repérés ; aucun envoi intégré.",
                    "Établir la durée et le nombre d’impulsions, la procédure exacte d’isolement du carburant, la pression résiduelle, l’arrêt et la remise en service. Débrancher seulement la pompe ne prouve pas l’absence de carburant dans la rampe.");
            case "ignition15":case "ignition26":case "ignition34":
                return entry("Les trois commandes d’allumage et leur paramètre supplémentaire sont repérés ; aucun envoi intégré.",
                    "Établir durée, matériel de contrôle et procédure constructeur de préparation/reprise. Aucun enchaînement automatique après un test injecteur ; démarrer le moteur ne constitue pas une autorisation de test.");
            case "cluster_values":case "cluster_needles":case "cluster_lamps":case "cluster_buzzer":case "cluster_faults":case "cluster_clear":case "cluster_coding":
                return entry("Profils de combinés trouvés pour d’autres branches 406 multiplexées ; aucune correspondance établie pour le combiné de ce véhicule.",
                    "Identifier le combiné et sa liaison avant de porter ses fonctions. Le test du signal compte-tours par le moteur est distinct et disponible dans Tests moteur.");
            case "abs_clear":
                return entry("Effacements Bosch 5 et 5.3 intégrés séparément : identifications concordantes, sauvegarde, envoi unique, relecture, fin puis réinitialisation et contrôle final sans fermer l’USB.",
                    "Effectuer les premiers essais physiques d’effacement sur chaque variante. La relecture immédiate et les interruptions sont vérifiées en simulation ; ne pas confondre ces essais avec une validation sur voiture.");
            case "abs_routines":
                return entry("Des procédures de roues, pompe et purge sont référencées, avec chargement et exécution de routines.",
                    "Retrouver les programmes de routine exacts, leurs conditions, réponses et arrêt. Une simple commande de test ne remplace pas ces programmes.");
            case "airbag_config":
                return entry("Deux configurations fixes et une commande après-vente sont référencées ; leur correspondance aux équipements n’est pas établie.",
                    "Identifier la signification des options, lire la configuration actuelle et définir la vérification après écriture avant toute activation.");
            case "steering_read_config":
                return entry("Des commandes de lecture RAM et EEPROM existent, avec adresse en paramètre.",
                    "Établir la carte mémoire et la réponse pour cette référence ZF ; aucune lecture d’adresse arbitraire n’est exposée.");
            case "steering_write_config":
                return entry("Une commande EEPROM existe avec adresse et valeur en paramètres.",
                    "Établir les champs autorisés, sauvegarde, règles de validation et relecture. L’existence d’une écriture mémoire ne donne pas la signification d’un réglage.");
            case "keys_clear":
                return entry("L’intitulé effacement partage sa commande avec une remise à zéro usine dans cette famille.",
                    "Distinguer leur effet exact sur la référence Valeo avant tout envoi ; préserver les clés et l’appairage existants.");
            case "keys_learn":
                return entry("Pour le boîtier Valeo 9624799080, la saisie d’un code et sa réponse de refus ont été observées dans PP2000. L’encodage et le refus sont préparés et testés hors véhicule ; ils ne sont pas envoyés depuis PP406. Les états et le nombre de clés restent lisibles.",
                    "Valider les conditions d’accès et une réponse de code accepté, puis reconstituer et vérifier les étapes de début, présentation de toutes les clés, fin et reprise après interruption. Le code confidentiel n’est pas lu automatiquement ; disposer de toutes les clés à conserver.");
            case "keys_replace":
                return entry("Les procédures de remplacement moteur et boîtier antidémarrage sont distinctes de l’apprentissage des clés.",
                    "Identifier l’élément remplacé, son état neuf/appairé, les prérequis et l’ordre des confirmations. Aucun remplacement ni déverrouillage implicite.");
            case "keys_config":
                return entry("Une commande de configuration du boîtier est référencée avec paramètre variable.",
                    "Établir la signification des valeurs et une lecture de contrôle. Ne pas envoyer une valeur déduite d’un autre boîtier.");
            case "remote_sync":
                return entry("La documentation Peugeot distingue télécommande et transpondeur ; elle indique aucune synchronisation pour la ligne 406 D8 depuis DAM 7014.",
                    "Confirmer le boîtier et la télécommande réellement montés. Ne pas appliquer une procédure BSI de 406 multiplexée à ce système.");
            case "seat_clear":
                return entry("Effacement Bitron intégré avec identifications concordantes, sauvegarde des quarante bits de défauts, envoi unique, arrêt de demande E1 puis relecture.",
                    "Valider physiquement la séquence sur un siège Bitron équipé. L’état vide se lit dans les bits de présence ; des bits de statut seuls ne constituent pas un défaut présent.");
            case "seat_motors":case "mirror_motors":
                return entry("Sélecteurs des quatre axes de siège et quatre axes de rétroviseurs repérés, avec début d’activation et arrêt.",
                    "Établir durée, arrêt sur butée/obstacle, retour au mode normal et vérification de position. Présence du siège à mémoire à confirmer.");
            case "lock_clear":
                return entry("La procédure lit compteurs et défauts avec temporisations ; l’ordre final partage ses octets avec la fin de diagnostic.",
                    "Valider conservation/effacement des compteurs et portée de cet ordre sur le boîtier présent, puis établir une relecture fiable.");
            case "susp_clear":
                return entry("Effacement Denso intégré avec identifications concordantes, sauvegarde complète, commande 2D/09, accusé C3 et relecture sans quitter la session.",
                    "Effectuer le premier essai physique sur la suspension équipée. Aucun test souple/ferme ni changement de loi n’est déduit de cette commande d’effacement.");
            case "susp_test":
                return entry("Trois procédures Denso repérées : ferme, souple, alternance de quinze cycles avec temporisations de deux secondes.",
                    "Déterminer l’état initial, les conditions d’essai et le retour confirmé au mode normal, y compris après interruption.");
            case "susp_config":
                return entry("Trois lois de suspension sont référencées par des commandes distinctes.",
                    "Associer chaque loi à l’équipement, sauvegarder l’état courant et vérifier le résultat avant toute écriture.");
            case "auto_clear":
                return entry("Effacements 4HP20 et AL4 intégrés séparément. AL4 : paramètres fixes FF00, accusé 54FF00, attente de 1 s puis relecture. Aucun autre paramètre de remise à zéro n’est autorisé.",
                    "Valider sur chaque boîte les réponses et la relecture. Les adaptatifs, apprentissages et compteurs restent des opérations distinctes, sans activation implicite.");
            case "auto_adapt":
                return entry("Initialisation des adaptatifs 4HP20 et initialisations pression/temporisation AL4 repérées ; effets différents.",
                    "Établir conditions de température, position de levier, étapes, sauvegarde et critères de réussite sur la variante équipée.");
            case "auto_learn":
                return entry("Une commande d’apprentissage AL4 existe ; aucune équivalence 4HP20 démontrée.",
                    "Reconstituer le déroulé complet et les contrôles. Ne pas transformer une remise à zéro en apprentissage annoncé réussi.");
            case "auto_program":
                return entry("Sessions et commandes de programmation sont référencées, distinctes des lectures de boîte.",
                    "Établir fichiers exacts, alimentation stabilisée, contrôles d’intégrité, reprise et vérification ; aucun fichier ni transfert de programmation autorisé actuellement.");
            case "map_read":
                return entry("Le profil de programmation MP7.0 est repéré ; il ne fournit pas de commande identifiée d’extraction complète de la mémoire.",
                    "Établir une méthode de lecture pour la référence 0261204407 et vérifier deux lectures identiques. Un fichier constructeur de téléchargement n’est pas une sauvegarde de cette voiture.");
            case "map_write":
                return entry("Les échanges de programmation MP7.0 sont distincts des échanges de diagnostic.",
                    "Établir image exacte, zones et contrôles d’intégrité, alimentation stabilisée et récupération après interruption. Aucune écriture n’est intégrée.");
            case "motor_adapt":
                return entry("Une procédure Peugeot d’initialisation MP7.0 a été trouvée ; elle combine diagnostic et phases de fonctionnement moteur.",
                    "Confirmer son application à cette référence et définir le suivi entre coupures du contact, critères de réussite et contrôle final. Aucun ordre fictif de remise à zéro n’est envoyé.");
            default:return null;
        }
    }
    private FunctionEvidence() {}
}
