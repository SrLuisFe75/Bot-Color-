from __future__ import annotations

import os
from pydantic import BaseModel, Field, ValidationError
from dotenv import load_dotenv
from .crypto_utils import decrypt_secret


class BotSettings(BaseModel):
    bot_token: str | None = Field(default=None, alias="BOT_TOKEN")
    bot_token_enc: str | None = Field(default=None, alias="BOT_TOKEN_ENC")
    decrypt_password: str | None = Field(default=None, alias="DECRYPT_PASSWORD")
    use_uvloop: bool = Field(default=False, alias="USE_UVLOOP")
    persistence_path: str = Field(default="data/persistence.pkl", alias="PERSISTENCE_PATH")

    class Config:
        populate_by_name = True


def load_settings() -> BotSettings:
    # Load environment variables from .env if present
    load_dotenv(override=False)

    settings = BotSettings(**os.environ)

    # Resolve token
    if not settings.bot_token:
        if settings.bot_token_enc and settings.decrypt_password:
            try:
                token = decrypt_secret(settings.bot_token_enc, settings.decrypt_password)
                settings.bot_token = token
            except Exception as exc:  # pragma: no cover
                raise RuntimeError("No se pudo descifrar BOT_TOKEN_ENC. Verifica la contraseña.") from exc

    if not settings.bot_token:
        raise RuntimeError(
            "No hay token configurado. Usa BOT_TOKEN o BOT_TOKEN_ENC + DECRYPT_PASSWORD en el entorno/.env"
        )

    return settings