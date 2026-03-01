#!/usr/bin/env python3
"""
Add images to products in the database.
Uses .env for DB credentials. Updates products.images with JSON array of Unsplash URLs.
Products get category-appropriate images when category_id is available.

Usage: ./scripts/add-product-images.py
"""
import json
import os
import sys
import urllib.parse
from pathlib import Path

# Load .env
REPO_ROOT = Path(__file__).resolve().parent.parent
env_file = REPO_ROOT / ".env"
if env_file.exists():
    with open(env_file) as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, _, val = line.partition("=")
                os.environ.setdefault(key.strip(), val.strip().strip('"'))

DB_HOST = os.environ.get("DB_HOST", "localhost")
DB_PORT = int(os.environ.get("DB_PORT", "3306"))
DB_NAME = os.environ.get("DB_NAME", "inventory")
DB_USERNAME = os.environ.get("DB_USERNAME", "root")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "root")

try:
    import mysql.connector
except ImportError:
    print("Installing mysql-connector-python...")
    import subprocess
    subprocess.check_call([sys.executable, "-m", "pip", "install", "mysql-connector-python", "-q"])
    import mysql.connector


def main():
    conn = mysql.connector.connect(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USERNAME,
        password=DB_PASSWORD,
        database=DB_NAME,
    )
    cursor = conn.cursor(dictionary=True)

    cursor.execute("SELECT id, name, sku, category_id FROM products")
    products = cursor.fetchall()

    # Category ID -> Unsplash image URL (same mapping as seed-bulk-products.py)
    CATEGORY_IMAGES = {
        11: "https://images.unsplash.com/photo-1619566636858-adf3ef46400b",
        12: "https://images.unsplash.com/photo-1597362925123-77861d3fbac7",
        13: "https://images.unsplash.com/photo-1528825871115-3581a5387919",
        14: "https://images.unsplash.com/photo-1622206151226-18ca2c9ab4a1",
        15: "https://images.unsplash.com/photo-1488459716781-31db52582fe9",
        16: "https://images.unsplash.com/photo-1563636619-e9143da7973b",
        17: "https://images.unsplash.com/photo-1509440159596-0249088772ff",
        18: "https://images.unsplash.com/photo-1582722872445-44dc5f7e3c8f",
        19: "https://images.unsplash.com/photo-1589985270826-4b7bb135bc9d",
        20: "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d",
        21: "https://images.unsplash.com/photo-1631452180519-c014fe946bc7",
        22: "https://images.unsplash.com/photo-1488477181946-6428a0291777",
        23: "https://images.unsplash.com/photo-1621939514649-280e2ee25f60",
        24: "https://images.unsplash.com/photo-1581006852262-e4307cf6283a",
        25: "https://images.unsplash.com/photo-1600271886742-f049cd451bba",
        26: "https://images.unsplash.com/photo-1544787219-7f47ccb76574",
        27: "https://images.unsplash.com/photo-1622483767028-3f66f32aef97",
        28: "https://images.unsplash.com/photo-1548839140-29a749e1cf4d",
        29: "https://images.unsplash.com/photo-1517466787929-bc90951d0974",
        30: "https://images.unsplash.com/photo-1566478989037-eec170784d0b",
        31: "https://images.unsplash.com/photo-1558961363-fa8fdf82db35",
        32: "https://images.unsplash.com/photo-1551892374-ecf8754cf8b0",
        33: "https://images.unsplash.com/photo-1609501676725-7186f017a4b7",
        34: "https://images.unsplash.com/photo-1481391319762-47dff72954d9",
        35: "https://images.unsplash.com/photo-1601524909162-ae8725290836",
        36: "https://images.unsplash.com/photo-1600857062241-98e5dba60f2f",
        37: "https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d",
        38: "https://images.unsplash.com/photo-1607613009820-a29f7bb81c04",
        39: "https://images.unsplash.com/photo-1556228578-8c89e6adf883",
        40: "https://images.unsplash.com/photo-1621607512214-68297480165e",
        41: "https://images.unsplash.com/photo-1583947581924-860bda6a26df",
        42: "https://images.unsplash.com/photo-1571875257727-256c39da42af",
        43: "https://images.unsplash.com/photo-1610557892470-55d9e80c0bce",
        44: "https://images.unsplash.com/photo-1563453392212-326f5e854473",
        45: "https://images.unsplash.com/photo-1600353068440-6361ef3a86e8",
        46: "https://images.unsplash.com/photo-1625667626161-d18df8b36968",
        47: "https://images.unsplash.com/photo-1585128723678-e40485025c46",
        48: "https://images.unsplash.com/photo-1551030173-122aabc4489c",
        49: "https://images.unsplash.com/photo-1617788138017-80ad40651399",
        50: "https://images.unsplash.com/photo-1598639279843-644def8d82ad",
        51: "https://images.unsplash.com/photo-1612349317150-e413f6a5b16d",
        52: "https://images.unsplash.com/photo-1596461404969-9ae70f2830c1",
        53: "https://images.unsplash.com/photo-1587593810167-a84920ea0781",
        54: "https://images.unsplash.com/photo-1602470520998-f4a52199a3d6",
        55: "https://images.unsplash.com/photo-1535591273668-578e31182c4f",
        56: "https://images.unsplash.com/photo-1539274989475-c75edd2f81c9",
        57: "https://images.unsplash.com/photo-1578985545062-69928b1d9587",
        58: "https://images.unsplash.com/photo-1499636136210-6f4ee915583e",
        59: "https://images.unsplash.com/photo-1549931319-a545dcf3bc73",
        60: "https://images.unsplash.com/photo-1513104890138-7c749659a591",
        61: "https://images.unsplash.com/photo-1563805042-7684c019e1cb",
        62: "https://images.unsplash.com/photo-1506617420156-8e4536971650",
        63: "https://images.unsplash.com/photo-1630367462565-a355ddd6b3d8",
        64: "https://images.unsplash.com/photo-1603894584373-5ac82b2ae398",
    }
    DEFAULT_IMAGE = "https://images.unsplash.com/photo-1556742049-0cfed4f6a45d"

    updated = 0
    for p in products:
        cat_id = p.get("category_id")
        image_url = CATEGORY_IMAGES.get(cat_id, DEFAULT_IMAGE) if cat_id else DEFAULT_IMAGE
        images_json = json.dumps({"front": image_url, "back": None, "side": None})

        cursor.execute(
            "UPDATE products SET images = %s WHERE id = %s",
            (images_json, p["id"]),
        )
        updated += 1

    conn.commit()
    cursor.close()
    conn.close()

    print(f"Updated {updated} products with Unsplash images.")


if __name__ == "__main__":
    main()
