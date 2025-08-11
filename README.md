# Hair Color Mixing Telegram Bot

Bot de Telegram para buscar y mezclar colores profesionales de cabello (Igora, Wella, L'Oréal), guardar favoritos y ver previsualizaciones.

## Requisitos
- Python 3.10+
- Token de bot de Telegram (creado con `@BotFather`)

## Configuración
1. Crear entorno e instalar dependencias:
   ```bash
   python -m venv .venv && source .venv/bin/activate
   pip install -r requirements.txt
   ```
2. Copiar `.env.example` a `.env` y completar el `BOT_TOKEN`.

## Ejecutar
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
