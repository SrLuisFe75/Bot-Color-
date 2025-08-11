# Hair Color Mixing Telegram Bot

Bot de Telegram para buscar y mezclar colores profesionales de cabello (Igora, Wella, L'Oréal), guardar favoritos y ver previsualizaciones.

## Requisitos
- Python 3.10+
- Token de bot de Telegram (creado con `@BotFather`)

## Instalación rápida
- Script automático:
  ```bash
  bash scripts/setup.sh
  ```
- Makefile:
  ```bash
  make setup      # instala dependencias y prepara .env
  make dry-run    # valida configuración
  make run        # inicia el bot
  ```

## Cómo proteger tu token (recomendado)
Puedes almacenar el token cifrado con una contraseña. El bot descifra en runtime.

1) Generar el cifrado:
```bash
python -m scripts.seal_token <TU_TOKEN> <TU_CONTRASEÑA>
```
Esto imprimirá dos líneas para pegar en `.env`:
```
BOT_TOKEN_ENC=...
DECRYPT_PASSWORD=...
```
2) Asegúrate de NO commitear `.env` (ya está en `.gitignore`).
3) Alternativamente, puedes usar `BOT_TOKEN` en claro en `.env`, pero no es recomendable.

## Docker
- Build:
  ```bash
  docker build -t hair-color-bot .
  ```
- Run pasando variables de entorno (recomendado usar un archivo `.env`):
  ```bash
  docker run --env-file .env --name hair-bot --rm hair-color-bot
  ```

## Ejecutar sin Docker
- Validar configuración sin iniciar el bot:
  ```bash
  python -m bot.main --dry-run
  ```
- Iniciar el bot:
  ```bash
  python -m bot.main
  ```

## Comandos
- `/start` – Bienvenida
- `/help` – Ayuda
- `/brands` – Marcas disponibles
- `/search <término>` – Buscar tonos
- `/mix <code1> <code2>` – Mezclar colores con cálculo profesional
- `/favorites` – Ver y limpiar favoritos
- `/simulate` – Simulador (placeholder)

## Persistencia
Se usa `PicklePersistence` en `data/persistence.pkl` para guardar favoritos por usuario.

## Notas
- El archivo antiguo `Color Bot` se ha reemplazado por una estructura de paquete Python bajo `bot/`.
- Puedes ampliar la base de datos en `bot/database.py` o cargar desde JSON. 
