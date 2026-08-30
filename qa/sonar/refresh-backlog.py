#!/usr/bin/env python3
"""Regenerates qa/sonar/BACKLOG.md from the SonarQube API.

    SONAR_TOKEN=... python3 qa/sonar/refresh-backlog.py [--offline]

Issues are grouped into waves of increasing risk (see WAVES below) so that a batch can be
reverted on its own. --offline rebuilds the backlog from the raw-*.json already on disk,
without contacting the server.

Sonar publishes this project under the branch name "main" even when the analysed working
tree is another branch, so the line numbers describe the working tree at analysis time, not
the main commit. Re-run scripts/sonar.sh before trusting them again after any edit.
"""

import argparse
import base64
import collections
import glob
import json
import os
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

HOST = os.environ.get("SONAR_HOST_URL", "http://localhost:9000")
PROJECTS = [
    ("ai-sentinel-pii-reporting-api", "pii-reporting-api"),
    ("ai-sentinel-pii-detector-service", "pii-detector-service"),
    ("ai-sentinel-pii-reporting-ui", "pii-reporting-ui"),
]
HERE = Path(__file__).parent

# Wave = rollback unit ordering. A wave is only started once the previous one is green.
WAVES = {
    "A": (
        "Tests — mecanique pure",
        "Aucun fichier de production touche. Une regression ici ne peut venir que du test lui-meme.",
        ["java:S5778", "java:S8924", "typescript:S5906", "java:S1117", "java:S6068", "java:S9015"],
    ),
    "B": (
        "Production — mecanique locale",
        "Modifie du code de production, mais chaque correction est locale et sans effet de bord fonctionnel.",
        [
            "python:S8572", "python:S9073", "python:S5778", "java:S7467", "python:S9083",
            "java:S8491", "java:S1612", "java:S1128", "typescript:S1128", "python:S3415",
            "python:S5781", "python:S8714", "java:S6213", "typescript:S7776",
        ],
    ),
    "C": (
        "Accessibilite UI — modifie le DOM rendu",
        "Change le HTML produit. Peut casser des selecteurs de test ou un snapshot : vague isolee, verifiee a part.",
        [
            "Web:S6819", "Web:MouseEventWithoutKeyboardEquivalentCheck", "Web:S5256",
            "Web:InputWithoutLabelCheck", "Web:ItemTagNotWithinContainerTagCheck", "css:S1874",
        ],
    ),
    "D": (
        "Jugement requis — lire le RUNBOOK avant",
        "Changement de signature, de semantique ou de structure. Chaque lot a une garde explicite.",
        [
            "java:S8688", "python:S3776", "typescript:S2699", "java:S8947", "java:S6809",
            "java:S107", "python:S5332", "java:S2925", "typescript:S5976", "java:S2143",
        ],
    ),
}

# The "how", written once per rule so the fixing agent does not re-derive it 36 times.
RECIPES = {
    "java:S5778": "Sortir du lambda `assertThatThrownBy` / `assertThrows` tout ce qui n'est pas l'appel qui leve. Les preparations remontent avant l'assertion. Ne jamais changer l'exception attendue.",
    "java:S8924": "Remplacer `Mockito.when(...)` par `when(...)` et ajouter l'import statique. Purement syntaxique.",
    "typescript:S5906": "Remplacer l'assertion generique par la plus specifique : `toBe(true)` -> `toBeTruthy()` selon le message Sonar, `toEqual(null)` -> `toBeNull()`. Ne pas relacher la contrainte.",
    "java:S1117": "Renommer la variable locale qui masque un champ. Renommage local uniquement, jamais le champ.",
    "java:S6068": "Simplifier l'appel Mockito indique (ex. `verify(x, times(1))` -> `verify(x)`).",
    "java:S9015": "Remplacer le mock construit a la main par un champ `@Mock`, l'extension Mockito etant deja active.",
    "python:S8572": "Dans un bloc `except`, remplacer `logging.error(...)` / `logger.error(...)` par `logger.exception(...)` et supprimer un eventuel `exc_info=True` devenu redondant. Ajoute la stacktrace au log : verifier qu'aucun test n'asserte le texte exact du log.",
    "python:S9073": "Scinder l'assertion composite (`assert a and b`) en deux assertions distinctes. Meme conditions, meme resultat.",
    "python:S5778": "Ne garder que l'appel qui leve dans le bloc `pytest.raises`. Le reste remonte au-dessus.",
    "java:S7467": "Remplacer le parametre d'exception inutilise par la variable anonyme `_` (Java 21+).",
    "python:S9083": "Uniformiser les parentheses des decorateurs pytest (`@pytest.fixture` sans parentheses vides).",
    "java:S8491": "Le Javadoc ne documente rien : soit le rattacher a la declaration suivante, soit le convertir en commentaire bloc. Ne pas supprimer une information utile.",
    "java:S1612": "Remplacer le lambda par une reference de methode.",
    "java:S1128": "Supprimer l'import inutilise. Verifier qu'il n'est pas utilise uniquement dans un Javadoc.",
    "typescript:S1128": "Supprimer l'import inutilise.",
    "python:S3415": "Remettre les arguments d'assertion dans l'ordre attendu/obtenu. Ne change pas le verdict du test.",
    "python:S5781": "Supprimer la valeur dupliquee dans le litteral d'ensemble. Verifier qu'il ne s'agit pas d'une valeur manquante mal recopiee.",
    "python:S8714": "Remplacer le `try/except ... fail()` par `pytest.raises`.",
    "java:S6213": "Renommer l'identifiant qui utilise un mot reserve restreint (`var`, `record`, `yield`, `sealed`).",
    "typescript:S7776": "Remplacer le tableau utilise seulement pour un test d'appartenance par un `Set`.",
    "Web:S6819": "Remplacer le `role=` ARIA par la balise HTML native equivalente : `role=\"region\"` -> `<section>`, `role=\"status\"` -> `<output>`. Verifier qu'aucun test ne cible le role via un selecteur.",
    "Web:MouseEventWithoutKeyboardEquivalentCheck": "Ajouter le gestionnaire clavier correspondant (`(keyup.enter)` / `(keydown.space)`) a cote du `(click)`. Ne pas retirer le gestionnaire souris.",
    "Web:S5256": "Ajouter les en-tetes `<th>` manquants au tableau.",
    "Web:InputWithoutLabelCheck": "Associer un `<label for>` au champ, ou un `aria-label` si l'etiquette est deja visible ailleurs.",
    "Web:ItemTagNotWithinContainerTagCheck": "Remettre le `<li>` dans un conteneur `<ul>` / `<ol>`.",
    "css:S1874": "Remplacer la propriete CSS depreciee par son equivalent courant.",
    "java:S8688": "GARDE : ne pas injecter de `Clock` a la volee. Utiliser la zone deja retenue par le projet et rester coherent avec le code voisin. Si le fichier n'a pas de convention, escalader plutot que d'en inventer une.",
    "python:S3776": "GARDE : extraire une fonction privee nommee, sans changer les entrees/sorties. Gain vise : passer sous 15. Si la fonction est couverte par moins d'un test, escalader au lieu de refactorer.",
    "typescript:S2699": "GARDE CRITIQUE : ajouter l'assertion qui manque, celle que le test pretend verifier d'apres son nom. Si elle passe au rouge, c'est un bug de production revele : NE PAS affaiblir l'assertion pour la faire passer. Marquer l'issue `[!]` et escalader.",
    "java:S8947": "Retirer le `final` sur la methode d'entite JPA : il empeche le proxy Hibernate de fonctionner (bug reel, pas un nit).",
    "java:S6809": "Extraire la methode transactionnelle dans un bean injecte : appelee via `this`, la transaction n'est jamais ouverte (bug reel).",
    "java:S107": "GARDE : 9 parametres sur une methode de repository. Regrouper en objet de criteres touche tous les appelants. Escalader si plus de 3 appelants.",
    "python:S5332": "GARDE : `http://` vise ici une instance LM Studio locale. Passer en `https://` casserait le detector. Documenter l'usage local et marquer l'issue en attente d'arbitrage, ne pas modifier l'URL.",
    "java:S2925": "GARDE : remplacer `Thread.sleep` par une attente conditionnelle (Awaitility ou latch). Si le test devient instable, revenir au sleep et escalader.",
    "typescript:S5976": "Regrouper les tests similaires en un `it.each`. Conserver un cas par jeu de donnees, ne pas en perdre en route.",
    "java:S2143": "Remplacer l'API de date historique par `java.time`. Verifier le format produit dans l'export Excel.",
}

SEV_ICON = {"BLOCKER": "🟥", "CRITICAL": "🟧", "MAJOR": "🟨", "MINOR": "⬜", "INFO": "ℹ️"}


def fetch(path, token):
    auth = base64.b64encode(f"{token}:".encode()).decode()
    req = urllib.request.Request(f"{HOST}{path}", headers={"Authorization": f"Basic {auth}"})
    return json.load(urllib.request.urlopen(req))


def collect(offline):
    if not offline:
        token = os.environ["SONAR_TOKEN"]
        for key, _ in PROJECTS:
            data = fetch(f"/api/issues/search?componentKeys={key}&branch=main"
                         f"&statuses=OPEN,CONFIRMED&ps=500", token)
            (HERE / f"raw-{key}.json").write_text(json.dumps(data, indent=1))
    issues = []
    for key, module in PROJECTS:
        for issue in json.loads((HERE / f"raw-{key}.json").read_text())["issues"]:
            issue["module"] = module
            issue["path"] = issue["component"].split(":", 1)[1]
            issues.append(issue)
    return issues


def wave_of(rule):
    for wave, (_, _, rules) in WAVES.items():
        if rule in rules:
            return wave
    return "D"


def render(issues):
    stamp = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC")
    by_wave = collections.defaultdict(lambda: collections.defaultdict(list))
    for i in issues:
        by_wave[wave_of(i["rule"])][i["rule"]].append(i)

    out = [
        "# Backlog SonarQube — ai-sentinel",
        "",
        f"> Genere le {stamp} par `qa/sonar/refresh-backlog.py` depuis {HOST}.",
        "> Perimetre : issues **OPEN / CONFIRMED** des 3 modules, branche d'analyse `main`.",
        ">",
        "> ⚠️ L'analyse publiee sous `main` decrit en realite la branche `chore/improve-error-handling`.",
        "> Les numeros de ligne ne sont valides que sur cette branche, et se decalent des le premier commit.",
        "",
        f"**Total : {len(issues)} issues** — 0 security hotspot.",
        "",
        "Protocole d'execution obligatoire : [RUNBOOK.md](RUNBOOK.md).",
        "",
        "## Avancement par vague",
        "",
        "| Vague | Perimetre | Issues | Statut |",
        "|-------|-----------|--------|--------|",
    ]
    for wave, (title, _, _) in WAVES.items():
        n = sum(len(v) for v in by_wave[wave].values())
        out.append(f"| **{wave}** | {title} | {n} | ⬜ a faire |")
    out += [
        "",
        "Statuts : ⬜ a faire · 🔄 en cours · ✅ terminee et verifiee · ⛔ annulee (rollback).",
        "",
        "Legende des cases : `[ ]` a traiter · `[x]` corrigee et verifiee · `[!]` escaladee, "
        "voir la section Escalades du RUNBOOK.",
        "",
    ]

    for wave, (title, intent, _) in WAVES.items():
        lots = by_wave[wave]
        n = sum(len(v) for v in lots.values())
        out += [f"---", "", f"## Vague {wave} — {title} ({n} issues)", "", intent, ""]
        for rule, group in sorted(lots.items(), key=lambda kv: -len(kv[1])):
            sev = group[0]["severity"]
            modules = sorted({g["module"] for g in group})
            out += [
                f"### Lot `{rule}` — {len(group)} issue(s)",
                "",
                f"{SEV_ICON.get(sev, '')} {sev} · modules : {', '.join(modules)} · "
                f"1 commit pour tout le lot",
                "",
                f"**Correction** : {RECIPES.get(rule, 'Voir la description de la regle dans SonarQube.')}",
                "",
            ]
            for i in sorted(group, key=lambda x: (x["module"], x["path"], x.get("textRange", {}).get("startLine", 0))):
                line = i.get("textRange", {}).get("startLine", "?")
                msg = i["message"].replace("\n", " ").strip()
                out.append(f"- [ ] `{i['module']}/{i['path']}:{line}` — {msg} <!-- {i['key'][:8]} -->")
            out.append("")
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--offline", action="store_true", help="rebuild from the raw-*.json on disk")
    args = parser.parse_args()
    found = collect(args.offline)
    (HERE / "BACKLOG.md").write_text(render(found))
    print(f"BACKLOG.md ecrit — {len(found)} issues")
    counts = collections.Counter(wave_of(i["rule"]) for i in found)
    for wave in WAVES:
        print(f"  vague {wave}: {counts[wave]}")
