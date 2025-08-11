from __future__ import annotations

from typing import Dict, List, Optional


ColorEntry = Dict[str, object]
DatabaseType = Dict[str, List[ColorEntry]]


def load_complete_hair_database() -> DatabaseType:
    # Sample extended database; replace with file-based load if desired
    return {
        "igora_royal": [
            {"code": "1-00", "name": "Natural Black", "brand": "Schwarzkopf", "level": 1, "hex": "#000000"},
            {"code": "2-00", "name": "Natural Brown Black", "brand": "Schwarzkopf", "level": 2, "hex": "#2F1B14"},
            {"code": "3-00", "name": "Natural Dark Brown", "brand": "Schwarzkopf", "level": 3, "hex": "#654321"},
            {"code": "4-00", "name": "Natural Medium Brown", "brand": "Schwarzkopf", "level": 4, "hex": "#8B4513"},
            {"code": "5-00", "name": "Natural Light Brown", "brand": "Schwarzkopf", "level": 5, "hex": "#A0522D"},
            {"code": "6-00", "name": "Natural Dark Blonde", "brand": "Schwarzkopf", "level": 6, "hex": "#CD853F"},
            {"code": "7-00", "name": "Natural Medium Blonde", "brand": "Schwarzkopf", "level": 7, "hex": "#DAA520"},
            {"code": "8-00", "name": "Natural Light Blonde", "brand": "Schwarzkopf", "level": 8, "hex": "#F0E68C"},
            {"code": "9-00", "name": "Natural Extra Light Blonde", "brand": "Schwarzkopf", "level": 9, "hex": "#F5E6A3"},
            {"code": "10-00", "name": "Natural Ultra Light Blonde", "brand": "Schwarzkopf", "level": 10, "hex": "#FFF8DC"},
            {"code": "6-1", "name": "Dark Blonde Ash", "brand": "Schwarzkopf", "level": 6, "hex": "#C0C0C0"},
            {"code": "7-1", "name": "Medium Blonde Ash", "brand": "Schwarzkopf", "level": 7, "hex": "#D3D3D3"},
            {"code": "8-1", "name": "Light Blonde Ash", "brand": "Schwarzkopf", "level": 8, "hex": "#E5E5E5"},
            {"code": "9-1", "name": "Extra Light Blonde Ash", "brand": "Schwarzkopf", "level": 9, "hex": "#F0F0F0"},
            {"code": "6-77", "name": "Dark Blonde Copper Extra", "brand": "Schwarzkopf", "level": 6, "hex": "#B87333"},
            {"code": "7-77", "name": "Medium Blonde Copper Extra", "brand": "Schwarzkopf", "level": 7, "hex": "#CD7F32"},
            {"code": "8-77", "name": "Light Blonde Copper Extra", "brand": "Schwarzkopf", "level": 8, "hex": "#DAA520"},
            {"code": "6-5", "name": "Dark Blonde Gold", "brand": "Schwarzkopf", "level": 6, "hex": "#FFD700"},
            {"code": "7-5", "name": "Medium Blonde Gold", "brand": "Schwarzkopf", "level": 7, "hex": "#FFF700"},
            {"code": "8-5", "name": "Light Blonde Gold", "brand": "Schwarzkopf", "level": 8, "hex": "#FFFF99"},
        ],
        "wella_color_touch": [
            {"code": "5/37", "name": "Light Brown Gold Brown", "brand": "Wella", "level": 5, "hex": "#8B4513"},
            {"code": "6/37", "name": "Dark Blonde Gold Brown", "brand": "Wella", "level": 6, "hex": "#CD853F"},
            {"code": "6/43", "name": "Dark Blonde Red Gold", "brand": "Wella", "level": 6, "hex": "#B8860B"},
            {"code": "7/43", "name": "Medium Blonde Red Gold", "brand": "Wella", "level": 7, "hex": "#DAA520"},
            {"code": "8/43", "name": "Light Blonde Red Gold", "brand": "Wella", "level": 8, "hex": "#F0E68C"},
            {"code": "6/1", "name": "Dark Blonde Ash", "brand": "Wella", "level": 6, "hex": "#C0C0C0"},
            {"code": "7/1", "name": "Medium Blonde Ash", "brand": "Wella", "level": 7, "hex": "#D3D3D3"},
            {"code": "8/1", "name": "Light Blonde Ash", "brand": "Wella", "level": 8, "hex": "#E6E6FA"},
            {"code": "9/1", "name": "Very Light Blonde Ash", "brand": "Wella", "level": 9, "hex": "#F0F8FF"},
            {"code": "10/6", "name": "Lightest Blonde Violet", "brand": "Wella", "level": 10, "hex": "#DDA0DD"},
        ],
        "loreal_majirel": [
            {"code": "5.3", "name": "Light Golden Brown", "brand": "L'Oréal", "level": 5, "hex": "#B8860B"},
            {"code": "6.3", "name": "Dark Golden Blonde", "brand": "L'Oréal", "level": 6, "hex": "#DAA520"},
            {"code": "7.3", "name": "Golden Blonde", "brand": "L'Oréal", "level": 7, "hex": "#FFD700"},
            {"code": "8.3", "name": "Light Golden Blonde", "brand": "L'Oréal", "level": 8, "hex": "#FFEF94"},
            {"code": "7.1", "name": "Ash Blonde", "brand": "L'Oréal", "level": 7, "hex": "#D3D3D3"},
            {"code": "8.1", "name": "Light Ash Blonde", "brand": "L'Oréal", "level": 8, "hex": "#E6E6FA"},
            {"code": "9.1", "name": "Very Light Ash Blonde", "brand": "L'Oréal", "level": 9, "hex": "#F0F8FF"},
            {"code": "6.35", "name": "Dark Golden Mahogany Blonde", "brand": "L'Oréal", "level": 6, "hex": "#CD853F"},
        ],
    }


def list_brands(db: DatabaseType) -> List[str]:
    return list(db.keys())


def lookup_color_by_code(db: DatabaseType, code: str) -> Optional[ColorEntry]:
    code_lower = code.lower()
    for brand, colors in db.items():
        for entry in colors:
            if str(entry.get("code", "")).lower() == code_lower:
                return entry
    return None


def search_colors(db: DatabaseType, query: str, limit: int = 10) -> List[ColorEntry]:
    if not query:
        return []
    q = query.lower()
    results: List[ColorEntry] = []
    for brand, colors in db.items():
        for entry in colors:
            hay = f"{entry.get('code','')} {entry.get('name','')} {entry.get('brand','')}".lower()
            if q in hay:
                results.append(entry)
                if len(results) >= limit:
                    return results
    return results