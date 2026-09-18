---
name: creer-release
description: >-
  Publie une version Make It Run (numéro, notes, tests, APK, tag et release
  GitHub). Utiliser quand l'utilisateur demande une release, une livraison, un
  tag vX.Y, d'incrémenter la version, ou invoque CreerRelease.
---

# CreerRelease

Quand l'utilisateur demande une release, lis et suis à la lettre
[`.cursor/agents/CreerRelease.md`](../../agents/CreerRelease.md).

N'improvise pas le déroulé. Le fichier de l'agent est la source de vérité :
version dans `gradle.properties`, notes dans `RELEASE_NOTES.md`, APK signé
attaché à la release GitHub du tag `vMAJOR.MINOR`.
