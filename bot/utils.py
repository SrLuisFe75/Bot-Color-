from __future__ import annotations

from typing import Dict, Tuple
from PIL import Image, ImageDraw, ImageFont


class ColorMixingUtils:
    @staticmethod
    def hex_to_rgb(hex_color: str) -> Tuple[int, int, int]:
        hex_color = hex_color.lstrip('#')
        return tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4))  # type: ignore[return-value]

    @staticmethod
    def rgb_to_hex(rgb: Tuple[int, int, int]) -> str:
        return f"#{rgb[0]:02x}{rgb[1]:02x}{rgb[2]:02x}"

    @staticmethod
    def calculate_professional_mix(color1: Dict, color2: Dict) -> Dict:
        level1, level2 = int(color1['level']), int(color2['level'])

        if abs(level1 - level2) <= 1:
            result_level = (level1 + level2) / 2
            mixing_ratio = "1:1"
            developer_vol = 30 if result_level >= 7 else 20
        elif level1 > level2:
            result_level = level1 - 0.5
            mixing_ratio = "2:1 (más del color claro)"
            developer_vol = 30
        else:
            result_level = level2 - 0.5
            mixing_ratio = "1:2 (más del color oscuro)"
            developer_vol = 20

        if result_level >= 8:
            processing_time = "35-40 minutos"
        elif result_level >= 6:
            processing_time = "30-35 minutos"
        else:
            processing_time = "25-30 minutos"

        recommendations = []
        name1 = str(color1.get('name', '')).lower()
        name2 = str(color2.get('name', '')).lower()
        if result_level >= 8:
            recommendations.append("Requiere decoloración previa")
        if 'ash' in name1 or 'ash' in name2:
            recommendations.append("Neutralizará tonos amarillos")
        if 'copper' in name1 or 'copper' in name2:
            recommendations.append("Resultado con reflejos cobrizos intensos")

        return {
            'result_level': round(result_level, 1),
            'mixing_ratio': mixing_ratio,
            'developer_volume': developer_vol,
            'processing_time': processing_time,
            'recommendations': recommendations,
            'color_family': ColorMixingUtils.determine_color_family(color1, color2),
        }

    @staticmethod
    def determine_color_family(color1: Dict, color2: Dict) -> str:
        names = (str(color1.get('name','')) + ' ' + str(color2.get('name',''))).lower()
        if 'copper' in names or 'red' in names:
            return "Cobrizo/Rojizo"
        if 'ash' in names or 'grey' in names:
            return "Ceniza"
        if 'gold' in names or 'honey' in names:
            return "Dorado"
        if 'blonde' in names:
            return "Rubio"
        if 'brown' in names:
            return "Castaño"
        return "Mixto"


def generate_color_preview_image(left_hex: str, right_hex: str | None = None, size=(768, 384)) -> Image.Image:
    width, height = size
    image = Image.new("RGB", size, (255, 255, 255))
    draw = ImageDraw.Draw(image)

    if right_hex:
        left_rgb = ColorMixingUtils.hex_to_rgb(left_hex)
        right_rgb = ColorMixingUtils.hex_to_rgb(right_hex)
        # Left half
        draw.rectangle([0, 0, width // 2, height], fill=left_rgb)
        # Right half
        draw.rectangle([width // 2, 0, width, height], fill=right_rgb)
    else:
        draw.rectangle([0, 0, width, height], fill=ColorMixingUtils.hex_to_rgb(left_hex))

    return image