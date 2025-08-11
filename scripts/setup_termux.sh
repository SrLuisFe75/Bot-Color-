#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$HERE"

echo "== Termux setup for Hair Color Bot =="

# 1) System packages
if ! command -v pkg >/dev/null 2>&1; then
  echo "ERROR: Este script es para Termux (requiere 'pkg')." >&2
  exit 1
fi

echo "Actualizando paquetes..."
pkg update -y && pkg upgrade -y

echo "Instalando dependencias del sistema..."
pkg install -y git python clang libjpeg-turbo zlib openssl python-cryptography

# 2) Python deps (usa cryptography del sistema)
PY=python
if ! command -v "$PY" >/dev/null 2>&1; then
  PY=python3
fi

$PY -m pip install --upgrade pip setuptools wheel --break-system-packages || true

if command -v grep >/dev/null 2>&1; then
  echo "Instalando requirements (sin cryptography, se usa la del sistema)..."
  grep -v '^cryptography' requirements.txt | $PY -m pip install -r /dev/stdin --no-input --break-system-packages || true
else
  echo "Instalando requirements..."
  $PY -m pip install -r requirements.txt --no-input --break-system-packages || true
fi

# 3) .env setup
if [[ -f .env ]]; then
  echo ".env ya existe, no se modifica."
else
  echo "Creando .env..."
  if [[ -n "${BOT_TOKEN_ENC:-}" && -n "${DECRYPT_PASSWORD:-}" ]]; then
    cat > .env <<EOF
BOT_TOKEN_ENC=${BOT_TOKEN_ENC}
DECRYPT_PASSWORD=${DECRYPT_PASSWORD}
USE_UVLOOP=false
PERSISTENCE_PATH=data/persistence.pkl
EOF
    echo "Escribí BOT_TOKEN_ENC y DECRYPT_PASSWORD desde variables de entorno."
  elif [[ -n "${BOT_TOKEN:-}" ]]; then
    cat > .env <<EOF
BOT_TOKEN=${BOT_TOKEN}
USE_UVLOOP=false
PERSISTENCE_PATH=data/persistence.pkl
EOF
    echo "Escribí BOT_TOKEN en claro desde variables de entorno."
  else
    echo "¿Quieres cifrar tu token ahora? (s/N)"
    read -r CHOICE || CHOICE="n"
    case "${CHOICE,,}" in
      s|si|sí|y|yes)
        read -r -p "Token (formato 12345:ABC...): " TOKEN
        read -r -p "Contraseña para cifrar: " PASS
        echo "Generando token cifrado..."
        # shellcheck disable=SC2086
        ENC_OUT=$($PY -m scripts.seal_token "$TOKEN" "$PASS")
        # Extraer líneas
        ENC_LINE=$(printf "%s\n" "$ENC_OUT" | grep '^BOT_TOKEN_ENC=') || true
        PASS_LINE=$(printf "%s\n" "$ENC_OUT" | grep '^DECRYPT_PASSWORD=') || true
        if [[ -n "$ENC_LINE" && -n "$PASS_LINE" ]]; then
          printf "%s\n%s\nUSE_UVLOOP=false\nPERSISTENCE_PATH=data/persistence.pkl\n" "$ENC_LINE" "$PASS_LINE" > .env
          echo ".env creado con token cifrado."
        else
          echo "No pude generar el cifrado, creando .env vacío. Edita manualmente."
          cp .env.example .env || true
        fi
        ;;
      *)
        read -r -p "Token en claro (opcional, puedes dejar vacío y editar luego): " PLAIN
        if [[ -n "$PLAIN" ]]; then
          cat > .env <<EOF
BOT_TOKEN=${PLAIN}
USE_UVLOOP=false
PERSISTENCE_PATH=data/persistence.pkl
EOF
          echo ".env creado con BOT_TOKEN en claro."
        else
          cp .env.example .env || true
          echo "Usando .env.example. Edita .env para añadir el token."
        fi
        ;;
    esac
  fi
fi

# 4) Validación
echo "Ejecutando dry-run..."
$PY -m bot.main --dry-run || {
  echo "Dry-run falló. Revisa que .env tenga el token válido."
  exit 1
}

echo "Listo. Para iniciar el bot:"
echo "  $PY -m bot.main"