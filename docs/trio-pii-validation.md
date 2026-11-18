# Validation du détecteur Trio PII

Validation effectuée le 11 novembre 2026 sur la branche `feature/trio-pii-detector`,
créée depuis `release/v1.2.0-rc.2-strata-qwen` au commit
`508efd98c9eff0bacd5c3074974a317b3a6611fa`. Les modèles et décodeurs reprennent
le travail de la session `269be635-b646-4f2d-badf-8b42fe3f9b67`.

## Tests automatisés

- Python : 1 378 tests unitaires réussis, couverture globale de 83 %.
- Python : 27 tests d'intégration réussis, puis les 3 tests PostgreSQL initialement
  ignorés exécutés avec succès dans une base séparée après correction de leurs fixtures.
- Java : 1 854 tests exécutés réussis, suites unitaires et intégration comprises.
  Deux cas du benchmark optionnel `TableHeaderPrecisionBenchmarkIT` restent ignorés.
- Angular : 722 tests réussis ; build de production réussi.
- Ruff sur les nouveaux fichiers Python et `git diff --check` réussis.

Les tests couvrent les trois processus simultanés, les timeouts, leur nettoyage,
les textes longs, Unicode, les seuils, les interrupteurs et les chevauchements.
Une revue indépendante confirme qu'après trois timeouts sur des textes de 1 Mo,
les nombres de descripteurs, threads et processus enfants restent inchangés.
Un test reproduit et protège également la fusion des types natifs du Trio avec
les types enum des détecteurs historiques.

## Modèles réels

Les trois modèles ont été exécutés localement sur MPS, avec leurs snapshots figés.
La comparaison des adaptateurs sur 104 documents conserve les spans du benchmark :
734 Bards, 27 CBX et 875 Ettin. Chaque modèle détecte aussi dans un texte de plus de
70 000 caractères.

Un second contrôle passe par le serveur gRPC et la configuration PostgreSQL.
Sur 120 documents, dont les 30 plus longs du corpus de référence, l'union des
caractères détectés correspond exactement au benchmark Trio : zéro document divergent.
Ce contrôle vérifie la fidélité de l'intégration, pas l'exhaustivité de détection des PII.

## Application et interface

L'application complète a été lancée : Angular sur le port 4201, API sur 8081,
service gRPC sur 50051 et PostgreSQL dans une base isolée `ai_sentinel_trio_test`.
L'utilisateur a enregistré sa connexion Confluence dans cette application.

Vérifications effectuées depuis l'UI :

- Une seule carte Trio, avec trois interrupteurs enfants et 104 types regroupés.
- Désactivation du parent conservant les choix des enfants ; refus d'enregistrer
  un Trio actif sans modèle actif.
- Enregistrement de CBX seul, puis vérification gRPC de son activation effective.
- Désactivation du type PASSWORD de CBX : disparition de cette détection, les
  11 autres types CBX restant actifs ; restauration ensuite.
- Réouverture des paramètres rechargeant correctement les types après une API indisponible.
- Résultats masqués, badges de provenance natifs et filtre Email fonctionnels.
- Vérification visuelle de la configuration et des statistiques du scan.

Deux scans réels de l'espace « AI Sentinel » ont abouti, chacun avec 12 pages et
une pièce jointe analysées, sans échec :

- Trio seul : 5 secondes affichées, 544 résultats après fusion. Contributions
  avant fusion : Bards 264, CBX 5, Ettin 286.
- Trio avec Presidio, Regex et post-filtre : 7 secondes affichées, 493 résultats
  conservés ; le post-filtre en écarte 70. Les cinq sources contribuent.

Ces durées sont des observations locales, pas une mesure comparative de performance.
Les paramètres Presidio, Regex et post-filtre ont été restaurés après le test Trio seul.
Aucune page Confluence n'a été modifiée ou remédiée.

## SonarQube

Les analyses portent sur le code du worktree de cette branche, sur le serveur local
`http://localhost:9000`. Cette édition publie les analyses sous le contexte Sonar
par défaut ; cela ne constitue pas un merge Git.

- `ai-sentinel-pii-detector-service` : zéro issue ouverte, zéro hotspot à revoir,
  quality gate OK, couverture du nouveau code 88,7 %.
- `ai-sentinel-pii-reporting-api` : zéro issue ouverte, zéro hotspot à revoir,
  quality gate OK, couverture du nouveau code 95,4 %.
- `ai-sentinel-pii-reporting-ui` : zéro issue ouverte, zéro hotspot à revoir,
  quality gate OK, couverture du nouveau code 82,9 %.

Aucune règle ni quality gate n'a été affaiblie pour obtenir ces résultats.

## État de livraison

Les modifications sont dans le worktree `/private/tmp/ai-sentinel-trio-detector`,
sur la branche dédiée. Aucun commit, push ou merge n'a été effectué.
Les modifications qui préexistaient dans le checkout principal ont été préservées.
L'application de validation reste accessible à `http://127.0.0.1:4201`.
