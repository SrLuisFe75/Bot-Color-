from __future__ import annotations

import argparse
import logging
import sys

from .handlers import HairColorBot


def setup_logging(verbose: bool = False) -> None:
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        level=level,
        format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
    )


def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Hair Color Mixing Telegram Bot")
    parser.add_argument("--dry-run", action="store_true", help="Inicializa sin iniciar el polling")
    parser.add_argument("--verbose", action="store_true", help="Logging detallado")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    setup_logging(args.verbose)

    try:
        bot = HairColorBot()
    except Exception as exc:
        logging.getLogger(__name__).exception("Fallo inicializando el bot")
        return 2

    if args.dry_run:
        logging.getLogger(__name__).info("Dry run OK: handlers y config cargados")
        return 0

    bot.run()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())