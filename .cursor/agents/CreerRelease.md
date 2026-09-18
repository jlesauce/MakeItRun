---
name: creer-release
description: >-
  Prépare et publie une version Make It Run : incrémente le numéro, rédige les
  notes, compile, teste, pose le tag et crée la release GitHub avec l'APK. À
  utiliser quand l'utilisateur demande une release, une livraison, un tag vX.Y,
  d'incrémenter la version, ou invoque CreerRelease.
---

Tu prépares et publies une version de Make It Run. Suis ce déroulé dans l'ordre.
N'invente pas d'étape, n'en saute pas, et arrête-toi à la première erreur.

## Entrée

La version cible est `vMAJOR.MINOR` (exemple : `v0.3`).

- Si l'utilisateur la donne, utilise-la telle quelle.
- Sinon, lis `makeItRun.versionMajor` et `makeItRun.versionMinor` dans
  `gradle.properties`, propose d'incrémenter le minor (ou le major s'il le
  demande), et attends confirmation avant d'écrire quoi que ce soit.

Le tag GitHub, le nom de l'APK et le titre de la release sont toujours
`vMAJOR.MINOR`, identiques à `v{versionMajor}.{versionMinor}` dans
`gradle.properties`. Pas de troisième chiffre.

## Déroulé

Copie cette checklist et coche au fur et à mesure.

```
- [ ] 1. Vérifier l'état du dépôt
- [ ] 2. Incrémenter gradle.properties
- [ ] 3. Mettre à jour RELEASE_NOTES.md
- [ ] 4. Compiler et lancer les tests
- [ ] 5. Commiter
- [ ] 6. Pousser la branche
- [ ] 7. Poser le tag et créer la release GitHub
- [ ] 8. Rendre l'URL de la release
```

### 1. Vérifier l'état du dépôt

- Branche `main`, à jour avec `origin/main`.
- Arbre de travail propre : aucun fichier modifié ou non suivi qui n'appartienne
  pas à cette release. S'il reste du travail en cours, arrête-toi et demande.
- Aucun tag `vMAJOR.MINOR` n'existe déjà en local ni sur `origin`.
- `gh` est disponible. S'il manque, installe GitHub CLI (`winget install --id GitHub.cli`
  sous Windows) et vérifie `gh auth status`.
- Ne lis pas, ne copie pas et n'affiche jamais `keystore.properties`,
  `*.jks` ni `*.keystore`.

### 2. Incrémenter gradle.properties

Écris uniquement :

```
makeItRun.versionMajor=MAJOR
makeItRun.versionMinor=MINOR
```

Ne touche pas aux autres propriétés. Le `versionCode` et le `versionName` de
l'application se calculent tout seuls dans `app/build.gradle.kts`.

### 3. Mettre à jour RELEASE_NOTES.md

Fichier à la racine, en français, avec accents.

Préfixe une section `## vMAJOR.MINOR` **au-dessus** des sections déjà présentes.
Ne réécris pas les versions antérieures.

Le contenu de la section :

- une phrase d'accroche (ce que cette version change pour l'utilisateur) ;
- une liste à puces de fonctionnalités, **deux lignes maximum par puce** ;
- uniquement ce qui est nouveau **depuis le tag précédent** (`git log vPREV..HEAD`).
  S'il n'y a pas encore de tag, depuis le dernier bump de version, ou toute
  l'application s'il s'agit de la première livraison.

Reste du côté utilisateur : pas de détails d'implémentation, pas de chemins de
fichiers, pas de jargon de commits. Les corrections de bugs visibles ont droit
à une puce ; le reste non.

N'ajoute pas les consignes d'installation dans ce fichier : le workflow GitHub
Actions les place tout seul en tête de la release.

### 4. Compiler et lancer les tests

Sur cette machine Windows, exporte d'abord :

```
JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
```

Puis :

```
./gradlew assembleRelease test
```

`assembleRelease` signe avec le keystore local (`keystore.properties` à la
racine, déjà gitignoré). Si la signature échoue, arrête-toi : sans APK signé il
n'y a pas de release.

Si un test échoue, corrige, relance, et ne continue pas tant que la suite n'est
pas verte.

L'APK signé se trouve à `app/build/outputs/apk/release/app-release.apk`.
Copie-le hors de `build/` sous le nom `MakeItRun-vMAJOR.MINOR.apk` (par exemple
dans un dossier temporaire) pour l'attacher à la release. Ne le commite jamais.

### 5. Commiter

Un seul commit, qui contient au minimum :

- `gradle.properties`
- `RELEASE_NOTES.md`

et, s'ils ont changé pour cette livraison, le workflow ou l'agent.

Message en français **sans accents**, style du dépôt : titre à l'indicatif,
corps qui dit pourquoi, puces qui disent quoi. Exemple de titre :
`Prepare la livraison utilisateur 0.3`.

Ne commite jamais `keystore.properties`, `*.jks`, `*.apk`, `local.properties`.

### 6. Pousser la branche

```
git push origin HEAD
```

Le tag doit pointer sur un commit déjà présent sur `origin/main`. Ne force-push
jamais.

### 7. Poser le tag et créer la release GitHub

Tag annoté, **après** le push du commit :

```
git tag -a vMAJOR.MINOR -m "Make It Run vMAJOR.MINOR"
git push origin vMAJOR.MINOR
```

Le push du tag déclenche `.github/workflows/release.yml`, qui revérifie que le
tag colle à `gradle.properties`, reconstruit l'APK signé en CI, et crée ou met
à jour la release GitHub.

Crée tout de suite la release avec l'APK local, pour ne pas attendre la CI :

```
gh release create vMAJOR.MINOR \
  --title "Make It Run vMAJOR.MINOR" \
  --notes-file - \
  MakeItRun-vMAJOR.MINOR.apk <<'EOF'
<consigne d'installation, identique à celle du workflow>
<ligne vide>
<texte de la section ## vMAJOR.MINOR de RELEASE_NOTES.md, sans le titre ##>
EOF
```

Si `gh release create` échoue parce que la CI a déjà créé la release, attache
l'APK avec `gh release upload vMAJOR.MINOR MakeItRun-vMAJOR.MINOR.apk --clobber`
et mets les notes à jour avec `gh release edit`.

Le body de la release GitHub doit toujours permettre deux choses :

1. télécharger un fichier `.apk` ;
2. lire les notes de cette version.

N'utilise pas `--draft`. N'utilise pas `--generate-notes` à la place de
`RELEASE_NOTES.md`.

### 8. Rendre l'URL

Quand tout est en place, donne à l'utilisateur :

- l'URL de la release GitHub (`gh release view vMAJOR.MINOR --json url -q .url`) ;
- le nom de l'APK attaché ;
- le numéro `versionName` / `versionCode` réellement embarqué.

## Interdits

- Forcer un push, ignorer les hooks, amender un commit déjà poussé.
- Pousser un tag dont le nom ne colle pas à `gradle.properties`.
- Taguer un commit où `gradle.properties` n'est pas encore à jour.
- Publier si `./gradlew assembleRelease test` a échoué.
- Mettre un secret, un mot de passe ou un keystore dans un fichier suivi, un
  message de commit, une note de version ou une réponse de chat.
- Incrémenter la version sans livrer, ou livrer sans incrémenter.

## Fichiers de référence

- Version : `gradle.properties` (`makeItRun.versionMajor`, `makeItRun.versionMinor`)
- Notes : `RELEASE_NOTES.md`
- Publication CI : `.github/workflows/release.yml`
- Signature locale : `keystore.properties` (gitignoré, ne pas l'ouvrir)
