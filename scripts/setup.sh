#!/usr/bin/env bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$HERE"

echo "== Hair Color Bot setup =="

if [[ ! -f ".env" ]]; then
  if [[ -f ".env.example" ]]; then
    cp .env.example .env
    echo "Created .env from .env.example (edítalo para agregar tu token)"
  else
    echo "WARN: .env.example no encontrado"
  fi
fi

PY=python3
PIP=pip3

if command -v "$PY" >/dev/null 2>&1; then
  echo "Python: $($PY -V)"
else
  echo "ERROR: python3 no encontrado en PATH" >&2
  exit 1
fi

if command -v "$PIP" >/dev/null 2>&1; then
  echo "Pip: $($PIP -V || true)"
else
  echo "ERROR: pip3 no encontrado en PATH" >&2
  exit 1
fi

# Try to create venv
USE_VENV=0
if [[ ! -d ".venv" ]]; then
  echo "Creando entorno virtual .venv (si falla, haré fallback a instalación de usuario)"
  if "$PY" -m venv .venv 2>/dev/null; then
    USE_VENV=1
  else
    echo "No se pudo crear venv (probablemente falta python3-venv). Continuando sin venv."
  fi
else
  USE_VENV=1
fi

if [[ "$USE_VENV" -eq 1 ]]; then
  # shellcheck source=/dev/null
  source .venv/bin/activate
  python -m pip install --upgrade pip setuptools wheel
  python -m pip install -r requirements.txt
  echo "Dependencias instaladas en .venv"
else
  echo "Instalando dependencias en usuario actual"
  $PIP install --no-input --break-system-packages -r requirements.txt
  echo "Dependencias instaladas en el entorno de usuario"
fi

echo "Setup completado. Ejecuta:\n  make dry-run   # validar\n  make run       # iniciar bot"