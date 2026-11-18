from __future__ import annotations

import atexit
import math
import multiprocessing as mp
import os
import time
from concurrent.futures import ThreadPoolExecutor
from concurrent.futures import TimeoutError as FutureTimeoutError
from threading import Lock

from pii_detector.domain.entity.detector_source import DetectorSource
from pii_detector.domain.entity.pii_entity import PIIEntity

MODEL_NAMES = ("bards", "cbx", "ettin")


def enabled_trio_models(config: dict) -> tuple[str, ...]:
    if not config.get("trio_enabled", False):
        return ()
    return tuple(name for name in MODEL_NAMES if config.get(f"{name}_enabled", True))


def _model_worker(name, connection):
    try:
        import torch

        from pii_detector.infrastructure.detector.trio_models import load_model

        torch.set_num_threads(int(os.getenv("TORCH_NUM_THREADS", "1")))
        device = "cuda" if torch.cuda.is_available() else "cpu"
        if torch.backends.mps.is_available():
            device = "mps"
        model = load_model(name, device)
        while (text := connection.recv()) is not None:
            started = time.perf_counter()
            with torch.inference_mode():
                predictions = model.predict(text) if text else []
            connection.send({"predictions": predictions, "error": "",
                             "duration_ms": int((time.perf_counter() - started) * 1000)})
    except (EOFError, BrokenPipeError):
        return
    except Exception as exc:
        # Model exceptions may embed the input; expose only the exception class.
        connection.send({"predictions": [], "error": f"{name}: {type(exc).__name__}",
                         "duration_ms": 0})
    finally:
        connection.close()


class TrioDetector:
    model_id = "trio-pii"

    def __init__(self, timeout=180):
        self._context = mp.get_context("spawn")
        self._workers = {}
        self._lock = Lock()
        self._timeout = timeout
        atexit.register(self.close)

    def _start(self, name):
        parent, child = self._context.Pipe()
        process = self._context.Process(target=_model_worker, args=(name, child), daemon=True)
        try:
            process.start()
        except Exception:
            parent.close()
            raise
        finally:
            child.close()
        self._workers[name] = (process, parent)

    def _stop(self, name):
        process, connection = self._workers.pop(name)
        connection.close()
        if process.is_alive():
            process.terminate()
        process.join(timeout=5)
        if process.is_alive():
            process.kill()
            process.join(timeout=5)
        process.close()

    def close(self):
        with self._lock:
            for name in self._workers.copy():
                self._stop(name)

    def _dispatch(self, text, names, deadline):
        errors = {}
        for name in self._workers.copy():
            if name not in names:
                self._stop(name)
        for name in names:
            try:
                if name not in self._workers:
                    self._start(name)
            except (OSError, RuntimeError) as exc:
                errors[name] = self._failure(name, type(exc).__name__)
        ready = [name for name in names if name not in errors]
        errors.update(self._send_requests(text, ready, deadline))
        return errors

    def _send_requests(self, text, names, deadline):
        errors = {}
        # Only IPC uses threads; each model has its own process and GPU state.
        with ThreadPoolExecutor(max_workers=3, thread_name_prefix="trio-send") as sender:
            pending = {name: sender.submit(self._workers[name][1].send, text) for name in names}
            for name, future in pending.items():
                try:
                    future.result(timeout=max(0, deadline - time.monotonic()))
                except (OSError, FutureTimeoutError) as exc:
                    self._stop(name)
                    reason = "model request timed out" if isinstance(exc, FutureTimeoutError) else type(exc).__name__
                    errors[name] = self._failure(name, reason)
        return errors

    @staticmethod
    def _failure(name, error):
        return {"predictions": [], "duration_ms": 0, "error": f"{name}: {error}"}

    def _receive(self, name, deadline):
        try:
            connection = self._workers[name][1]
            if not connection.poll(max(0, deadline - time.monotonic())):
                return self._failure(name, "model response timed out")
            return connection.recv()
        except (OSError, EOFError) as exc:
            return self._failure(name, type(exc).__name__)

    def _request(self, text, names):
        if any(name not in MODEL_NAMES for name in names):
            raise ValueError("Unknown trio model")
        if not self._lock.acquire(timeout=self._timeout):
            return {name: self._failure(name, "model queue timed out") for name in names}
        try:
            deadline = time.monotonic() + self._timeout
            replies = self._dispatch(text, names, deadline)
            for name in names:
                if name not in replies:
                    replies[name] = self._receive(name, deadline)
                if replies[name]["error"] and name in self._workers:
                    self._stop(name)
            return replies
        finally:
            self._lock.release()

    def detect(self, text, names, configs):
        if configs is None:
            replies = {name: self._failure(name, "PII type configuration unavailable") for name in names}
        else:
            replies = self._request(text, names)
        entities, stats = [], []
        for name, reply in replies.items():
            found = []
            try:
                found = self._entities(text, name, reply["predictions"], configs or {})
            except (ValueError, KeyError, TypeError):
                reply = self._failure(name, "invalid model output")
            entities.extend(found)
            stats.append({"source": DetectorSource[name.upper()],
                          "duration_ms": reply["duration_ms"], "entities_found": len(found),
                          "error": reply["error"]})
        return entities, stats

    @staticmethod
    def _entities(text, name, predictions, configs):
        source = DetectorSource[name.upper()]
        labels = {cfg["detector_label"]: (key.split(":")[-1], cfg)
                  for key, cfg in configs.items()
                  if cfg.get("detector") == source.value and cfg.get("enabled", False)}
        entities = []
        for span in predictions:
            start, end, score = span["start"], span["end"], float(span["score"])
            if not (0 <= start < end <= len(text)) or not math.isfinite(score) or not 0 <= score <= 1:
                raise ValueError("Invalid model offsets or confidence")
            setting = labels.get(span["label"])
            if setting is None or score < setting[1]["threshold"]:
                continue
            pii_type = setting[0]
            entities.append(PIIEntity(text[start:end], pii_type, pii_type,
                                      start, end, score, source))
        return entities

    def check_health(self, names):
        return [{"source": DetectorSource[name.upper()], "reachable": not reply["error"],
                 "endpoint": "", "error": reply["error"],
                 "error_code": "DETECTOR_INIT_FAILED" if reply["error"] else "",
                 "error_params": {"model": name}}
                for name, reply in self._request("", names).items()]
