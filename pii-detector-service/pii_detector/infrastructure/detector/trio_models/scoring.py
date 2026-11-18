from statistics import fmean


SECRET_LABEL = "AUTH_SECRET"


def _rejoin_secrets(text: str, spans: list[dict]) -> list[dict]:
    """Rejoins the pieces of one secret cut by the official decoder.

    That decoder splits words on , ; | " < > ( ) [ ] { } * ^, characters a password or a key
    often contains: `\\36}D\\fKjg` came out as two findings. Two secret pieces with no whitespace
    between them are one value.
    """
    joined = []
    for span in spans:
        previous = joined[-1] if joined else None
        glued = (previous and span["label"] == previous["label"] == SECRET_LABEL
                 and previous["end"] < span["start"]
                 and not any(character.isspace() for character in text[previous["end"]:span["start"]]))
        if glued:
            previous["end"] = span["end"]
            previous["text"] = text[previous["start"]:previous["end"]]
        else:
            joined.append(dict(span))
    return joined


class BardsAdapter:
    def __init__(self, detector):
        self.detector = detector
        self.model = detector.model

    def predict(self, text: str) -> list[dict]:
        spans = _rejoin_secrets(text, self.detector.predict(text))
        return [dict(span, score=self._score(span)) for span in spans]

    def _score(self, span: dict) -> float:
        if span["label"] == "EMAIL_ADDRESS":
            # The official decoder replaces email predictions with deterministic regex matches.
            return 1.0
        probabilities, counts, _ = self.detector.char_probabilities
        start, end = span["start"], span["end"]
        index = self.detector.types.index(span["label"])
        score = float(probabilities[start:end, index].sum() / counts[start:end].sum())
        return min(1.0, max(0.0, score))


def retained_token_scores(windows: list[list[float]], token_count: int, max_length: int) -> list[float]:
    step, overlap = max_length - 2, 64
    starts = [0] if token_count <= step else list(range(0, token_count - step, step - overlap)) + [token_count - step]
    scores = [0.0] * token_count
    for index, (start, window) in enumerate(zip(starts, windows, strict=True)):
        end = min(start + step, token_count)
        low = start if index == 0 else start + overlap // 2
        high = end if index == len(starts) - 1 else end - overlap // 2
        scores[low:high] = window[low - start:high - start]
    return scores


class CbxAdapter:
    def __init__(self, detector):
        self.detector = detector
        self.model = detector.model

    def predict(self, text: str) -> list[dict]:
        if not text.strip():
            return []
        encoded = self.detector.tok(
            text, add_special_tokens=False, return_offsets_mapping=True, verbose=False,
        )
        windows = []

        def capture_probabilities(_module, _arguments, logits):
            windows.append(logits.float().softmax(-1).amax(-1)[0, 1:-1].cpu().tolist())

        with self.model.register_forward_hook(capture_probabilities):
            tags = self.detector._tag(encoded["input_ids"])
        offsets = encoded["offset_mapping"]
        scores = retained_token_scores(windows, len(tags), self.detector.max_length)
        spans = self.detector._spans(text, offsets, tags)
        return [dict(span, score=self._score(span, offsets, scores)) for span in spans]

    @staticmethod
    def _score(span: dict, offsets: list, scores: list[float]) -> float:
        return fmean(
            score for (start, end), score in zip(offsets, scores, strict=True)
            if start < span["end"] and end > span["start"]
        )
