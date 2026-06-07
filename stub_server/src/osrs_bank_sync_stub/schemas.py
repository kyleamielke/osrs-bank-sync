from datetime import datetime, timedelta
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class BankItem(BaseModel):
    model_config = ConfigDict(extra="forbid")

    slot: int = Field(ge=0)
    item_id: int = Field(ge=1)
    quantity: int = Field(ge=1)


class BankSnapshot(BaseModel):
    model_config = ConfigDict(extra="forbid")

    account_hash: int
    display_name: str | None = None
    account_type: str | None = None
    captured_at: datetime
    plugin_version: str
    snapshot_id: UUID
    items: list[BankItem]

    @field_validator("captured_at")
    @classmethod
    def captured_at_must_be_utc(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() != timedelta(0):
            raise ValueError("captured_at must be timezone-aware UTC")
        return value
