from __future__ import annotations

import base64
import os
from typing import Tuple

from cryptography.hazmat.primitives.kdf.scrypt import Scrypt
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def _derive_key(password: str, salt: bytes) -> bytes:
    kdf = Scrypt(salt=salt, length=32, n=2**14, r=8, p=1)
    return kdf.derive(password.encode("utf-8"))


def encrypt_secret(plain: str, password: str) -> str:
    salt = os.urandom(16)
    key = _derive_key(password, salt)
    aesgcm = AESGCM(key)
    nonce = os.urandom(12)
    ct = aesgcm.encrypt(nonce, plain.encode("utf-8"), associated_data=None)
    blob = base64.urlsafe_b64encode(salt + nonce + ct).decode("ascii")
    return blob


def decrypt_secret(blob_b64: str, password: str) -> str:
    raw = base64.urlsafe_b64decode(blob_b64.encode("ascii"))
    salt, nonce, ct = raw[:16], raw[16:28], raw[28:]
    key = _derive_key(password, salt)
    aesgcm = AESGCM(key)
    pt = aesgcm.decrypt(nonce, ct, associated_data=None)
    return pt.decode("utf-8")