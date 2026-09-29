import json
import shlex
import sys
from pathlib import Path
from urllib.request import ProxyHandler, build_opener

import grpc
import psycopg2

repo = Path(__file__).resolve().parents[2]
baseline = repo / '.worktrees/detection-baseline/pii-detector-service'
sys.path.insert(0, str(baseline))

from pii_detector.proto.generated import pii_detection_pb2 as pb
from pii_detector.proto.generated import pii_detection_pb2_grpc as rpc

assert Path(pb.__file__).is_relative_to(baseline), 'Wrong protobuf source'
assert 'JudgeFinding' not in pb.DESCRIPTOR.services_by_name['PIIDetectionService'].methods_by_name
http = build_opener(ProxyHandler({}))


def get_json(url):
    with http.open(url, timeout=15) as response:
        return json.load(response)


def connect(variant):
    path = repo / f'.worktrees/pipeline-compare/{variant}.env'
    env = dict(line.split('=', 1) for line in path.read_text().splitlines())
    env = {key: shlex.split(value)[0] for key, value in env.items()}
    connection = psycopg2.connect(
        host=env['DB_HOST'], port=env['DB_PORT'], dbname=env['DB_NAME'],
        user=env['DB_USER'], password=env['DB_PASSWORD'], connect_timeout=5,
    )
    connection.set_session(readonly=True)
    return connection


assert get_json('http://127.0.0.1:8181/ai-sentinel/actuator/health')['status'] == 'UP'
config = get_json('http://127.0.0.1:8181/ai-sentinel/api/v1/pii-detection/config')
assert get_json('http://127.0.0.1:4201/api/v1/pii-detection/config') == config
assert 'lmStudioJudgeModel' not in config, 'Frontend is routed to the current API'
models = get_json('http://127.0.0.1:4201/api/v1/pii-detection/lm-studio/models')
assert not models['error'] and models['models'], 'API cannot query LM Studio through gRPC'

with connect('current') as current, connect('baseline') as reference:
    with current.cursor() as src, reference.cursor() as dst:
        src.execute('SELECT current_database(), (SELECT count(*) FROM scan_events)')
        source_name, source_count = src.fetchone()
        dst.execute('SELECT current_database(), (SELECT count(*) FROM scan_events)')
        reference_name, reference_count = dst.fetchone()
        assert source_name == 'ai-sentinel' and reference_name == 'ai-sentinel-baseline'
        dst.execute('SELECT default_threshold, ministral_concurrency FROM pii_detection_config WHERE id=1')
        threshold, concurrency = dst.fetchone()
        assert float(threshold) == config['defaultThreshold']
        assert concurrency == config['ministralConcurrency']
        with grpc.insecure_channel('127.0.0.1:50052') as channel:
            health = rpc.PIIDetectionServiceStub(channel).CheckDetectorsHealth(
                pb.DetectorsHealthRequest(), timeout=30,
            )
        assert health.detectors and all(detector.reachable for detector in health.detectors)
        print(f'OK: frontend, API, gRPC and dedicated database; scan events: current={source_count}, baseline={reference_count}')
