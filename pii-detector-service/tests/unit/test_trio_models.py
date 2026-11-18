import hashlib
import importlib
import json
import subprocess
import sys
from functools import cache
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import MagicMock, Mock

import numpy as np
import pytest

from pii_detector.infrastructure.detector.trio_models import ettin, loaders
from pii_detector.infrastructure.detector.trio_models.ettin import EttinAdapter, decode
from pii_detector.infrastructure.detector.trio_models.scoring import (
    BardsAdapter,
    CbxAdapter,
    retained_token_scores,
)


def _torch_modules():
    return {name: sys.modules.pop(name) for name in list(sys.modules) if name.split(".")[0] == "torch"}


@cache
def _real_torch_modules():
    stubs = _torch_modules()
    try:
        importlib.import_module("torch")
        return _torch_modules()
    finally:
        sys.modules.update(stubs)


@pytest.fixture
def real_torch(monkeypatch):
    # tests/unit/conftest.py stubs torch for the whole session; EttinAdapter needs real tensor maths,
    # and real torch submodules must not outlive the test or thinc mistakes the stub for torch.
    for name, module in _real_torch_modules().items():
        monkeypatch.setitem(sys.modules, name, module)
    return sys.modules["torch"]


def test_should_import_without_loading_ml_libraries():
    process = subprocess.run(
        [sys.executable, "-c", (
            "import sys; import pii_detector.infrastructure.detector.trio_models; "
            "assert 'torch' not in sys.modules; assert 'transformers' not in sys.modules"
        )], check=False, capture_output=True, text=True,
    )
    assert process.returncode == 0, process.stderr


def test_should_reject_changed_decoder_before_execution(tmp_path, monkeypatch):
    decoder = tmp_path / "decoder.py"
    decoder.write_text("raise AssertionError('must not execute')")
    monkeypatch.setitem(loaders.DECODER_DIGESTS, "test", (decoder.name, "invalid"))
    with pytest.raises(ValueError, match="fingerprint mismatch"):
        loaders._reviewed_decoder(tmp_path, "test")


def test_should_load_only_verified_decoder(tmp_path, monkeypatch):
    decoder = tmp_path / "decoder.py"
    decoder.write_text("VALUE = 42")
    digest = hashlib.sha256(decoder.read_bytes()).hexdigest()
    monkeypatch.setitem(loaders.DECODER_DIGESTS, "test", (decoder.name, digest))
    assert loaders._reviewed_decoder(tmp_path, "test").VALUE == 42


@pytest.mark.parametrize("name", ["bards", "cbx", "ettin"])
def test_should_download_exact_model_release(name, tmp_path, monkeypatch):
    snapshot = Mock(return_value=str(tmp_path))
    load = Mock(return_value="detector")
    monkeypatch.setattr("huggingface_hub.snapshot_download", snapshot)
    monkeypatch.setattr(loaders, f"_load_{name}", load)
    assert loaders.load_model(name, "cpu") == "detector"
    args, kwargs = snapshot.call_args
    assert args == (loaders.MODEL_RELEASES[name][0],)
    assert kwargs["revision"] == loaders.MODEL_RELEASES[name][1]
    assert "*.json" in kwargs["allow_patterns"]
    load.assert_called_once_with(tmp_path, "cpu")


def test_should_preserve_bards_decoder_and_capture_probabilities(tmp_path, monkeypatch):
    probabilities = (np.array([[0.8]]), np.array([1]), None)

    class NativeDetector:
        def __init__(self, path, **options):
            self.model = None
            self.path, self.options = path, options

        def _char_probs(self, _):
            return probabilities

    monkeypatch.setattr(loaders, "_reviewed_decoder", lambda *_: SimpleNamespace(PIIDetector=NativeDetector))
    adapter = loaders._load_bards(tmp_path, "cpu")
    assert adapter.detector._char_probs("x") is probabilities
    assert adapter.detector.char_probabilities is probabilities
    assert adapter.detector.options == {"device": "cpu", "max_len": 512, "stride": 128}


def test_should_keep_cbx_native_window_and_join_settings(tmp_path, monkeypatch):
    constructor = Mock(return_value=SimpleNamespace(model=None))
    monkeypatch.setattr(loaders, "_reviewed_decoder", lambda *_: SimpleNamespace(SecretDetector=constructor))
    adapter = loaders._load_cbx(tmp_path, "cpu")
    constructor.assert_called_once_with(str(tmp_path), device="cpu", overlap=64, join="none")
    assert adapter.detector is constructor.return_value


@pytest.mark.usefixtures("real_torch")
def test_should_wrap_local_ettin_weights_in_adapter(tmp_path, monkeypatch):
    tokenizer = Mock()
    loaded = Mock(config=SimpleNamespace(id2label={0: "O"}))
    load_weights = Mock()
    load_weights.return_value.to.return_value.eval.return_value = loaded
    monkeypatch.setattr("transformers.AutoTokenizer.from_pretrained", Mock(return_value=tokenizer))
    monkeypatch.setattr("transformers.AutoModelForTokenClassification.from_pretrained", load_weights)
    adapter = loaders._load_ettin(tmp_path, "cpu")
    assert (adapter.tokenizer, adapter.model, adapter.device) == (tokenizer, loaded, "cpu")
    assert load_weights.call_args.kwargs["local_files_only"]


def test_should_preserve_bards_spans_and_compute_pooled_confidence():
    span = {"start": 1, "end": 3, "label": "AUTH_SECRET", "text": "é🔐"}
    detector = SimpleNamespace(
        predict=Mock(return_value=[span]), model=None, types=["AUTH_SECRET"],
        char_probabilities=(np.array([[0.0], [1.6], [0.6]]), np.array([0, 2, 1]), None),
    )
    result = BardsAdapter(detector).predict(" é🔐")
    assert {key: result[0][key] for key in span} == span
    assert result[0]["score"] == pytest.approx(2.2 / 3)


def test_should_rejoin_bards_secret_pieces_when_no_whitespace_separates_them():
    text = "pw: \\36}D\\fKjg and x}y"
    pieces = [(4, 7, "AUTH_SECRET"), (8, 14, "AUTH_SECRET"), (19, 20, "PERSON_NAME"), (21, 22, "PERSON_NAME")]
    spans = [{"start": a, "end": b, "label": label, "text": text[a:b]} for a, b, label in pieces]
    detector = SimpleNamespace(
        predict=Mock(return_value=spans), model=None, types=["AUTH_SECRET", "PERSON_NAME"],
        char_probabilities=(np.ones((len(text), 2)), np.ones(len(text)), None),
    )
    result = BardsAdapter(detector).predict(text)
    assert [(span["start"], span["end"], span["text"]) for span in result] == [
        (4, 14, "\\36}D\\fKjg"), (19, 20, "x"), (21, 22, "y"),
    ]


def test_should_keep_bards_secrets_apart_when_whitespace_separates_them():
    text = "abc def"
    spans = [{"start": 0, "end": 3, "label": "AUTH_SECRET", "text": "abc"},
             {"start": 4, "end": 7, "label": "AUTH_SECRET", "text": "def"}]
    detector = SimpleNamespace(
        predict=Mock(return_value=spans), model=None, types=["AUTH_SECRET"],
        char_probabilities=(np.ones((7, 1)), np.ones(7), None),
    )
    assert len(BardsAdapter(detector).predict(text)) == 2


def test_should_score_regex_email_without_model_probability():
    span = {"start": 0, "end": 13, "label": "EMAIL_ADDRESS"}
    adapter = BardsAdapter(SimpleNamespace(predict=lambda _: [span], model=None))
    assert adapter.predict("a@example.org") == [dict(span, score=1.0)]


def test_should_keep_same_window_ownership_as_official_cbx_decoder():
    scores = retained_token_scores([[0.1] * 80, [0.8] * 80, [0.9] * 80], 100, 82)
    assert len(scores) == 100
    assert scores[:48] == [0.1] * 48
    assert scores[48:52] == [0.8] * 4
    assert scores[52:] == [0.9] * 48


def test_should_score_cbx_with_unicode_offsets_and_remove_hook():
    model = Mock()
    handle = MagicMock()
    model.register_forward_hook.return_value = handle
    encoded = {"input_ids": [1, 2], "offset_mapping": [(0, 2), (2, 4)]}
    span = {"start": 2, "end": 4, "text": "é🔐", "label": "PASSWORD"}
    detector = SimpleNamespace(
        model=model, tok=Mock(return_value=encoded), max_length=7999,
        _spans=Mock(return_value=[span]),
    )

    def tag(_):
        logits = MagicMock()
        logits.float().softmax().amax().__getitem__().cpu().tolist.return_value = [0.5, 0.880797]
        model.register_forward_hook.call_args.args[0](model, (), logits)
        return [0, 1]

    detector._tag = tag
    result = CbxAdapter(detector).predict("abé🔐")
    assert result == [dict(span, score=pytest.approx(0.880797))]
    handle.__exit__.assert_called_once()


def test_should_remove_cbx_hook_when_model_fails():
    model = Mock()
    handle = MagicMock()
    model.register_forward_hook.return_value = handle
    detector = SimpleNamespace(
        model=model, tok=lambda *args, **kwargs: {"input_ids": [1]},
        _tag=Mock(side_effect=RuntimeError("inference failed")),
    )
    adapter = CbxAdapter(detector)
    with pytest.raises(RuntimeError, match="inference failed"):
        adapter.predict("text")
    handle.__exit__.assert_called_once()


def test_should_label_whole_word_by_dominant_fragment_when_sub_words_disagree():
    tokens = [(0, 3, {"last_name": 0.9}), (3, 6, {"first_name": 0.6}), (6, 8, {"last_name": 0.5})]
    assert decode("Dupontel", tokens) == [
        {"start": 0, "end": 8, "label": "last_name", "text": "Dupontel", "score": pytest.approx(3.7 / 8)}
    ]


def test_should_drop_word_when_single_labelled_fragment_is_below_floor():
    assert decode("Antidisestablishment", [(0, 2, {"last_name": 0.9})]) == []


def test_should_label_whole_ip_address_when_block_label_dominates():
    tokens = [(3, 7, {"ipv4": 0.9}), (7, 9, {"ipv4": 0.9}), (9, 13, {"ipv4": 0.9})]
    [finding] = decode("192.168.1.256", tokens)
    assert (finding["start"], finding["end"], finding["label"]) == (0, 13, "ipv4")


def test_should_not_spread_value_label_when_separated_by_key_value_punctuation():
    tokens = [(4, 13, {"password": 0.9}), (19, 23, {"hostname": 0.8})]
    findings = decode("pwd=Tr0ub4dor;host=db01", tokens)
    assert [(f["text"], f["label"]) for f in findings] == [("Tr0ub4dor", "password"), ("db01", "hostname")]


def test_should_merge_across_space_but_not_newline_when_offsets_follow_emoji():
    text = "🔐 Élise Dupont\nMartin"
    tokens = [(2, 7, {"first_name": 0.8}), (8, 14, {"first_name": 0.6}), (15, 21, {"first_name": 0.9})]
    assert decode(text, tokens) == [
        {"start": 2, "end": 14, "label": "first_name", "text": "Élise Dupont", "score": pytest.approx(7.6 / 11)},
        {"start": 15, "end": 21, "label": "first_name", "text": "Martin", "score": 0.9},
    ]


def test_should_drop_finding_when_labelled_token_is_only_punctuation():
    assert decode("x ### y", [(2, 5, {"password": 0.9})]) == []


def test_should_sum_entity_prefixes_and_score_every_token_when_text_spans_several_windows(
    monkeypatch, real_torch
):
    monkeypatch.setattr(ettin, "WINDOW", 4)
    monkeypatch.setattr(ettin, "OVERLAP", 2)
    probabilities = real_torch.tensor([
        [0.25, 0.25, 0.25, 0.25],
        [0.95, 0.02, 0.02, 0.01],
        [0.2, 0.3, 0.4, 0.1],
    ])
    model = Mock(
        config=SimpleNamespace(id2label={0: "O", 1: "B-city", 2: "I-city", 3: "B-name"}),
        side_effect=lambda input_ids: SimpleNamespace(logits=probabilities.log()[input_ids]),
    )
    ids = [2, 1, 2, 2, 2, 2, 2, 2, 2, 2]
    encoded = {"input_ids": ids, "offset_mapping": [(i, i + 1) for i in range(len(ids))]}
    tokenizer = Mock(return_value=encoded, cls_token_id=0, sep_token_id=0)
    tokens = EttinAdapter(tokenizer, model, "cpu").token_probabilities("abcdefghij")
    assert model.call_count > 1
    assert [start for start, _, _ in tokens] == [0, 2, 3, 4, 5, 6, 7, 8, 9]
    assert all(scores == pytest.approx({"city": 0.7, "O": 0.2, "name": 0.1}) for _, _, scores in tokens)


@pytest.mark.parametrize("build", [CbxAdapter, lambda detector: EttinAdapter(detector.tok, detector, "cpu")],
                         ids=["cbx", "ettin"])
@pytest.mark.usefixtures("real_torch")
def test_should_skip_inference_for_blank_text(build):
    detector = Mock(config=SimpleNamespace(id2label={0: "O"}))
    assert build(detector).predict("  \n") == []
    detector.assert_not_called()
    detector.tok.assert_not_called()


def test_should_catalogue_every_native_type_without_alias_collision():
    catalog = json.loads((Path(loaders.__file__).parent / "catalog.json").read_text())
    assert {name: sum(row["detector"] == name for row in catalog) for name in ["BARDS", "CBX", "ETTIN"]} == {
        "BARDS": 37, "CBX": 12, "ETTIN": 55,
    }
    assert len({(row["detector"], row["pii_type"]) for row in catalog}) == len(catalog)
    assert all(0.0 <= row["threshold"] <= 1.0 for row in catalog)
    assert {(row["detector"], row["detector_label"]) for row in catalog if not row["enabled"]} == {
        ("BARDS", "CRIMINAL_OFFENCE_DATA"), ("BARDS", "FINANCIAL_AMOUNT"), ("BARDS", "PERSON_ALIAS"),
        ("BARDS", "DOCUMENT_REFERENCE"), ("BARDS", "TRADE_UNION_MEMBERSHIP"),
    }
    assert next(row for row in catalog if row["detector_label"] == "ssn")["pii_type"] == "SOCIAL_SECURITY_NUMBER"
