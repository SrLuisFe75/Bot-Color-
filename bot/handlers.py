from __future__ import annotations

import asyncio
import logging
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional

from telegram import (
    InlineKeyboardButton,
    InlineKeyboardMarkup,
    Update,
    InputMediaPhoto,
)
from telegram.constants import ParseMode
from telegram.ext import (
    Application,
    ApplicationBuilder,
    CallbackQueryHandler,
    CommandHandler,
    ContextTypes,
    MessageHandler,
    PicklePersistence,
    filters,
)

from .config import load_settings
from .database import load_complete_hair_database, list_brands, search_colors, lookup_color_by_code
from .utils import ColorMixingUtils, generate_color_preview_image


LOGGER = logging.getLogger(__name__)


@dataclass
class FavoritesEntry:
    type: str
    code: Optional[str] = None
    color1: Optional[str] = None
    color2: Optional[str] = None


class HairColorBot:
    def __init__(self) -> None:
        settings = load_settings()

        # Optional: uvloop for performance on Linux
        if settings.use_uvloop:
            try:
                import uvloop  # type: ignore

                uvloop.install()
            except Exception:  # pragma: no cover
                LOGGER.debug("uvloop not available; continuing with default event loop")

        # Ensure data dir exists
        persistence_path = Path(settings.persistence_path)
        persistence_path.parent.mkdir(parents=True, exist_ok=True)

        self.persistence = PicklePersistence(filepath=str(persistence_path))
        self.app: Application = (
            ApplicationBuilder()
            .token(settings.bot_token)
            .persistence(self.persistence)
            .concurrent_updates(True)
            .build()
        )

        self.db = load_complete_hair_database()

        # Register handlers
        self.app.add_handler(CommandHandler("start", self.start_command))
        self.app.add_handler(CommandHandler("help", self.help_command))
        self.app.add_handler(CommandHandler("brands", self.brands_command))
        self.app.add_handler(CommandHandler("search", self.search_command))
        self.app.add_handler(CommandHandler("mix", self.mix_command))
        self.app.add_handler(CommandHandler("favorites", self.favorites_command))
        self.app.add_handler(CommandHandler("simulate", self.simulate_command))

        self.app.add_handler(MessageHandler(filters.PHOTO, self.handle_photo))

        self.app.add_handler(CallbackQueryHandler(self.on_callback_query))

        self.app.add_error_handler(self.error_handler)

    # Commands
    async def start_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        await update.message.reply_text(
            """
💜 ¡Bienvenida al Hair Color Mixing Bot!

Comandos disponibles:
• /search <término> – Buscar colores
• /mix <code1> <code2> – Mezclar colores por código
• /brands – Ver marcas
• /favorites – Ver tus favoritos
• /simulate – Simulador (beta)
• /help – Ayuda
            """.strip()
        )

    async def help_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        await update.message.reply_text(
            """
Cómo usar el bot:
• /search rubio – Encuentra tonos que coincidan
• /mix 7-1 7-5 – Mezcla profesional con proporciones y peróxido
• Pulsa 💖 para guardar favoritos
• /favorites – Ver, limpiar y volver a mezclar
            """.strip()
        )

    async def brands_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        brands = list_brands(self.db)
        await update.message.reply_text("Marcas disponibles:\n" + "\n".join(f"• {b}" for b in brands))

    async def search_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        query = " ".join(context.args).strip()
        if not query:
            await update.message.reply_text("Uso: /search <término>")
            return
        results = search_colors(self.db, query, limit=6)
        if not results:
            await update.message.reply_text("No encontré coincidencias.")
            return

        for entry in results:
            code = str(entry.get("code"))
            name = str(entry.get("name"))
            brand = str(entry.get("brand"))
            hex_color = str(entry.get("hex"))

            image = generate_color_preview_image(hex_color)
            bio = self.image_to_bytes(image)

            keyboard = [
                [InlineKeyboardButton("💖 Guardar", callback_data=f"save_color:{code}")],
                [InlineKeyboardButton("🎭 Mezclar con...", callback_data=f"mix_prompt:{code}")],
            ]
            await update.message.reply_photo(
                photo=bio,
                caption=f"{brand} {code} – {name}\n{hex_color}",
                reply_markup=InlineKeyboardMarkup(keyboard),
            )

    async def mix_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        if len(context.args) < 2:
            await update.message.reply_text("Uso: /mix <code1> <code2>")
            return
        code1, code2 = context.args[0], context.args[1]
        col1 = lookup_color_by_code(self.db, code1)
        col2 = lookup_color_by_code(self.db, code2)
        if not col1 or not col2:
            await update.message.reply_text("No se encontraron uno o ambos códigos.")
            return

        details = ColorMixingUtils.calculate_professional_mix(col1, col2)
        left = str(col1["hex"])
        right = str(col2["hex"])
        img = generate_color_preview_image(left, right)
        bio = self.image_to_bytes(img)

        caption = (
            f"🎭 Mezcla: {col1['code']} + {col2['code']}\n"
            f"Nivel objetivo: {details['result_level']}\n"
            f"Proporción: {details['mixing_ratio']}\n"
            f"Peróxido: {details['developer_volume']} vol\n"
            f"Tiempo: {details['processing_time']}\n"
            f"Familia: {details['color_family']}\n"
        )
        recs = details.get("recommendations", [])
        if recs:
            caption += "\n" + "\n".join(f"• {r}" for r in recs)

        keyboard = [
            [InlineKeyboardButton("💖 Guardar mezcla", callback_data=f"save_mix:{col1['code']}|{col2['code']}")]
        ]
        await update.message.reply_photo(photo=bio, caption=caption, reply_markup=InlineKeyboardMarkup(keyboard))

    async def favorites_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        user_data = context.user_data.setdefault("favorites", [])
        if not user_data:
            await update.message.reply_text(
                "No tienes favoritos aún. Guarda colores con el botón 💖 o usa /mix para crear mezclas."
            )
            return

        lines: List[str] = [f"💖 TUS FAVORITOS ({len(user_data)})", ""]
        for i, fav in enumerate(user_data[-10:], 1):
            if fav.get("type") == "mix":
                lines.append(f"{i}. 🎭 {fav.get('color1')} + {fav.get('color2')}")
            else:
                lines.append(f"{i}. 🎨 {fav.get('code')}")

        keyboard = [
            [InlineKeyboardButton("🗑️ Limpiar", callback_data="clear_favorites")]
        ]
        await update.message.reply_text("\n".join(lines), reply_markup=InlineKeyboardMarkup(keyboard))

    async def simulate_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        await update.message.reply_text(
            """
📸 SIMULADOR DE COLOR (beta)
• Sube una foto y te guiaré. Por ahora es un placeholder.
• Usa /mix y /search mientras tanto.
            """.strip()
        )

    async def handle_photo(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        await update.message.reply_text(
            "📸 Recibí tu foto. El simulador aún está en desarrollo, pronto podrás visualizar colores sobre tu imagen."
        )

    # Callback queries
    async def on_callback_query(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        query = update.callback_query
        if not query:
            return
        await query.answer()

        data = query.data or ""
        if data.startswith("save_color:"):
            code = data.split(":", 1)[1]
            favorites: List[Dict] = context.user_data.setdefault("favorites", [])
            favorites.append({"type": "color", "code": code})
            await query.message.reply_text(f"Guardado en favoritos: {code}")
            return

        if data.startswith("save_mix:"):
            payload = data.split(":", 1)[1]
            if "|" in payload:
                c1, c2 = payload.split("|", 1)
                favorites: List[Dict] = context.user_data.setdefault("favorites", [])
                favorites.append({"type": "mix", "color1": c1, "color2": c2})
                await query.message.reply_text(f"Mezcla guardada: {c1} + {c2}")
            return

        if data.startswith("mix_prompt:"):
            code = data.split(":", 1)[1]
            await query.message.reply_text(
                f"Escribe ahora /mix {code} <otro_código> para mezclar."
            )
            return

        if data == "clear_favorites":
            context.user_data["favorites"] = []
            await query.message.reply_text("Favoritos limpiados.")
            return

    # Error handling
    async def error_handler(self, update: object, context: ContextTypes.DEFAULT_TYPE) -> None:
        LOGGER.exception("Exception while handling update", exc_info=context.error)

    # Utilities
    @staticmethod
    def image_to_bytes(image) -> bytes:
        import io

        bio = io.BytesIO()
        image.save(bio, format="PNG")
        bio.seek(0)
        return bio

    def run(self) -> None:
        LOGGER.info("Iniciando Hair Color Mixing Bot...")
        self.app.run_polling(drop_pending_updates=True, allowed_updates=Update.ALL_TYPES)