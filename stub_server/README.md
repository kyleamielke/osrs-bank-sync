# osrs-bank-sync stub server

FastAPI stub receiver used for Phase 2.1 plugin development. It mirrors the wire
contract in `../DESIGN.md` (see **Data model → Wire format example**).

> Non-production: this server is for local/dev testing only. It has no persistence,
> no hard auth policy, and no deployment hardening.

## Requirements

- Python 3.12

## Install

```bash
cd stub_server
python3.12 -m venv .venv
source .venv/bin/activate
pip install -e .[dev]
```

## Run

```bash
uvicorn osrs_bank_sync_stub.app:app --port 8484
```

## Smoke test

```bash
curl http://127.0.0.1:8484/healthz
curl -X POST \
  -H 'Content-Type: application/json' \
  -d @sample.json \
  http://127.0.0.1:8484/api/v1/sync/bank
```

## Manual POST with Authorization

```bash
curl -X POST \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer local-dev-token' \
  -d @sample.json \
  http://127.0.0.1:8484/api/v1/sync/bank
```

`Authorization` is optional. If present, it must start with `Bearer `.

## Test and lint

```bash
ruff check .
mypy --strict src
pytest -v
```

## API

- `GET /healthz` → `{"status":"ok"}`
- `POST /api/v1/sync/bank`:
  - validates request body against the Pydantic mirror schema
  - accepts missing `Authorization` or `Authorization: Bearer ...`
  - returns `401` for malformed auth header
  - echoes parsed payload:
    `{"received": true, "snapshot": <validated snapshot>}`
