# ICON

ICON (Intelligent Conversational Operating Nexus) is a native Kotlin and Jetpack Compose app (`com.icon.nexus`). Phase 1 is the Android Studio project and a local demo shell. It is not a WebView.

## Phase 1

- min SDK 26, target SDK 35, compile SDK 35, single module `:app`
- `MainActivity` and a Compose screen that shows the word ICON, the current `AppState` name, and a mic button
- The mic button cycles a local state machine only: Idle → Listening → Thinking → Speaking → Idle. Long-press the mic while Speaking interrupts into Listening. No network.
- `DemoProvider` is the default model boundary. `GeminiProvider` reads the API key from encrypted settings and fails the turn when that key is blank. The key is not a source constant.
- Contracts for speech input, speech synthesis, the visualizer, cinematic shots, conversation history, and opt-in memory

## Phase 2

Visual identity on the phase 1 shell. Material 3 dark theme, Outfit bundled in the APK (no runtime font download), and a procedural Canvas presence on the main screen. The mic still only cycles the local demo.

## Phase 3

Main screen on top of that identity. Full-bleed presence, a quiet status line, one mic control, and a slim cluster for the transcript, cinematic chrome, and a short settings sheet (demo mode and whether the transcript starts visible). Transcript lines are fixed local sentences.

## Phase 4

Timed local demo. A mic tap listens, thinks, speaks a fixed sentence with a smoothed voice level on the presence, then returns to Idle. Long-press while Idle previews Alert. Long-press while Speaking interrupts that turn.

## Phase 5

Text chat with Gemini when demo mode is off. A single-line field and Send stream a reply into the ICON line, then return to Idle. The API key stays in encrypted settings. The timed demo is unchanged while demo mode is on.

Speech recognition, Android text-to-speech, and cinematic playback are later phases.

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Hair Color Mixing Telegram Bot

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

## Interfaz intuitiva para estilistas
- Menú principal con botones: Paleta, Buscar, Mezclar, Favoritos, Simular, Ayuda
- Paleta de colores (`/palette`):
  - Elige marca o “Todas”
  - Vista de paleta con 8 muestras por página (imagen 4x2) con botones por color
  - Navegación ⬅️ ➡️ y “Modo mezcla” para elegir dos colores desde la paleta

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
- `/start` – Menú de acceso rápido
- `/palette` – Paleta por marca con navegación y modo mezcla
- `/search <término>` – Buscar tonos
- `/mix <code1> <code2>` – Mezclar colores con cálculo profesional
- `/favorites` – Ver y limpiar favoritos
- `/simulate` – Simulador (placeholder)

## Persistencia
Se usa `PicklePersistence` en `data/persistence.pkl` para guardar favoritos por usuario.

## Notas
- Puedes ampliar la base de datos en `bot/database.py` o cargar desde JSON.
