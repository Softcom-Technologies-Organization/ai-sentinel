# Serveurs du juge LLM

Le juge dispose de sa propre configuration dans **Paramètres → Serveurs LLM** :
fournisseur, hôte, port et modèle. Les choix proposés sont LM Studio, oMLX et Strata.
Changer le serveur du juge ne change pas celui du détecteur Ministral.

Les ports proposés lors du choix d'un fournisseur sont 1234 pour LM Studio, 8000
pour oMLX et 8080 pour Strata. Ils restent modifiables. L'hôte est celui que le
service de détection Python peut joindre ; dans un conteneur, `localhost` désigne
ce conteneur.

La liste des modèles provient du serveur sélectionné. Les paramètres permettent
aussi de saisir le nom du modèle manuellement. La fenêtre de lancement du juge
utilise cette même connexion et le modèle enregistré ; elle vérifie que le
serveur répond avant d'autoriser le lancement.

## Compatibilité avec les installations existantes

La configuration ajoute trois champs nullable : `judgeProvider`, `judgeHost` et
`judgePort`. Les fournisseurs sont `LM_STUDIO`, `OMLX` et `STRATA`. Le champ existant
`lmStudioJudgeModel` conserve le modèle du juge, quel que soit le fournisseur.

Pour l'hôte et le port, la priorité est : valeur explicite pendant la consultation
des modèles, configuration du juge enregistrée, variable `LLM_JUDGE_HOST` ou
`LLM_JUDGE_PORT`, puis configuration historique LM Studio. Une configuration sans
fournisseur sélectionné conserve ce fonctionnement historique.

Les trois fournisseurs utilisent le transport compatible OpenAI existant. Le
service Python consulte `/v1/models` lorsque l'API spécifique LM Studio n'est pas
disponible et envoie les évaluations à `/v1/chat/completions`. Aucun SDK de
fournisseur ni poids de modèle n'est ajouté à la distribution d'AI Sentinel.

## Licences vérifiées le 12 novembre 2026

[oMLX](https://github.com/jundot/omlx/blob/main/LICENSE) est sous Apache License 2.0.
[Strata de Niko1221](https://github.com/Niko1221/Strata/blob/main/LICENSE) est sous
MIT. Ces deux licences figurent dans la catégorie A de la
[politique des licences tierces de l'Apache Software Foundation](https://www.apache.org/legal/resolved.html#category-a).
Leur utilisation comme serveurs externes convient à cette intégration dans un
projet Apache-2.0. Toute redistribution de ces logiciels doit conserver les
licences et mentions requises.

La licence d'un serveur ne couvre pas les modèles qu'il exécute. Le modèle
`Jundot/Qwen3.8-Flash-Next-oQ4e-mtp` est sous
[Qwen Community License 1.0](https://huggingface.co/Jundot/Qwen3.8-Flash-Next-oQ4e-mtp/blob/main/LICENSE),
et non sous Apache-2.0. Cette licence impose notamment des conditions propres à
certains usages commerciaux. Cette intégration ne redistribue pas ses poids et
ne prétend pas les placer sous Apache-2.0.

## Validation du transport oMLX

Le 12 novembre 2026, les 49 tests Python ciblés du juge et de la liste des modèles
passent sans changement de code Python. Le service gRPC découvre le modèle
`Qwen3.8-Flash-Next-oQ4e-mtp` sur le serveur local oMLX au port 8000.
Un jugement sur un mot de passe fictif renvoie `TRUE_POSITIVE`, une
confiance de 1,0 et une justification, sans erreur. Après chargement, cet appel
prend 0,927 seconde. Le premier appel a pris environ 151 secondes, avec une
pression mémoire signalée par le serveur ; ce temps ne constitue pas une mesure
comparative de performance.

## Validation finale dans l'application

Sur la branche `feature/trio-pii-detector`, le 12 novembre 2026 :

- Java : 1 888 tests exécutés, aucun échec ; deux benchmarks facultatifs ignorés.
  La suite inclut les intégrations PostgreSQL et gRPC, la migration rejouable sur
  un ancien schéma et les priorités de connexion du juge.
- Angular : 750 tests réussis dans 52 fichiers, couverture régénérée et build
  de production réussi.
- SonarQube local : aucune issue ouverte ni hotspot à examiner dans les trois
  projets ; leurs Quality Gates sont OK. La couverture du nouveau code atteint
  96,2 % pour Java, 84,2 % pour Angular et 88,7 % pour Python.
- Depuis les paramètres, sélection d'oMLX et découverte de
  `Qwen3.8-Flash-Next-oQ4e-mtp`, enregistrement puis rechargement complet : le juge
  reste sur `localhost:8000` et Ministral reste sur `localhost:1234`.
- Depuis la remédiation d'une page de test, lancement d'un jugement en mode
  `PROPOSE` sur une seule détection. Le job
  `5a0d948c-317f-49fd-b38b-05ab0d0a8a60` termine en `COMPLETED`, avec 1 détection
  évaluée, 1 faux positif proposé et 0 erreur. Aucune modification Confluence
  n'est effectuée par ce mode.
- LM Studio : liste des modèles et port proposé vérifiés dans l'interface.
  Strata : choix, port proposé et erreur de connexion vérifiés ; aucun serveur
  Strata local n'étant disponible, aucune inférence réelle Strata n'est déclarée
  comme testée.

L'API, l'interface et le service Python restent démarrés. Les changements sont
dans le worktree `/private/tmp/ai-sentinel-trio-detector`, sans commit ni fusion.
