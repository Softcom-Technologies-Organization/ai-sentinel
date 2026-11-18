import re
from collections import defaultdict

OUTSIDE = "O"
WORD = re.compile(r"\w+")
# Characters that separate two values (key=value, lists, quotes, brackets); the others glue one value.
BLOCK = re.compile(r"[^\s=;,\"'()\[\]{}<>|]+")
WORD_FLOOR = 0.15
BLOCK_FLOOR = 0.3
WINDOW, OVERLAP = 1022, 128
TOP_LABELS = 3
CONFIDENT_OUTSIDE = 0.9


def _owners(size: int, tokens: list[tuple]) -> list:
    owners = [None] * size
    for start, end, probabilities in tokens:
        end = min(end, size)
        owners[start:end] = [probabilities] * (end - start)
    return owners


def _vote(owners: list, words: list[tuple]) -> tuple:
    tally = defaultdict(float)
    for start, end in words:
        for position in range(start, end):
            for label, probability in (owners[position] or {}).items():
                tally[label] += probability
    tally.pop(OUTSIDE, None)
    if not tally:
        return None, 0.0
    label, total = max(tally.items(), key=lambda item: item[1])
    return label, total / sum(end - start for start, end in words)


def _label_words(text: str, owners: list) -> tuple[list, list]:
    """Labels each character; a score is set on word characters only."""
    labels, scores = [None] * len(text), [None] * len(text)

    def assign(start, end, words, label, score):
        labels[start:end] = [label] * (end - start)
        for word_start, word_end in words:
            scores[word_start:word_end] = [score] * (word_end - word_start)

    for block in BLOCK.finditer(text):
        words = [(block.start() + word.start(), block.start() + word.end())
                 for word in WORD.finditer(block.group())]
        for start, end in words:
            scores[start:end] = [0.0] * (end - start)
        label, score = _vote(owners, words) if len(words) > 1 else (None, 0.0)
        if label and score >= BLOCK_FLOOR:
            assign(words[0][0], words[-1][1], words, label, score)
            continue
        for word in words:
            label, score = _vote(owners, [word])
            if label and score >= WORD_FLOOR:
                assign(*word, [word], label, score)
    return labels, scores


def _label_punctuation(text: str, owners: list, labels: list, scores: list) -> None:
    for position, probabilities in enumerate(owners):
        unlabelled_symbol = labels[position] is None and scores[position] is None
        if probabilities and unlabelled_symbol and not text[position].isspace():
            top = max(probabilities, key=probabilities.get)
            if top != OUTSIDE:
                labels[position] = top


def _bridges(text: str, labels: list, position: int, label: str) -> bool:
    return (labels[position] is None and text[position] != "\n"
            and position + 1 < len(labels) and labels[position + 1] == label)


def _run_end(text: str, labels: list, start: int) -> int:
    label, end = labels[start], start
    while end < len(labels) and (labels[end] == label or _bridges(text, labels, end, label)):
        end += 1
    return end


def decode(text: str, tokens: list[tuple]) -> list[dict]:
    """Turns token probabilities into findings made of whole words.

    The model labels sub-word tokens independently, so one value can come out as several
    fragments carrying different labels. Each word instead takes the label with the highest
    mean probability over all its characters, tokens labelled outside counting as zero. Words
    glued by punctuation (IP address, URL, e-mail, key) take one label together when it
    dominates the block.
    """
    owners = _owners(len(text), tokens)
    labels, scores = _label_words(text, owners)
    _label_punctuation(text, owners, labels, scores)
    entities, position = [], 0
    while position < len(text):
        if labels[position] is None:
            position += 1
            continue
        end = _run_end(text, labels, position)
        word_scores = [score for score in scores[position:end] if score is not None]
        if word_scores:
            entities.append({"start": position, "end": end, "label": labels[position],
                             "text": text[position:end],
                             "score": min(1.0, sum(word_scores) / len(word_scores))})
        position = end
    return entities


class EttinAdapter:
    def __init__(self, tokenizer, model, device: str):
        import torch

        self.tokenizer, self.model, self.device = tokenizer, model, device
        names = model.config.id2label
        self.types = sorted({name.split("-", 1)[-1] for name in names.values()})
        self.fold = torch.zeros(len(names), len(self.types), device=device)
        for index, name in names.items():
            self.fold[int(index), self.types.index(name.split("-", 1)[-1])] = 1

    def predict(self, text: str) -> list[dict]:
        if not text.strip():
            return []
        return decode(text, self.token_probabilities(text))

    def token_probabilities(self, text: str) -> list[tuple]:
        """Per informative token: offsets and its most probable entity types, B- and I- summed."""
        encoded = self.tokenizer(text, add_special_tokens=False, return_offsets_mapping=True,
                                 verbose=False)
        values, indices = self._type_probabilities(encoded["input_ids"]).topk(TOP_LABELS, dim=-1)
        tokens = []
        for (start, end), value, index in zip(encoded["offset_mapping"], values.tolist(),
                                              indices.tolist(), strict=True):
            confident_outside = self.types[index[0]] == OUTSIDE and value[0] >= CONFIDENT_OUTSIDE
            if start < end and not confident_outside:
                tokens.append((start, end, {self.types[i]: v for i, v in zip(index, value)}))
        return tokens

    def _type_probabilities(self, ids: list[int]):
        import torch

        count, step = len(ids), WINDOW - OVERLAP
        starts = [0] if count <= WINDOW else [*range(0, count - WINDOW, step), count - WINDOW]
        kept = torch.zeros(count, len(self.types))
        for index, start in enumerate(starts):
            window = [self.tokenizer.cls_token_id, *ids[start:start + WINDOW],
                      self.tokenizer.sep_token_id]
            logits = self.model(input_ids=torch.tensor([window], device=self.device)).logits[0, 1:-1]
            end = min(start + WINDOW, count)
            # Each token keeps the window where it sits farthest from a cut.
            low = start if index == 0 else start + OVERLAP // 2
            high = end if index == len(starts) - 1 else end - OVERLAP // 2
            kept[low:high] = (logits.float().softmax(-1) @ self.fold).cpu()[low - start:high - start]
        return kept
