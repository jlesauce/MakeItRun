<p align="center">
  <img src="logo.svg" alt="Logo Make It Run" width="96" height="96">
</p>

# Guide d'utilisation de Make It Run

Make It Run transforme votre téléphone en télécommande intelligente pour votre tapis de course.
Vous décrivez une séance une bonne fois pour toutes — échauffement, fractions, récupérations, retour
au calme — et l'application se charge de régler la vitesse et la pente du tapis au bon moment. Vous
n'avez plus qu'à courir.

Ce guide part du principe que vous n'avez jamais ouvert l'application. Il vous emmène de la première
connexion jusqu'à la relecture d'une séance terminée.

## Sommaire

- [Avant de commencer](#avant-de-commencer)
- [Premier démarrage](#premier-démarrage)
- [Connecter le tapis](#connecter-le-tapis)
- [Connecter un capteur de fréquence cardiaque](#connecter-un-capteur-de-fréquence-cardiaque)
- [Créer votre premier entraînement](#créer-votre-premier-entraînement)
- [Lancer une séance](#lancer-une-séance)
- [Les étapes pilotées par la fréquence cardiaque](#les-étapes-pilotées-par-la-fréquence-cardiaque)
- [L'historique de vos séances](#lhistorique-de-vos-séances)
- [Reprendre une séance interrompue](#reprendre-une-séance-interrompue)
- [Les paramètres](#les-paramètres)
- [L'onglet Debug](#longlet-debug)
- [En cas de problème](#en-cas-de-problème)

## Avant de commencer

Il vous faut :

- Un téléphone Android 8.0 ou plus récent, avec le Bluetooth.
- Un tapis de course qui expose le service Bluetooth **FTMS**. C'est un standard : la plupart des
  tapis connectés vendus depuis quelques années le proposent, souvent sous l'appellation
  « Bluetooth FTMS » ou « compatible Zwift / Kinomap » dans leur notice.
- Éventuellement une ceinture ou une montre cardio qui diffuse la fréquence cardiaque en Bluetooth.
  Elle n'est indispensable que pour les étapes qui visent une zone de fréquence.

**Un mot sur la sécurité, avant tout le reste.** L'application commande à distance une machine sur
laquelle vous courez. Gardez toujours le cordon de sécurité du tapis attaché, restez capable
d'arrêter la machine depuis son propre panneau, et relisez vos consignes de vitesse avant de lancer
une séance. Un zéro oublié dans une vitesse cible se rattrape mal à 15 km/h.

## Premier démarrage

À la première ouverture, l'application demande l'autorisation d'utiliser le Bluetooth. Android
présente cette permission sous le nom « Appareils à proximité ». Sans elle, Make It Run ne peut ni
détecter ni piloter le tapis : touchez **Autoriser**.

L'application s'ouvre sur trois onglets, en bas de l'écran :

| Onglet | À quoi il sert |
| --- | --- |
| **Entraînements** | La connexion au tapis, vos séances, et le point de départ de tout le reste. |
| **Historique** | Les séances que vous avez déjà courues. |
| **Debug** | Le pilotage manuel du tapis, pour les curieux et le dépannage. |

## Connecter le tapis

Tout se passe en haut de l'onglet **Entraînements**, dans le bandeau de connexion. Une petite pastille
colorée vous dit en un coup d'œil où vous en êtes.

1. Allumez le tapis et assurez-vous qu'aucune autre application n'y est déjà connectée. Un tapis ne
   parle qu'à un seul téléphone à la fois : c'est la cause numéro un des connexions qui échouent.
2. Touchez **Rechercher**. L'application liste les machines à portée ; celles qui annoncent le
   protocole attendu portent l'étiquette **FTMS**.
3. Touchez votre tapis dans la liste.

L'application lit alors les capacités de la machine : la plage de vitesses qu'elle accepte, le pas
de réglage, et si sa pente est pilotable à distance. Cette lecture ne se fait qu'une fois, mais elle
est indispensable — c'est elle qui permet à l'éditeur de vous empêcher de saisir une vitesse que
votre tapis refusera.

Si votre tapis n'apparaît pas, l'option **Afficher tous les appareils Bluetooth** lève le filtre et
montre tout ce qui émet autour de vous. Certaines machines annoncent mal leurs services tant qu'on
ne s'y est pas connecté une première fois.

Aux ouvertures suivantes, un bouton **Reconnecter** vous remet en liaison avec le dernier tapis
utilisé, sans repasser par la recherche. Si la liaison se perd en cours de route, l'application
retente d'elle-même pendant un moment avant d'abandonner.

> Tant qu'aucun tapis n'a jamais été connu, la liste d'entraînements reste grisée et un message
> « Tapis pas encore connu » vous l'explique. Ce n'est pas une panne : l'application a besoin de
> connaître les vitesses de votre machine avant de vous laisser composer une séance.

## Connecter un capteur de fréquence cardiaque

Cette étape est facultative. Elle ne devient obligatoire que si vous créez des étapes qui visent une
zone de fréquence cardiaque.

Le principe est le même que pour le tapis : un second bandeau, un bouton de recherche, une liste.
Sont détectées les ceintures pectorales et les montres qui diffusent leur fréquence en Bluetooth.

Sur une montre Garmin, la diffusion n'est pas active par défaut. Activez **Diffuser la FC** dans
_Paramètres → Capteurs et accessoires → FC au poignet_. La montre se met alors à émettre, et
Make It Run la voit apparaître. Quand le capteur le communique, son niveau de batterie s'affiche à
côté de la fréquence.

## Créer votre premier entraînement

Depuis l'onglet **Entraînements**, touchez le bouton **+**. Vous arrivez dans l'éditeur.

### Nommer la séance

Donnez-lui un nom parlant : c'est sous ce nom qu'elle apparaîtra dans votre liste et dans votre
historique. « 10 × 400 m » vous dira plus que « Séance 3 ».

### Ajouter une étape

Une séance est une suite d'étapes. Touchez **Ajouter une étape** et renseignez :

**Le type d'étape.** Échauffement, Effort, Récupération ou Retour au calme. Ce choix n'a aucune
incidence sur le pilotage du tapis : il sert à colorer et nommer les étapes pour que vous vous y
retrouviez d'un coup d'œil pendant la séance.

**La fin de l'étape.** Une étape se termine soit au bout d'une durée, soit au bout d'une distance.
Un échauffement se pense en minutes, une fraction se pense souvent en mètres.

**La consigne.** C'est le cœur du réglage, avec trois possibilités :

- **Imposée** — vous fixez l'allure ou la vitesse, et l'application l'envoie au tapis dès le début de
  l'étape. Les bornes acceptées par votre tapis vous sont rappelées sous le champ. Touchez la valeur
  pour la saisir au clavier plutôt qu'au curseur.
- **Fréquence cardiaque** — vous fixez une zone en battements par minute, et l'application cherche
  elle-même la vitesse qui vous y maintient. Voir [la section dédiée](#les-étapes-pilotées-par-la-fréquence-cardiaque).
- **Libre** — l'application n'envoie aucune consigne et vous laissez la vitesse telle quelle. Utile
  pour un retour au calme que vous voulez gérer à la main.

Selon votre préférence, la consigne se saisit en allure (min/km) ou en vitesse (km/h). L'application
convertit toute seule.

**L'inclinaison.** Facultative. Si votre tapis ne sait pas régler sa pente à distance, le réglage
reste visible mais la machine refusera poliment la commande.

### Répéter un bloc

C'est ce qui rend le fractionné supportable à écrire. Touchez **Ajouter un bloc répété**, indiquez un
nombre de répétitions, puis ajoutez les étapes du bloc. Une séance de 10 × 400 m s'écrit donc en deux
étapes — la fraction et la récupération — plutôt qu'en vingt.

Une case **Ignorer la dernière étape de la dernière répétition** rend service plus souvent qu'on ne
croit : une séance se termine presque toujours sur un effort, pas sur la récupération qui le suit.
Cochez-la et la dernière récupération saute.

### Enregistrer

Touchez **Enregistrer**. Vous revenez à la liste, et un résumé vous rappelle le nombre d'étapes ainsi
que la durée et la distance estimées quand elles sont calculables.

Les étapes se réorganisent après coup avec les boutons **Monter** et **Descendre**, et se suppriment
une par une.

## Lancer une séance

Touchez votre entraînement dans la liste : vous arrivez sur son résumé, étape par étape. Le bouton
**Lancer sur le tapis** démarre la séance.

Deux conditions sont vérifiées avant : le tapis doit être connecté, et si l'entraînement comporte des
étapes en zone de fréquence cardiaque, le capteur doit l'être aussi. Sinon, un message vous dit
laquelle manque.

### Le décompte

L'application réclame d'abord la main sur la machine — le protocole FTMS impose de le faire avant
toute commande — puis un décompte de cinq secondes vous laisse le temps de monter sur la courroie.
Si le tapis refuse la prise de contrôle, l'erreur s'affiche immédiatement plutôt qu'à la fin du
décompte.

### Pendant la course

L'écran de séance affiche, en gros et lisible à bout de bras :

- L'étape en cours, son numéro et, dans un bloc répété, le numéro de répétition.
- Le **temps restant** ou la **distance restante** de l'étape, selon la façon dont vous l'avez définie.
- Votre **fréquence cardiaque**, à côté du temps restant, dès qu'un capteur est connecté.
- La **consigne** envoyée au tapis et la **vitesse réelle** qu'il affiche.
- Ce qui vient **ensuite**, pour ne pas être surpris par le prochain changement d'allure.
- Le **total** parcouru depuis le départ.

Trois commandes sont à portée de pouce :

- **Pause** suspend la séance et arrête la courroie. Le chronomètre de l'étape s'arrête avec elle :
  le temps en pause n'est pas décompté.
- **Étape suivante** passe à l'étape d'après. Pratique quand l'échauffement est déjà fait, ou quand
  une série tourne mal.
- **Arrêt** met fin à la séance et stoppe le tapis.

### L'écran reste verrouillé sur la séance

Tant qu'une activité est en cours, on ne peut pas naviguer ailleurs dans l'application. C'est
volontaire : il serait fâcheux de couper le Bluetooth ou de supprimer un entraînement en pleine
course. Le retour propose d'arrêter l'activité, et si vous quittez l'application pour faire autre
chose, vous retombez sur la séance en revenant.

### Si vous arrêtez le tapis depuis sa console

L'application s'en aperçoit et se met en pause toute seule. Relancez la courroie depuis la console et
la séance repart où elle en était. Vous pouvez donc mener toute la séance depuis les boutons du tapis
si vous préférez, sans jamais toucher au téléphone.

### En arrière-plan

Une notification affiche l'étape en cours et ce qu'il en reste. La séance survit à l'écran verrouillé
et au passage dans une autre application. Si vous fermez Make It Run pour de bon, l'application tente
d'arrêter le tapis avant de partir.

### À l'arrivée

Un écran de fin récapitule la distance parcourue et le temps total. La séance est déjà enregistrée
dans votre historique.

## Les étapes pilotées par la fréquence cardiaque

C'est la fonctionnalité la moins évidente et la plus utile. Plutôt que d'imposer une vitesse, vous
imposez un **effort** : « tiens-moi entre 140 et 150 battements pendant trente minutes ». Make It Run
ajuste la vitesse du tapis toute seule pour vous y maintenir.

Pourquoi c'est mieux qu'une vitesse fixe : votre endurance fondamentale ne se court pas à la même
vitesse un jour de forme et un lendemain de nuit courte, ni au début et à la fin d'une longue sortie
où la dérive cardiaque s'installe. La zone, elle, ne bouge pas.

**Comment l'application s'y prend.** Elle corrige lentement et par petits pas, parce que le cœur met
jusqu'à une minute à répondre à un changement d'allure. Corriger plus vite ferait osciller la vitesse
sans jamais se stabiliser. Trois franchises sont possibles :

| Franchise | Correction | Comportement |
| --- | --- | --- |
| **Douce** | au plus 0,3 km/h toutes les 60 s | Très stable, met du temps à rejoindre la zone. Pour les longs paliers. |
| **Normale** | au plus 0,5 km/h toutes les 45 s | Le compromis par défaut. |
| **Réactive** | au plus 0,8 km/h toutes les 30 s | Rejoint la zone plus vite, au prix d'une allure qui bouge davantage. |

Le choix se fait globalement dans les **Paramètres**, et se surcharge étape par étape dans l'éditeur
quand une séance mélange un long palier et des séries courtes.

**Pendant l'étape**, l'application vous dit où vous en êtes : dans la zone, sous la zone, au-dessus.
Elle vous signale aussi les situations où elle ne peut plus rien faire pour vous : un capteur devenu
muet ou mal porté — auquel cas elle maintient la vitesse plutôt que de partir à l'aveugle —, ou une
fréquence qui reste basse alors que le tapis est déjà à sa vitesse maximale.

## L'historique de vos séances

Chaque séance courue est enregistrée, avec un relevé par seconde. L'onglet **Historique** les liste de
la plus récente à la plus ancienne, avec la durée, la distance, la fréquence cardiaque moyenne et la
part du temps passé dans la zone visée. Les séances arrêtées en cours de route sont signalées comme
telles, avec le nombre d'étapes réellement parcourues.

Touchez une séance pour ouvrir son rapport détaillé :

- **Un graphe** superposant la vitesse et la fréquence cardiaque sur toute la durée de la séance. On y
  lit d'un coup d'œil la dérive cardiaque d'une sortie longue, ou la façon dont la fréquence retombe
  entre deux fractions.
- **Les chiffres clés** : durée, distance, fréquences moyenne et maximale, temps passé dans la zone,
  énergie dépensée.
- **Le détail étape par étape**, qui compare le prévu au réalisé. C'est là qu'on voit si le tapis a
  bien suivi les consignes.
- **Les compteurs du tapis**, c'est-à-dire ce que la machine affichait sur son propre écran à la fin.
  À comparer avec votre montre : beaucoup de tapis mesurent la distance de façon optimiste.

Un rapport se supprime depuis son propre écran, relevés compris.

## Reprendre une séance interrompue

Une séance s'arrête parfois sans que ce soit prévu : un lacet à refaire, une sonnette, une batterie de
téléphone à plat. Tant que l'arrêt remonte à **moins de trente minutes** et que toutes les étapes
n'ont pas été parcourues, la séance affiche dans l'historique un bouton **Reprendre où j'en étais**.

La reprise redémarre à l'étape exacte où vous vous étiez arrêté, en tenant compte du temps et de la
distance déjà accomplis dans cette étape. Et surtout, elle prolonge la séance d'origine au lieu d'en
créer une seconde : vous gardez une seule entrée dans l'historique, avec une durée, une distance et
une courbe continues.

Cela fonctionne même si vous avez modifié ou supprimé l'entraînement entre-temps, parce que le plan
est enregistré avec la séance.

## Les paramètres

Accessibles par le menu **⋮** en haut de l'écran.

**Langue.** Par défaut, Make It Run suit la langue du téléphone et bascule en anglais quand celle-ci
n'est pas disponible. Vous pouvez forcer le français ou l'anglais.

**Régulation par fréquence cardiaque.** La franchise de correction décrite plus haut, appliquée par
défaut à toutes les étapes qui ne précisent rien.

**Sauvegarde.** Vos entraînements vivent dans le téléphone et disparaîtraient avec l'application.
L'export les écrit, avec le profil de votre tapis, dans un fichier que vous rangez où vous voulez.
L'import ajoute le contenu d'un fichier à ce qui est déjà présent, sans rien écraser. Si la
sauvegarde contient un tapis différent de celui que connaît déjà le téléphone, l'application vous
demande lequel garder — vos entraînements sont importés dans les deux cas.

## L'onglet Debug

Cet onglet n'est pas nécessaire pour courir, mais il rend service quand quelque chose cloche.

- **Pilotage manuel** : prendre le contrôle du tapis, le démarrer, l'arrêter, lui envoyer une vitesse
  ou une pente à la main. C'est le moyen le plus rapide de vérifier que votre machine obéit.
- **Capacités déclarées** : ce que le tapis dit savoir faire, plage de vitesse et pas de réglage
  compris. Utile pour comprendre pourquoi l'éditeur refuse une valeur.
- **Dernière trame reçue** : les octets bruts envoyés par le tapis, à comparer avec la spécification
  FTMS si vous soupçonnez un décodage bancal.

## En cas de problème

**Le tapis n'apparaît pas dans la recherche.** Vérifiez qu'il est allumé et qu'aucune autre
application n'y est connectée — une seule liaison à la fois. Certains tapis se mettent en veille
Bluetooth : faites défiler la courroie quelques secondes pour les réveiller. En dernier recours,
l'option **Afficher tous les appareils Bluetooth** lève le filtre FTMS.

**Le tapis refuse la prise de contrôle.** Cela arrive quand la machine a été utilisée depuis son
propre écran entre-temps, ou qu'une application concurrente garde la main. Déconnectez, reconnectez,
et relancez. L'onglet Debug permet de tester la prise de contrôle isolément.

**La pente ne bouge pas.** Tous les tapis ne savent pas régler leur inclinaison à distance, même
quand ils ont un moteur pour le faire. L'onglet Debug vous dit ce que votre machine déclare.

**La vitesse envoyée n'est pas appliquée.** L'application s'en aperçoit et renvoie la consigne
plusieurs fois avant d'abandonner. Si le problème persiste, c'est en général que la prise de contrôle
a été perdue.

**Le capteur cardiaque n'est pas trouvé.** Sur une montre Garmin, la diffusion doit être activée
explicitement (voir plus haut). Sur une ceinture, vérifiez qu'elle est humidifiée et qu'aucune autre
application ne la capte déjà.

**La liste d'entraînements est grisée.** Un tapis doit avoir été connecté au moins une fois. Ce n'est
pas une perte de données : vos entraînements sont toujours là.

---

Make It Run est un projet personnel, distribué sous licence MIT et fourni sans garantie. Il n'est
affilié à aucun fabricant de tapis de course, et son comportement dépend de ce que votre machine
implémente réellement du protocole FTMS.
