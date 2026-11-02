# Validation de la RC Strata/Qwen — 2 novembre 2026

Branche `release/v1.2.0-rc.2-strata-qwen`, images
`v1.2.0-rc.2-strata-qwen`. Aucun scan Confluence client, aucune publication et aucune
modification du serveur Strata existant pendant cette préparation.

## Tests et données de référence

| Vérification | Résultat |
|---|---|
| Python, tests unitaires finaux | 1 340 réussis, couverture 83 % |
| Java, `mvn clean test` | 1 818 réussis |
| Angular, suite complète | 705 réussis |
| Intégrations Java Strata/configuration/migration | 4 réussies |
| Intégrations gRPC du post-filtre | 6 réussies |
| Contrat du prompt | Prompts `per_type` et schéma identiques au benchmark archivé |
| Archive du benchmark | 71 fichiers, 16,94 Mio ; empreintes SHA-256 et rejeu des métriques vérifiés |
| Configuration de publication | `actionlint` sans erreur ; Compose production + DGNSI validé |

Cela représente 3 863 tests unitaires et 10 tests d'intégration. Les tests de frontière
du juge couvrent notamment `0.95`, dont la représentation gRPC en float doit être
normalisée avant comparaison au seuil Java.

Le [rapport du benchmark](../../benchmarks/pii-judge-dataset-eval/results/qwen38-strata-vs-gptoss-20261101/RAPPORT.md)
reste la référence pour le choix Qwen3.8-Flash-Next IQ3_S, Strata, `per_type`, seuil `0.95`.

## Images et démarrage Docker

Les trois images ont été construites localement pour `linux/arm64` avec les Dockerfiles
de la branche. Le builder API utilise Maven 3.9.16 et Java 25 ; le frontend utilise
Node 22 ; le détecteur utilise Python 3.11. Le téléchargement Infisical a été corrigé
vers son nouveau dépôt officiel et `pip --pre` a été retiré pour ne pas sélectionner
de dépendances expérimentales.

La stack de vérification utilisait un PostgreSQL 18 vide, des identifiants factices,
des ports locaux aléatoires et son propre réseau. Elle ne montait aucun volume de
l'installation existante. L'API et le détecteur démarraient directement, sans l'entrypoint
Infisical, afin de vérifier les images indépendamment des secrets du client.

Vérifications réussies :

- PostgreSQL, API, détecteur et UI atteignent l'état `healthy`.
- L'API répond `UP`, l'UI répond HTTP 200 et son proxy transmet les appels à l'API.
- La liste du juge suit API → gRPC → faux Strata et présente Qwen comme chargé.
- Le faux Strata exige un bearer token et vérifie le modèle, `max_tokens=2048`,
  `temperature=0` et `enable_thinking=false` ; le mot de passe fictif `1234` est conservé.
- Une détection réelle Presidio + Regex retourne `CREDIT_CARD`, `IBAN` et `AVS_NUMBER`
  sur trois valeurs de test, sans erreur de détecteur. Ministral était désactivé
  uniquement dans cette base jetable.
- L'image finale conserve les secrets dans 60 cas couvrant les alias `SECRET_KEY`,
  `JWT_TOKEN`, `AWS_KEY` et `CONNECTION_STRING`, puis réussit à nouveau l'appel gRPC authentifié.
- Le SQL d'upgrade a été appliqué deux fois sur le schéma créé par Hibernate avec
  PostgreSQL 18 : seul le modèle du juge et ses champs d'audit changent.
- Le configurateur Infisical d'une installation déjà initialisée quitte correctement
  dans un conteneur `--network none`, sans installation de paquets ni accès aux secrets réels.

## Qwen réel et limites

Le juge Python a aussi appelé le Strata existant sur `127.0.0.1:8080`, avec son contexte
de 32 768 tokens, sans le redémarrer : modèle annoncé chargé, mot de passe fictif
`1234` conservé (`TRUE_POSITIVE`, confiance 1), référence `${PASSWORD}` rejetée
(`FALSE_POSITIVE`, confiance 1).

Depuis Docker, ce serveur existant sans clé refuse `host.docker.internal` par sa
protection DNS rebinding (HTTP 403). Le [guide DGNSI](README.md) prévoit le démarrage
sur `8081` avec une clé Strata partagée via Infisical. Le transport Docker authentifié
a été validé avec le faux serveur ; le démarrage réel de Strata sur `8081`, les
certificats DGNSI, l'authentification Infisical du client et l'accès à son Confluence
restent à vérifier sur le poste de déploiement.

La CI est configurée pour publier `linux/amd64` et `linux/arm64`, mais la publication
et le build `linux/amd64` n'ont pas été exécutés pendant cette préparation. Les scores
du benchmark concernent des données synthétiques, pas un scan du Confluence client.
