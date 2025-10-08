# Stage 1: build (descarga deps)
FROM python:3.11-slim AS builder

WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN apt-get update && apt-get install -y --no-install-recommends build-essential && rm -rf /var/lib/apt/lists/*

COPY tests/issuerservice-vc/requirements.txt .
RUN pip wheel --no-cache-dir --no-deps -r requirements.txt -w /wheels

# Stage 2: runtime
FROM python:3.11-slim

WORKDIR /app
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app/keys

COPY --from=builder /wheels /wheels
RUN pip install --no-cache-dir /wheels/*

COPY tests/issuerservice-vc/main.py .

EXPOSE 9090

ENV ISSUER_DID="did:web:localhost%3A9876:issuerservicevc" \
    PRIVATE_KEY_PATH="/app/keys/issuer_private.pem" \
    EXPIRY_DAYS="365"

HEALTHCHECK --interval=30s --timeout=5s --retries=3 CMD \
  python -c "import urllib.request,sys; \
  url='http://localhost:8080/api/v1/health'; \
  urllib.request.urlopen(url).read(); \
  sys.exit(0)" || exit 1

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "9090"]
