#!/usr/bin/env python3
from __future__ import annotations

import argparse
import sys

# Local imports via module path
from bot.crypto_utils import encrypt_secret


def parse_args(argv: list[str]) -> argparse.Namespace:
    p = argparse.ArgumentParser(description="Seal a Telegram bot token with a password")
    p.add_argument("token", help="Plain bot token (e.g., 12345:ABCDE)")
    p.add_argument("password", help="Password to derive encryption key")
    return p.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    blob = encrypt_secret(args.token, args.password)
    print("Add these lines to your .env:")
    print(f"BOT_TOKEN_ENC={blob}")
    print(f"DECRYPT_PASSWORD={args.password}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())