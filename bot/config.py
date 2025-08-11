from __future__ import annotations

import os
from pydantic import BaseModel, Field, ValidationError
from dotenv import load_dotenv


class BotSettings(BaseModel):
    bot_token: str = Field(..., alias="BOT_TOKEN")
    use_uvloop: bool = Field(default=False, alias="USE_UVLOOP")
    persistence_path: str = Field(default="data/persistence.pkl", alias="PERSISTENCE_PATH")

    class Config:
        populate_by_name = True


def load_settings() -> BotSettings:
    # Load environment variables from .env if present
    load_dotenv(override=False)
    try:
        return BotSettings(**os.environ)
    except ValidationError as exc:
        missing = []
        for err in exc.errors():
            if err.get("type") == "missing":
                missing.append(err.get("loc", [""])[0])
        missing_str = ", ".join(missing) if missing else "unknown"
        raise RuntimeError(f"Missing required configuration: {missing_str}. Copy .env.example to .env and set values.") from exc