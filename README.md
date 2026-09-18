<p align="center">
  <img src="docs/logo.svg" alt="Logo Make It Run" width="128" height="128">
</p>

# Make It Run

Application Android qui pilote un tapis de course Bluetooth compatible **FTMS** (_Fitness Machine
Service_) pour y dérouler des entraînements fractionnés automatiquement.

Vous composez une séance dans l'application (échauffement, blocs répétés, récupération, retour au
calme), vous la lancez, et Make It Run envoie la vitesse et la pente au tapis étape par étape. Plus
besoin de surveiller le chrono ni de régler la machine à la main entre deux fractions.

> **[Guide d'utilisation](docs/guide-utilisateur.md)** — comment connecter son tapis, créer sa
> première séance et la courir, expliqué pas à pas.

## Fonctionnalités

- **Connexion Bluetooth Low Energy** à un tapis FTMS : détection des machines à portée, connexion,
  lecture des capacités déclarées (plage de vitesse, pas, pente pilotable ou non) et reconnexion au
  dernier tapis utilisé.
- **Éditeur d'entraînements** : étapes typées (échauffement, effort, récupération, retour au calme),
  fin d'étape au temps ou à la distance, allure/vitesse et inclinaison imposées ou libres, blocs
  répétés pour le fractionné, avec la possibilité d'en sauter la dernière étape à la dernière
  répétition.
- **Étapes pilotées par la fréquence cardiaque** : plutôt qu'une vitesse, l'étape vise une zone de
  battements et l'application cherche elle-même l'allure qui vous y maintient, par petits pas
  espacés pour laisser le cœur répondre. La franchise de correction se règle globalement et se
  surcharge étape par étape.
- **Capteur cardiaque Bluetooth** : ceinture pectorale ou montre diffusant sa fréquence, avec
  reconnexion automatique et repli sûr quand le signal se tait.
- **Exécution pilotée** : décompte de départ, consigne envoyée automatiquement à chaque changement
  d'étape et renvoyée si la machine l'ignore, temps et distance restants, étape suivante annoncée,
  pause, saut d'étape et arrêt.
- **Suivi du tapis** : arrêter la courroie depuis la console de la machine met la séance en pause, et
  la relancer la fait repartir. Toute la séance peut se mener depuis les boutons du tapis.
- **Séance en arrière-plan** : un service de premier plan maintient l'entraînement en vie et affiche
  sa progression dans une notification, même si l'écran est quitté ou verrouillé. L'écran de séance
  est verrouillé tant qu'elle dure, et le tapis est arrêté si l'application est fermée.
- **Historique et rapport de séance** : un relevé par seconde, un graphe superposant vitesse et
  fréquence cardiaque, le détail étape par étape comparant le prévu au réalisé, et les compteurs
  affichés par le tapis.
- **Reprise d'une séance interrompue** : dans les trente minutes, une séance arrêtée en cours de
  route repart à l'étape où elle s'était arrêtée et prolonge l'enregistrement d'origine.
- **Mesures en direct** : vitesse, allure, distance, temps, inclinaison, fréquence cardiaque et
  dépense énergétique telles que remontées par le tapis.
- **Sauvegarde** : export et import des entraînements et du profil du tapis dans un fichier que vous
  conservez vous-même.
- **Onglet Debug** : pilotage manuel de la machine, capacités déclarées et dernière trame FTMS reçue,
  utile pour comparer le décodage avec la spécification.

L'application est disponible en français et en anglais. Les données sont stockées localement (Room et
DataStore) ; l'application ne communique avec aucun serveur.

## Avertissement

Ce projet est un travail personnel, fourni **tel quel et sans aucune garantie** (voir la
[licence](LICENSE)). Il commande à distance une machine en mouvement sur laquelle vous courez :
gardez toujours le cordon de sécurité du tapis à portée, restez capable d'arrêter la machine depuis
son propre panneau, et vérifiez vos consignes de vitesse et de pente avant de lancer une séance.
L'auteur ne saurait être tenu responsable d'un dommage corporel ou matériel lié à l'utilisation de
cette application.

Make It Run n'est affilié à aucun fabricant de tapis de course. Le comportement dépend de ce que la
machine implémente réellement du protocole FTMS : certains tapis refusent la prise de contrôle, la
consigne de pente, ou n'exposent qu'une partie des mesures.

## Prérequis

- Android 8.0 (API 26) ou supérieur, avec Bluetooth Low Energy.
- Un tapis de course exposant le service Bluetooth **FTMS** (UUID `0x1826`).
- Facultatif : un capteur de fréquence cardiaque Bluetooth, nécessaire seulement pour les étapes qui
  visent une zone de fréquence.
- Côté développement : JDK 17 ou plus récent et Android Studio récent. Le projet utilise Gradle 9.5,
  AGP 9.3, Kotlin 2.4 et compile contre le SDK 37.

## Installation

Les versions publiées sont disponibles dans les [releases](../../releases) du dépôt, sous forme
d'APK signé. Téléchargez le fichier `.apk` et ouvrez-le sur le téléphone ; Android demandera
l'autorisation d'installer une application issue d'une source inconnue, ce qui est normal pour une
distribution hors Play Store.

## Compiler et lancer

```bash
# Compiler la variante debug
./gradlew assembleDebug

# Installer sur un appareil connecté en ADB
./gradlew installDebug

# Tests unitaires (logique d'entraînement, historique, décodage FTMS et cardio)
./gradlew test
```

La logique métier est couverte par des tests unitaires JVM : décodage des trames FTMS et cardio,
déroulement d'un plan d'entraînement, progression d'étape, régulation par fréquence cardiaque,
enregistrement et relecture d'une séance, reprise d'une séance interrompue, sauvegarde et formatage.
Tout ce qui touche au Bluetooth demande en revanche un vrai tapis : l'émulateur ne permet pas de
tester la connexion.

## Publication

Un workflow GitHub Actions construit, signe et publie l'APK lorsqu'un tag `v*` est poussé. Il refuse
un tag qui ne correspond pas à la version déclarée dans `gradle.properties`, et le matériel de
signature est reconstitué depuis les secrets du dépôt puis effacé en fin de course.

## Structure du projet

| Module       | Rôle                                                                                      |
| ------------ | ----------------------------------------------------------------------------------------- |
| `:app`       | Interface Compose, modèle d'entraînement, persistance, moteur de séance et historique.     |
| `:ftms`      | Couche Bluetooth du tapis : scan, client GATT, encodage des commandes et décodage des trames. |
| `:heartrate` | Couche Bluetooth du capteur cardiaque : scan, client GATT et décodage des mesures.          |

Principaux paquets de `:app` :

- `workout/` — modèle d'entraînement (étapes, blocs répétés, allures) et écrans liste / résumé /
  éditeur.
- `session/` — moteur de séance, régulation par fréquence cardiaque, état exposé à l'interface et
  service de premier plan.
- `history/` — liste des séances passées, rapport détaillé et statistiques.
- `data/` — base Room des entraînements et des séances, enregistrement des relevés, sauvegarde, et
  profils du tapis et du capteur (DataStore).
- `ui/` — navigation, bandeaux de connexion et demande des permissions Bluetooth.
- `settings/`, `about/` — préférences (langue, régulation, sauvegarde) et écran d'informations.
- `debug/` — écran de pilotage manuel et d'inspection des trames.

L'application est écrite en Kotlin avec Jetpack Compose, Hilt pour l'injection de dépendances, Room
et DataStore pour la persistance, et des coroutines/`Flow` pour les flux temps réel.

## Licence

Distribué sous licence MIT. Voir le fichier [LICENSE](LICENSE).
