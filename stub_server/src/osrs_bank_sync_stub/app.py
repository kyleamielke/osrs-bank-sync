import logging

from fastapi import FastAPI, Header, HTTPException
from fastapi.responses import JSONResponse

from osrs_bank_sync_stub.schemas import BankSnapshot

log = logging.getLogger("osrs_bank_sync_stub")
app = FastAPI(title="osrs-bank-sync stub server", version="0.1.0")


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/api/v1/sync/bank")
def sync_bank(
    snapshot: BankSnapshot,
    authorization: str | None = Header(default=None),
) -> JSONResponse:
    if authorization is not None and not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=401,
            detail="Malformed Authorization header. Expected 'Bearer <token>'.",
        )

    log.info(
        "received bank snapshot account_hash=%s snapshot_id=%s items=%s",
        snapshot.account_hash,
        snapshot.snapshot_id,
        len(snapshot.items),
    )
    return JSONResponse({"received": True, "snapshot": snapshot.model_dump(mode="json")})
