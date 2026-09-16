<p align="center">
  <img src="docs/logo.svg" alt="Logo Make It Run" width="128" height="128">
</p>

# Make It Run

Application Android qui pilote un tapis de course Bluetooth compatible **FTMS** (_Fitness Machine
Service_) pour y dérouler des entraînements fractionnés automatiquement.

Vous composez une séance dans l'application (échauffement, blocs répétés, récupération, retour au
calme), vous la lancez, et Make It Run envoie la vitesse et la pente au tapis étape par étape. Plus
besoin de surveiller le chrono ni de régler la machine à la main entre deux fractions.

## Fonctionnalités

- **Connexion Bluetooth Low Energy** à un tapis FTMS : détection des machines à portée, connexion,
  lecture des capacités déclarées (plage de vitesse, pas, pente pilotable ou non) et reconnexion au
  dernier tapis utilisé.
- **Éditeur d'entraînements** : étapes typées (échauffement, effort, récupération, retour au calme),
  fin d'étape au temps ou à la distance, allure/vitesse et inclinaison imposées ou libres, blocs
  répétés pour le fractionné.
- **Exécution pilotée** : décompte de départ, consigne envoyée automatiquement à chaque changement
  d'étape, temps et distance restants, étape suivante annoncée, pause et arrêt d'urgence.
- **Séance en arrière-plan** : un service de premier plan maintient l'entraînement en vie et affiche
  sa progression dans une notification, même si l'écran est quitté ou verrouillé.
- **Mesures en direct** : vitesse, allure, distance, temps, inclinaison, fréquence cardiaque et
  dépense énergétique telles que remontées par le tapis.
- **Onglet Debug** : pilotage manuel de la machine, capacités déclarées et dernière trame FTMS reçue,
  utile pour comparer le décodage avec la spécification.

Les entraînements sont stockés localement (Room) ; l'application ne communique avec aucun serveur.

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
- Côté développement : JDK 17 et Android Studio récent (le projet utilise Gradle 9.5, AGP 9.x,
  Kotlin 2.4 et compile contre le SDK 37).

## Compiler et lancer

```bash
# Compiler la variante debug
./gradlew assembleDebug

# Installer sur un appareil connecté en ADB
./gradlew installDebug

# Tests unitaires (logique d'entraînement et décodage FTMS)
./gradlew test
```

Le décodage FTMS et la logique de séance sont couverts par des tests unitaires JVM, mais tout ce qui
touche au Bluetooth demande un vrai tapis : l'émulateur ne permet pas de tester la connexion.

## Structure du projet

| Module  | Rôle                                                                                     |
| ------- | ---------------------------------------------------------------------------------------- |
| `:ftms` | Couche Bluetooth : scan, client GATT, encodage des commandes et décodage des trames FTMS. |
| `:app`  | Interface Compose, modèle d'entraînement, persistance et moteur de séance.                |

Principaux paquets de `:app` :

- `workout/` — modèle d'entraînement (étapes, blocs répétés, allures) et écrans liste / résumé /
  éditeur.
- `session/` — moteur de séance, état exposé à l'interface, service de premier plan.
- `data/` — base Room des entraînements et profil du dernier tapis connecté (DataStore).
- `ui/` — navigation, bandeau de connexion et demande des permissions Bluetooth.
- `debug/` — écran de pilotage manuel et d'inspection des trames.

L'application est écrite en Kotlin avec Jetpack Compose, Hilt pour l'injection de dépendances, Room
et DataStore pour la persistance, et des coroutines/`Flow` pour les flux temps réel.

## Licence

Distribué sous licence MIT. Voir le fichier [LICENSE](LICENSE).
