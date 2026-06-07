from fastapi.testclient import TestClient

from osrs_bank_sync_stub.app import app

client = TestClient(app)


def valid_payload() -> dict:
    return {
        "account_hash": 1234567890123456789,
        "display_name": "Zezima",
        "account_type": "IRONMAN",
        "captured_at": "2025-01-15T22:31:04Z",
        "plugin_version": "0.1.0",
        "snapshot_id": "550e8400-e29b-41d4-a716-446655440000",
        "items": [
            {"slot": 0, "item_id": 995, "quantity": 2147483647},
            {"slot": 1, "item_id": 4151, "quantity": 1},
        ],
    }


def test_healthz() -> None:
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_submit_valid_snapshot() -> None:
    response = client.post("/api/v1/sync/bank", json=valid_payload())
    assert response.status_code == 200
    body = response.json()
    assert body["received"] is True
    assert body["snapshot"]["account_hash"] == 1234567890123456789


def test_submit_valid_snapshot_with_bearer_header() -> None:
    response = client.post(
        "/api/v1/sync/bank",
        json=valid_payload(),
        headers={"Authorization": "Bearer token123"},
    )
    assert response.status_code == 200
    assert response.json()["received"] is True


def test_submit_malformed_authorization_header_returns_401() -> None:
    response = client.post(
        "/api/v1/sync/bank",
        json=valid_payload(),
        headers={"Authorization": "Token nope"},
    )
    assert response.status_code == 401
    assert response.json()["detail"] == "Malformed Authorization header. Expected 'Bearer <token>'."


def test_submit_extra_field_returns_422() -> None:
    payload = valid_payload()
    payload["extra_field"] = "unexpected"
    response = client.post("/api/v1/sync/bank", json=payload)
    assert response.status_code == 422


def test_submit_missing_account_hash_returns_422() -> None:
    payload = valid_payload()
    payload.pop("account_hash")
    response = client.post("/api/v1/sync/bank", json=payload)
    assert response.status_code == 422


def test_submit_invalid_item_id_returns_422() -> None:
    payload = valid_payload()
    payload["items"][0]["item_id"] = 0
    response = client.post("/api/v1/sync/bank", json=payload)
    assert response.status_code == 422


def test_submit_non_utc_captured_at_returns_422() -> None:
    payload = valid_payload()
    payload["captured_at"] = "2025-01-15T22:31:04+01:00"
    response = client.post("/api/v1/sync/bank", json=payload)
    assert response.status_code == 422
