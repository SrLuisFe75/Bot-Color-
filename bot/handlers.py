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
from .utils import ColorMixingUtils, generate_color_preview_image, generate_palette_image, generate_mix_composite_image


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
        self.app.add_handler(CommandHandler("palette", self.palette_command))

        self.app.add_handler(MessageHandler(filters.PHOTO, self.handle_photo))

        self.app.add_handler(CallbackQueryHandler(self.on_callback_query))

        self.app.add_error_handler(self.error_handler)

    # Commands
    async def start_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        keyboard = [
            [InlineKeyboardButton("🎨 Paleta", callback_data="menu:palette"), InlineKeyboardButton("🔎 Buscar", callback_data="menu:search")],
            [InlineKeyboardButton("🎭 Mezclar", callback_data="menu:mix"), InlineKeyboardButton("💖 Favoritos", callback_data="menu:favorites")],
            [InlineKeyboardButton("📸 Simular", callback_data="menu:simulate"), InlineKeyboardButton("❓ Ayuda", callback_data="menu:help")],
        ]
        await update.message.reply_text(
            "Bienvenida 💜 Elige una opción:", reply_markup=InlineKeyboardMarkup(keyboard)
        )

    async def help_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        await update.message.reply_text(
            """
Cómo usar el bot:
• /palette – Explora paletas por marca
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
        await self._send_mix_result(update, context, code1, code2)

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

    async def palette_command(self, update: Update, context: ContextTypes.DEFAULT_TYPE) -> None:
        brands = list(self.db.keys())
        keyboard = [[InlineKeyboardButton("🌈 Todas", callback_data="palette_brand:all")]]
        for b in brands:
            keyboard.append([InlineKeyboardButton(b, callback_data=f"palette_brand:{b}")])
        await update.message.reply_text("Elige una marca:", reply_markup=InlineKeyboardMarkup(keyboard))

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

        # Menu shortcuts
        if data.startswith("menu:"):
            action = data.split(":", 1)[1]
            if action == "palette":
                await self.palette_command(query, context)  # type: ignore[arg-type]
                return
            if action == "search":
                await query.message.reply_text("Usa /search <término>")
                return
            if action == "mix":
                await query.message.reply_text("Usa /mix <code1> <code2>")
                return
            if action == "favorites":
                fake_update = Update(update.update_id, message=query.message)  # type: ignore[arg-type]
                await self.favorites_command(fake_update, context)
                return
            if action == "simulate":
                fake_update = Update(update.update_id, message=query.message)  # type: ignore[arg-type]
                await self.simulate_command(fake_update, context)
                return
            if action == "help":
                fake_update = Update(update.update_id, message=query.message)  # type: ignore[arg-type]
                await self.help_command(fake_update, context)
                return

        if data.startswith("palette_brand:"):
            brand = data.split(":", 1)[1]
            context.user_data["palette_brand"] = brand
            context.user_data["palette_page"] = 0
            await self.send_palette_page(query, context)
            return

        if data.startswith("palette_page:" ):
            direction = data.split(":", 1)[1]
            page = int(context.user_data.get("palette_page", 0))
            total = len(self._get_palette_list(context))
            page_size = 8
            max_page = max(0, (total - 1) // page_size)
            if direction == "prev" and page > 0:
                context.user_data["palette_page"] = page - 1
            elif direction == "next" and page < max_page:
                context.user_data["palette_page"] = page + 1
            await self.send_palette_page(query, context)
            return

        if data.startswith("palette_pick:"):
            code = data.split(":", 1)[1]
            mix_mode = bool(context.user_data.get("mix_mode", False))
            if mix_mode:
                first = context.user_data.get("mix_first")
                if not first:
                    context.user_data["mix_first"] = code
                    await query.message.reply_text(f"Primero seleccionado: {code}. Ahora elige el segundo.")
                else:
                    code1 = str(first)
                    code2 = code
                    # Reset
                    context.user_data["mix_first"] = None
                    # Prompt for ratio
                    keyboard = [[
                        InlineKeyboardButton("1:1", callback_data=f"mix_ratio:{code1}|{code2}|1|1"),
                        InlineKeyboardButton("2:1", callback_data=f"mix_ratio:{code1}|{code2}|2|1"),
                        InlineKeyboardButton("1:2", callback_data=f"mix_ratio:{code1}|{code2}|1|2"),
                    ],[
                        InlineKeyboardButton("3:1", callback_data=f"mix_ratio:{code1}|{code2}|3|1"),
                        InlineKeyboardButton("1:3", callback_data=f"mix_ratio:{code1}|{code2}|1|3"),
                    ]]
                    await query.message.reply_text("Elige proporción de mezcla:", reply_markup=InlineKeyboardMarkup(keyboard))
                return
            else:
                entry = lookup_color_by_code(self.db, code)
                if not entry:
                    await query.message.reply_text("No encontré ese color.")
                    return
                img = generate_color_preview_image(str(entry["hex"]))
                bio = self.image_to_bytes(img)
                keyboard = [
                    [InlineKeyboardButton("💖 Guardar", callback_data=f"save_color:{code}")],
                    [InlineKeyboardButton("🎭 Mezclar con...", callback_data=f"mix_prompt:{code}")],
                ]
                await query.message.reply_photo(
                    photo=bio,
                    caption=f"{entry['brand']} {entry['code']} – {entry['name']}\n{entry['hex']}",
                    reply_markup=InlineKeyboardMarkup(keyboard),
                )
                return

        if data == "palette_toggle_mix":
            current = bool(context.user_data.get("mix_mode", False))
            context.user_data["mix_mode"] = not current
            mode = "ON" if not current else "OFF"
            await query.message.reply_text(f"Modo mezcla: {mode}")
            return

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

        if data.startswith("mix_ratio:"):
            payload = data.split(":", 1)[1]
            c1, c2, w1, w2 = payload.split("|")
            await self.send_guided_mix(query, context, c1, c2, float(w1), float(w2))
            return

        if data == "clear_favorites":
            context.user_data["favorites"] = []
            await query.message.reply_text("Favoritos limpiados.")
            return

    # Palette helpers
    def _get_palette_list(self, context: ContextTypes.DEFAULT_TYPE) -> List[Dict]:
        brand = context.user_data.get("palette_brand", "all")
        if brand == "all":
            items: List[Dict] = []
            for _, colors in self.db.items():
                items.extend(colors)
            return items
        return list(self.db.get(brand, []))

    async def send_palette_page(self, query, context: ContextTypes.DEFAULT_TYPE) -> None:
        page = int(context.user_data.get("palette_page", 0))
        items = self._get_palette_list(context)
        img = generate_palette_image(items, page=page, page_size=8, columns=4)
        bio = self.image_to_bytes(img)

        start = page * 8
        subset = items[start:start + 8]
        # Buttons: one per color, then nav + mix toggle
        rows: List[List[InlineKeyboardButton]] = []
        for entry in subset:
            code = str(entry.get("code"))
            label = f"{entry.get('brand','')} {code}"
            rows.append([InlineKeyboardButton(label, callback_data=f"palette_pick:{code}")])

        total = len(items)
        max_page = max(0, (total - 1) // 8)
        nav = []
        nav.append(InlineKeyboardButton("⬅️", callback_data="palette_page:prev"))
        nav.append(InlineKeyboardButton(f"{page+1}/{max_page+1}", callback_data="noop"))
        nav.append(InlineKeyboardButton("➡️", callback_data="palette_page:next"))
        rows.append(nav)
        rows.append([InlineKeyboardButton("🎭 Modo mezcla ON/OFF", callback_data="palette_toggle_mix")])

        if query.message and query.message.photo:
            await query.message.reply_photo(photo=bio, caption="Elige un color:", reply_markup=InlineKeyboardMarkup(rows))
        else:
            await query.message.reply_photo(photo=bio, caption="Elige un color:", reply_markup=InlineKeyboardMarkup(rows))

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

    async def _send_mix_result(self, update: Update, context: ContextTypes.DEFAULT_TYPE, code1: str, code2: str) -> None:
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

    async def send_guided_mix(self, query, context: ContextTypes.DEFAULT_TYPE, code1: str, code2: str, w1: float, w2: float) -> None:
        col1 = lookup_color_by_code(self.db, code1)
        col2 = lookup_color_by_code(self.db, code2)
        if not col1 or not col2:
            await query.message.reply_text("No se encontraron uno o ambos códigos.")
            return

        # Calculate professional guidance
        details = ColorMixingUtils.calculate_professional_mix(col1, col2)
        # Visual mix preview based on selected ratio
        hex1 = str(col1['hex'])
        hex2 = str(col2['hex'])
        mixed_hex = ColorMixingUtils.mix_hex_colors(hex1, hex2, w1, w2)
        img = generate_mix_composite_image(hex1, hex2, mixed_hex)
        bio = self.image_to_bytes(img)

        # Default total volume suggestion
        total_ml = 60  # typical tube mix size reference
        ml1 = round(total_ml * (w1 / (w1 + w2)))
        ml2 = total_ml - ml1

        caption = (
            f"🎭 Mezcla guiada: {col1['code']} (A) + {col2['code']} (B)\n"
            f"Proporción elegida: {int(w1)}:{int(w2)}\n"
            f"Sugerencia: {ml1} ml de A + {ml2} ml de B\n"
            f"Peróxido recomendado: {details['developer_volume']} vol\n"
            f"Tiempo: {details['processing_time']}\n"
            f"Familia: {details['color_family']}\n"
        )
        tips = [
            "Mezcla primero los tintes hasta homogeneizar antes de añadir peróxido",
            "Usa balanza o jeringa para precisión en ml",
            "Haz prueba de mechón si dudas del resultado",
        ]
        caption += "\n" + "\n".join(f"• {t}" for t in tips)

        keyboard = [
            [InlineKeyboardButton("💖 Guardar mezcla", callback_data=f"save_mix:{col1['code']}|{col2['code']}")]
        ]
        await query.message.reply_photo(photo=bio, caption=caption, reply_markup=InlineKeyboardMarkup(keyboard))

    def run(self) -> None:
        LOGGER.info("Iniciando Hair Color Mixing Bot...")
        self.app.run_polling(drop_pending_updates=True, allowed_updates=Update.ALL_TYPES)