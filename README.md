# PP406

PP406 est une application Android de diagnostic pour les Peugeot 406 Coupé, développée par **Novalys Apps**. Le projet vise les différentes architectures de la voiture : non multiplexée, semi-multiplexée et multiplexée. La couverture dépend de la motorisation, du calculateur et de sa référence ; plusieurs fonctions restent en développement ou à valider sur véhicule.

## État de cette publication

Cette première publication contient le code Java de la version 0.12.55, les ressources Android et des tests de développement. Elle ne contient **ni clé de signature, ni relevés privés, ni tables de communication et catalogues Peugeot issus de PP2000**. Ces données sont nécessaires à une partie de l'application : une compilation de ce dépôt ne suffit donc pas à obtenir une application de diagnostic complète. Elle ne remplace pas la version distribuée par Novalys Apps.

La lecture BSI Siemens D9 est expérimentale et n'a pas encore été validée physiquement. Les tests simulés ou la présence d'un menu ne garantissent pas le fonctionnement d'une commande sur un véhicule. Ne pas utiliser un build de développement pour des opérations d'écriture, de codage ou de sécurité sur une voiture sans validation adaptée.

## Code

- `src/` : application Android et protocoles.
- `res/` : ressources et graphismes.
- `research-tests/` : tests de développement ; certains nécessitent les jeux de données locaux non publiés et ne sont pas lancés automatiquement par Gradle.
- `licenses/` : licences des références tierces conservées pour les traitements correspondants.

## Compilation de développement

Le projet utilise le JDK 17, le SDK Android 36, Gradle 8.14 et Android Gradle Plugin 8.13. Lancez `./gradlew assembleDebug` (ou `gradlew.bat assembleDebug` sous Windows) pour compiler un APK de développement : les données de diagnostic manquantes doivent être ajoutées séparément et légalement. La signature de développement est différente de celle de l'application publiée ; ne désinstallez pas l'application installée pour contourner une incompatibilité de signature.

## Droits

© Novalys Apps. La mise à disposition publique du code ne constitue pas en elle-même une licence de réutilisation. Les noms et marques Peugeot, Citroën, Lexia et PP2000 appartiennent à leurs titulaires respectifs. Les références tierces identifiées sont accompagnées de leurs licences dans `licenses/`.
