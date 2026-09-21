"""Prompt, output schema and verdict parsing of the LLM judge.

The judge answers one question about a stored finding: is the extracted ``value``
really an instance of ``pii_type`` in this context? The system prompt is the ``v2``
design and the verdict schema is the one measured by ``benchmarks/llm-judge-eval``
(August 2026): keep both in sync with the benchmark, never tune them here blind.

Transport (endpoint resolution, HTTP client, timeouts) stays in
``MinistralDetector.judge_finding``; this module is pure.
"""
from __future__ import annotations

import json
from typing import Any, Dict

VERDICTS = frozenset({"TRUE_POSITIVE", "FALSE_POSITIVE", "UNSURE"})

# Reasoning models spend most of the budget before the JSON; a plain model stops
# at the closing brace long before.
JUDGE_MAX_TOKENS = 2048

VERDICT_SCHEMA: Dict[str, Any] = {
    "type": "object",
    "properties": {
        "reason": {"type": "string"},
        "confidence": {"type": "number"},
        "verdict": {
            "type": "string",
            "enum": sorted(VERDICTS),
        },
    },
    "required": ["reason", "confidence", "verdict"],
}

# `reason` is generated before `verdict` so the verdict is a conclusion drawn from
# stated evidence rather than a coin flip the model then rationalises.
SYSTEM_PROMPT = """\
Tu audites la PRECISION d'un detecteur d'entites nommees (PII).
Question unique : la `value` extraite est-elle reellement une instance du type \
`pii_type`, dans ce contexte precis ?

Tu ne juges PAS :
- la sensibilite, la gravite, ni le risque juridique (RGPD / nLPD) ;
- prod vs test, public vs prive : une IP RFC1918 reste une adresse IP, une carte \
de test reste un numero de carte ;
- la force ou la notoriete d'un secret : un mot de passe par defaut, faible ou \
publie reste un mot de passe.

Tu juges UNIQUEMENT : le FORMAT, la SEMANTIQUE et le ROLE de la value dans son \
contexte.

REGLE D'OR (cout asymetrique) : rejeter une vraie PII est PIRE que garder un \
faux positif. N'emets FALSE_POSITIVE que si tu peux justifier par l'un de ces \
motifs, et NOMME-le dans le reason :
(a) VIOLATION DE FORMAT : la value viole structurellement la specification du \
type (longueur, alphabet, prefixe, cle de controle manifestement absente) ;
(b) TYPE CONCURRENT : le contexte designe explicitement une AUTRE entite (un \
numero de suivi, un port reseau, une version, un ticket, un hash, un code \
comptable...) ;
(c) PAS UNE VALEUR : la value est le NOM du champ/parametre/colonne et non son \
contenu, un placeholder de documentation, une reference de variable non \
resolue, ou une valeur deja masquee ;
(d) PAS UNE PERSONNE : pour les types qui qualifient un individu, la value ne \
se rattache a aucune personne (compte de service, entite juridique, notion \
generale, programme institutionnel).

GARDE-FOUS SUR LES MOTIFS (les 3 erreurs les plus frequentes) :
- Motif (a) INTERDIT si tu ne connais pas avec certitude la specification \
officielle du type pour le pays concerne. Les formats nationaux (plaques \
cantonales suisses, IDE CHE-, carte d'assure a 20 chiffres, passeports \
etrangers, permis de sejour) varient : dans le doute sur la SPEC elle-meme, \
c'est UNSURE, jamais FALSE_POSITIVE.
- Motif (b) exige un type INCOMPATIBLE, pas un type plus PRECIS. Une date de \
creation reste une DATE ; une appartenance a un parti reste une opinion \
politique ; un identifiant federal de batiment reste un identifiant unique. Ne \
rejette que si la value designe reellement AUTRE CHOSE.
- Motif (d) ne s'applique QU'AUX types qui qualifient un individu (nom, \
nationalite, religion, profession...). Ne l'invoque jamais pour un code, un \
numero ou un secret : un PIN ou un numero de compte n'a pas a "designer une \
personne" pour etre valide.

L'absence d'indice contextuel n'est JAMAIS un motif de rejet : une value au bon \
format, seule, reste TRUE_POSITIVE. Ne calcule JAMAIS toi-meme un Luhn, un \
mod-97 ou un mod-11, tu te tromperais.
En cas de doute reel -> UNSURE (la value est conservee).

SORTIE : JSON strict, rien d'autre. `reason` d'abord (1 phrase en francais, \
240 caracteres maximum, citant le motif retenu), `verdict` en dernier :
{"reason": "...", "confidence": <float 0..1>, "verdict": \
"TRUE_POSITIVE"|"FALSE_POSITIVE"|"UNSURE"}"""


def mark_span(context: str, value: str) -> str:
    """Delimit the first occurrence of ``value`` in ``context`` with ``>>>``/``<<<``.

    Without the markers a repeated value (a label and its value on the same line)
    would be ambiguous about which occurrence is on trial. A value absent from the
    context (masked or normalised window) leaves the context untouched: the value
    is already stated on its own line of the prompt.
    """
    if not value:
        return context
    start = context.find(value)
    if start < 0:
        return context
    end = start + len(value)
    return f"{context[:start]}>>>{context[start:end]}<<<{context[end:]}"


def user_prompt(pii_type: str, type_label: str, value: str, context: str) -> str:
    return (
        f"pii_type   : {pii_type}\n"
        f"type_label : {type_label}\n"
        f"value      : {value}\n"
        f"contexte   : {mark_span(context, value)}"
    )


def build_payload(
    model_id: str, pii_type: str, type_label: str, value: str, context: str
) -> Dict[str, Any]:
    """OpenAI ``chat/completions`` body: temperature 0, strict JSON schema output."""
    return {
        "model": model_id,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt(pii_type, type_label, value, context)},
        ],
        "temperature": 0,
        "stream": False,
        "max_tokens": JUDGE_MAX_TOKENS,
        "response_format": {
            "type": "json_schema",
            "json_schema": {
                "name": "pii_verdict",
                "strict": True,
                "schema": VERDICT_SCHEMA,
            },
        },
    }


def parse_verdict(content: str) -> Dict[str, Any]:
    """Read the verdict object out of the model's content channel.

    Structured output is requested, but a reasoning model that exhausts its token
    budget answers with an empty or truncated content channel, and some servers
    wrap the JSON in prose. Anything that is not a well-formed verdict comes back as
    ``UNPARSEABLE`` with the raw head of the content as reason, never as an
    exception: the caller keeps the finding (fail-open) and records why.
    """
    text = (content or "").strip()
    if not text:
        return _unparseable("empty content channel")
    parsed = _first_json_object(text)
    if parsed is None:
        return _unparseable(text[:200])
    verdict = str(parsed.get("verdict", "")).upper()
    if verdict not in VERDICTS:
        return _unparseable(f"unknown verdict {verdict!r}")
    return {
        "verdict": verdict,
        "confidence": _confidence(parsed.get("confidence")),
        "reason": str(parsed.get("reason", ""))[:400],
    }


def _first_json_object(text: str) -> Dict[str, Any] | None:
    start, end = text.find("{"), text.rfind("}")
    if start < 0 or end <= start:
        return None
    try:
        parsed = json.loads(text[start:end + 1])
    except ValueError:
        return None
    return parsed if isinstance(parsed, dict) else None


def _confidence(raw: Any) -> float:
    try:
        return min(1.0, max(0.0, float(raw)))
    except (TypeError, ValueError):
        return 0.0


def _unparseable(reason: str) -> Dict[str, Any]:
    return {"verdict": "UNPARSEABLE", "confidence": 0.0, "reason": reason}
