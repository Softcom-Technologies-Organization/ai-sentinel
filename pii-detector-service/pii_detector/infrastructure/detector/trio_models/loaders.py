import hashlib
import importlib.util
from pathlib import Path

from .ettin import EttinAdapter
from .scoring import BardsAdapter, CbxAdapter

MODEL_RELEASES = {
    "bards": ("bardsai/eu-pii-anonimization-multilang-v3", "091984b5fbce4c161abc4776ed0f4e3206e1b374"),
    "cbx": ("CobuildX-AI/cbx-secrets-detector-32M", "d6af1fabe7e6404edd4545ad1d3f62b1e7eda475"),
    "ettin": ("kalyan-ks/ettin-68m-nemotron-pii", "500262a2aaf913825ef750ef255c3fe437cd8e64"),
}
DECODER_DIGESTS = {
    "bards": ("pii_detector.py", "2f00dc3c31bf78a32ec729905d57b8513c7d02d5758b8992a351e7c4d0a109fa"),
    "cbx": ("cbx_secrets.py", "48a3f8f83b20e7de9cd519cbc50b93fd03498ed2287596e0aa4d4f8daf7868bc"),
}


def _reviewed_decoder(path: Path, name: str):
    filename, expected_digest = DECODER_DIGESTS[name]
    source = path / filename
    if hashlib.sha256(source.read_bytes()).hexdigest() != expected_digest:
        raise ValueError(f"Pinned {name} decoder fingerprint mismatch")
    spec = importlib.util.spec_from_file_location(f"trio_{name}_decoder", source)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _load_bards(path: Path, device: str):
    module = _reviewed_decoder(path, "bards")

    class ScoredDetector(module.PIIDetector):
        def _char_probs(self, text):
            result = super()._char_probs(text)
            self.char_probabilities = result
            return result

    return BardsAdapter(ScoredDetector(str(path), device=device, max_len=512, stride=128))


def _load_cbx(path: Path, device: str):
    module = _reviewed_decoder(path, "cbx")
    detector = module.SecretDetector(str(path), device=device, overlap=64, join="none")
    return CbxAdapter(detector)


def _load_ettin(path: Path, device: str):
    import torch
    from transformers import AutoModelForTokenClassification, AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(path, local_files_only=True)
    model = AutoModelForTokenClassification.from_pretrained(
        path, local_files_only=True, dtype=torch.float32, attn_implementation="sdpa"
    ).to(device).eval()
    return EttinAdapter(tokenizer, model, device)


def load_model(name: str, device: str):
    from huggingface_hub import snapshot_download
    from huggingface_hub.constants import HF_HUB_OFFLINE

    model_id, revision = MODEL_RELEASES[name]
    patterns = ["*.json", "*.safetensors"]
    if name in DECODER_DIGESTS:
        patterns.append(DECODER_DIGESTS[name][0])
    path = Path(snapshot_download(
        model_id, revision=revision, allow_patterns=patterns, local_files_only=HF_HUB_OFFLINE,
    ))
    return {"bards": _load_bards, "cbx": _load_cbx, "ettin": _load_ettin}[name](path, device)
