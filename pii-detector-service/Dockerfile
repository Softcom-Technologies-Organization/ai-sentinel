# syntax=docker/dockerfile:1

FROM python:3.11-slim AS builder

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    VIRTUAL_ENV=/opt/venv

RUN python -m venv "$VIRTUAL_ENV"
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

RUN apt-get update \
    && apt-get install --no-install-recommends -y build-essential \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY pii-detector-service/pyproject.toml ./
COPY pii-detector-service/README.md ./
COPY pii-detector-service/pii_detector ./pii_detector
COPY pii-detector-service/config ./config

# Copy proto to root level so script can find it at ../../../proto
# (script navigates 3 levels up from /app/pii_detector/proto)
COPY proto /proto

RUN pip install --upgrade pip \
    && pip install -e . --extra-index-url https://download.pytorch.org/whl/cpu \
    && python -m pii_detector.proto.generate_pb

FROM python:3.11-slim AS runtime

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    VIRTUAL_ENV=/opt/venv
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

WORKDIR /app

COPY --from=builder "$VIRTUAL_ENV" "$VIRTUAL_ENV"
COPY --from=builder /app /app
COPY --from=builder /proto /proto

EXPOSE 50051

CMD ["python", "-m", "pii_detector.server", "--port", "50051"]
