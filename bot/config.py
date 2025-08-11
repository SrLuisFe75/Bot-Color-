from __future__ import annotations

import os
from dotenv import load_dotenv
from .crypto_utils import decrypt_secret


class BotSettings:
    def __init__(self) -> None:
        load_dotenv(override=False)
        self.bot_token = os.getenv("BOT_TOKEN")
        self.bot_token_enc = os.getenv("BOT_TOKEN_ENC")
        self.decrypt_password = os.getenv("DECRYPT_PASSWORD")
        self.use_uvloop = os.getenv("USE_UVLOOP", "false").lower() in {"1", "true", "yes", "y"}
        self.persistence_path = os.getenv("PERSISTENCE_PATH", "data/persistence.pkl")

        if not self.bot_token:
            if self.bot_token_enc and self.decrypt_password:
                try:
                    self.bot_token = decrypt_secret(self.bot_token_enc, self.decrypt_password)
                except Exception as exc:
                    raise RuntimeError("No se pudo descifrar BOT_TOKEN_ENC. Verifica la contraseña.") from exc

        if not self.bot_token:
            raise RuntimeError("No hay token configurado. Usa BOT_TOKEN o BOT_TOKEN_ENC + DECRYPT_PASSWORD en .env")


def load_settings() -> BotSettings:
    return BotSettings()