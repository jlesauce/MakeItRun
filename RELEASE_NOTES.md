# Notes de version

## v0.3

Une version de rodage, qui rend la séance plus souple et la régulation cardiaque plus confortable.

- **Prolonger une étape** : le bouton +1' ajoute une minute à l'étape en cours sans toucher à l'entraînement. La prolongation survit à une reprise et apparaît dans le rapport.
- **Départ en douceur en zone cardiaque** : un long palier démarre à l'allure de la borne basse et n'accélère que si le cœur ne monte pas. Les fractions visent le centre de la zone plus vite.
- **Éditeur de zone cardiaque** : le minimum et le maximum se saisissent au clavier, et le curseur de zone est de retour.
- **Allure au cran près** : les boutons +/− et le curseur avancent d'une vitesse réellement applicable par le tapis. Fini les appuis sans effet aux allures lentes.
- **Rapport de séance plus lisible** : le graphe gradue la vitesse à gauche et la fréquence cardiaque à droite, avec des repères en minutes rondes.

## v0.2

Première version destinée aux utilisateurs. Make It Run pilote un tapis de course Bluetooth FTMS pour y dérouler des entraînements automatiquement.

- **Pilotage du tapis** : l'application envoie la vitesse et la pente à chaque changement d'étape. Plus besoin de régler la machine à la main entre deux fractions.
- **Éditeur d'entraînements** : étapes à durée ou à distance, allure ou vitesse, inclinaison, et blocs répétés pour le fractionné. Une case permet d'ignorer la dernière récupération.
- **Fréquence cardiaque** : une étape peut viser une zone en battements plutôt qu'une vitesse. L'application ajuste l'allure par petits pas, et un capteur Bluetooth (ceinture ou montre) se connecte en parallèle du tapis.
- **Le tapis et le téléphone se suivent** : arrêter la courroie depuis la console met la séance en pause, la relancer la fait repartir. Pendant une séance, les autres écrans restent inaccessibles.
- **Arrière-plan** : une notification affiche l'étape en cours. Fermer l'application arrête le tapis.
- **Historique** : chaque séance est enregistrée, avec un graphe vitesse / fréquence, le détail étape par étape, et les compteurs affichés par le tapis.
- **Reprise** : une séance arrêtée depuis moins de trente minutes peut reprendre à l'étape où elle s'était arrêtée, sans créer une deuxième entrée dans l'historique.
- **Sauvegarde** : les entraînements et le profil du tapis s'exportent dans un fichier que vous conservez vous-même.
- **Français et anglais** : l'application suit la langue du téléphone, ou celle choisie dans les paramètres.
- **Guide d'utilisation** : le README pointe vers une documentation pas à pas, de la première connexion à la relecture d'une séance.
