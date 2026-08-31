# Backlog SonarQube — ai-sentinel

> Genere le 2026-08-31 13:48 UTC par `qa/sonar/refresh-backlog.py` depuis http://localhost:9000.
> Perimetre : issues **OPEN / CONFIRMED** des 3 modules, branche d'analyse `main`.
>
> ⚠️ L'analyse publiee sous `main` decrit en realite la branche `chore/improve-error-handling`.
> Les numeros de ligne ne sont valides que sur cette branche, et se decalent des le premier commit.

**Total : 0 issues** — 0 security hotspot.

Protocole d'execution obligatoire : [RUNBOOK.md](RUNBOOK.md).

## Avancement par vague

| Vague | Perimetre | Issues | Statut |
|-------|-----------|--------|--------|
| **A** | Tests — mecanique pure | 0 | ⬜ a faire |
| **B** | Production — mecanique locale | 0 | ⬜ a faire |
| **C** | Accessibilite UI — modifie le DOM rendu | 0 | ⬜ a faire |
| **D** | Jugement requis — lire le RUNBOOK avant | 0 | ⬜ a faire |

Statuts : ⬜ a faire · 🔄 en cours · ✅ terminee et verifiee · ⛔ annulee (rollback).

Legende des cases : `[ ]` a traiter · `[x]` corrigee et verifiee · `[!]` escaladee, voir la section Escalades du RUNBOOK.

---

## Vague A — Tests — mecanique pure (0 issues)

Aucun fichier de production touche. Une regression ici ne peut venir que du test lui-meme.

---

## Vague B — Production — mecanique locale (0 issues)

Modifie du code de production, mais chaque correction est locale et sans effet de bord fonctionnel.

---

## Vague C — Accessibilite UI — modifie le DOM rendu (0 issues)

Change le HTML produit. Peut casser des selecteurs de test ou un snapshot : vague isolee, verifiee a part.

---

## Vague D — Jugement requis — lire le RUNBOOK avant (0 issues)

Changement de signature, de semantique ou de structure. Chaque lot a une garde explicite.

