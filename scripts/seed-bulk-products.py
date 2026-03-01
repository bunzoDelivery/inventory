#!/usr/bin/env python3
"""
Bulk sync products and inventory for all seeded categories.
Uses POST /api/v1/catalog/products/sync (max 500 items per request).
Sources .env for PRODUCT_SERVICE_URL. Requires store 1 to exist.

Usage: ./scripts/seed-bulk-products.py [base_url]
"""

import json
import os
import sys
import time
import urllib.request
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

BASE_URL = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("PRODUCT_SERVICE_URL", "http://localhost:8081")
STORE_ID = int(os.environ.get("STORE_ID", "1"))
API_URL = f"{BASE_URL}/api/v1/catalog/products/sync"

# Category ID -> list of Unsplash image URLs for products in that category
CATEGORY_IMAGES = {
    11: ["https://images.unsplash.com/photo-1619566636858-adf3ef46400b", "https://images.unsplash.com/photo-1528825871115-3581a5387919", "https://images.unsplash.com/photo-1597362925123-8d979f6a4567"],  # Fresh Fruits
    12: ["https://images.unsplash.com/photo-1597362925123-77861d3fbac7", "https://images.unsplash.com/photo-1622206151226-18ca2c9ab4a1", "https://images.unsplash.com/photo-1540420773420-3366772f4994"],  # Fresh Vegetables
    13: ["https://images.unsplash.com/photo-1528825871115-3581a5387919", "https://images.unsplash.com/photo-1585059895524-72359e06133a", "https://images.unsplash.com/photo-1585059895524-72359e06133a"],  # Exotic Fruits
    14: ["https://images.unsplash.com/photo-1622206151226-18ca2c9ab4a1", "https://images.unsplash.com/photo-1512621776951-a57141f2eefd", "https://images.unsplash.com/photo-1540420773420-3366772f4994"],  # Leafy Greens
    15: ["https://images.unsplash.com/photo-1488459716781-31db52582fe9", "https://images.unsplash.com/photo-1619566636858-adf3ef46400b", "https://images.unsplash.com/photo-1597362925123-77861d3fbac7"],  # Organic Produce
    16: ["https://images.unsplash.com/photo-1563636619-e9143da7973b", "https://images.unsplash.com/photo-1550583724-b2692b85b150", "https://images.unsplash.com/photo-1550583724-b2692b85b150"],  # Milk
    17: ["https://images.unsplash.com/photo-1509440159596-0249088772ff", "https://images.unsplash.com/photo-1549931319-a545dcf3bc73", "https://images.unsplash.com/photo-1509440159596-0249088772ff"],  # Bread & Pav
    18: ["https://images.unsplash.com/photo-1582722872445-44dc5f7e3c8f", "https://images.unsplash.com/photo-1569127959161-3b6bb32f8e6c", "https://images.unsplash.com/photo-1582722872445-44dc5f7e3c8f"],  # Eggs
    19: ["https://images.unsplash.com/photo-1589985270826-4b7bb135bc9d", "https://images.unsplash.com/photo-1589985270826-4b7bb135bc9d", "https://images.unsplash.com/photo-1589985270826-4b7bb135bc9d"],  # Butter & Ghee
    20: ["https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d", "https://images.unsplash.com/photo-1452195100486-9cc805987862", "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d"],  # Cheese
    21: ["https://images.unsplash.com/photo-1631452180519-c014fe946bc7", "https://images.unsplash.com/photo-1541529086526-db283c563270", "https://images.unsplash.com/photo-1631452180519-c014fe946bc7"],  # Paneer & Tofu
    22: ["https://images.unsplash.com/photo-1488477181946-6428a0291777", "https://images.unsplash.com/photo-1571212515416-ffe4b2d2a119", "https://images.unsplash.com/photo-1488477181946-6428a0291777"],  # Yogurt & Curd
    23: ["https://images.unsplash.com/photo-1621939514649-280e2ee25f60", "https://images.unsplash.com/photo-1526318896980-cf78ec0887c3", "https://images.unsplash.com/photo-1621939514649-280e2ee25f60"],  # Breakfast Cereals
    24: ["https://images.unsplash.com/photo-1581006852262-e4307cf6283a", "https://images.unsplash.com/photo-1554866585-cd94860890b7", "https://images.unsplash.com/photo-1581006852262-e4307cf6283a"],  # Soft Drinks
    25: ["https://images.unsplash.com/photo-1600271886742-f049cd451bba", "https://images.unsplash.com/photo-1621506289937-a8e4df240d0b", "https://images.unsplash.com/photo-1600271886742-f049cd451bba"],  # Juices
    26: ["https://images.unsplash.com/photo-1544787219-7f47ccb76574", "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085", "https://images.unsplash.com/photo-1544787219-7f47ccb76574"],  # Tea & Coffee
    27: ["https://images.unsplash.com/photo-1622483767028-3f66f32aef97", "https://images.unsplash.com/photo-1554866585-cd94860890b7", "https://images.unsplash.com/photo-1622483767028-3f66f32aef97"],  # Energy Drinks
    28: ["https://images.unsplash.com/photo-1548839140-29a749e1cf4d", "https://images.unsplash.com/photo-1548839140-29a749e1cf4d", "https://images.unsplash.com/photo-1548839140-29a749e1cf4d"],  # Water
    29: ["https://images.unsplash.com/photo-1517466787929-bc90951d0974", "https://images.unsplash.com/photo-1571019613454-1cb2f99b2d8b", "https://images.unsplash.com/photo-1517466787929-bc90951d0974"],  # Health Drinks
    30: ["https://images.unsplash.com/photo-1566478989037-eec170784d0b", "https://images.unsplash.com/photo-1566478989037-eec170784d0b", "https://images.unsplash.com/photo-1566478989037-eec170784d0b"],  # Chips & Crisps
    31: ["https://images.unsplash.com/photo-1558961363-fa8fdf82db35", "https://images.unsplash.com/photo-1558961363-fa8fdf82db35", "https://images.unsplash.com/photo-1558961363-fa8fdf82db35"],  # Biscuits & Cookies
    32: ["https://images.unsplash.com/photo-1551892374-ecf8754cf8b0", "https://images.unsplash.com/photo-1569718212165-3a24449bc0a8", "https://images.unsplash.com/photo-1551892374-ecf8754cf8b0"],  # Noodles & Pasta
    33: ["https://images.unsplash.com/photo-1609501676725-7186f017a4b7", "https://images.unsplash.com/photo-1585032226651-759b368d7246", "https://images.unsplash.com/photo-1609501676725-7186f017a4b7"],  # Ready to Eat
    34: ["https://images.unsplash.com/photo-1481391319762-47dff72954d9", "https://images.unsplash.com/photo-1511381939415-e44015466834", "https://images.unsplash.com/photo-1481391319762-47dff72954d9"],  # Chocolates & Candies
    35: ["https://images.unsplash.com/photo-1601524909162-ae8725290836", "https://images.unsplash.com/photo-1601524909162-ae8725290836", "https://images.unsplash.com/photo-1601524909162-ae8725290836"],  # Namkeen & Mixtures
    36: ["https://images.unsplash.com/photo-1600857062241-98e5dba60f2f", "https://images.unsplash.com/photo-1600857062241-98e5dba60f2f", "https://images.unsplash.com/photo-1600857062241-98e5dba60f2f"],  # Soaps & Body Wash
    37: ["https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d", "https://images.unsplash.com/photo-1522338242992-e1a54906a8da", "https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d"],  # Hair Care
    38: ["https://images.unsplash.com/photo-1607613009820-a29f7bb81c04", "https://images.unsplash.com/photo-1607613009820-a29f7bb81c04", "https://images.unsplash.com/photo-1607613009820-a29f7bb81c04"],  # Oral Care
    39: ["https://images.unsplash.com/photo-1556228578-8c89e6adf883", "https://images.unsplash.com/photo-1556228578-8c89e6adf883", "https://images.unsplash.com/photo-1556228578-8c89e6adf883"],  # Skin Care
    40: ["https://images.unsplash.com/photo-1621607512214-68297480165e", "https://images.unsplash.com/photo-1621607512214-68297480165e", "https://images.unsplash.com/photo-1621607512214-68297480165e"],  # Men's Grooming
    41: ["https://images.unsplash.com/photo-1583947581924-860bda6a26df", "https://images.unsplash.com/photo-1583947581924-860bda6a26df", "https://images.unsplash.com/photo-1583947581924-860bda6a26df"],  # Feminine Hygiene
    42: ["https://images.unsplash.com/photo-1571875257727-256c39da42af", "https://images.unsplash.com/photo-1571875257727-256c39da42af", "https://images.unsplash.com/photo-1571875257727-256c39da42af"],  # Deodorants
    43: ["https://images.unsplash.com/photo-1610557892470-55d9e80c0bce", "https://images.unsplash.com/photo-1610557892470-55d9e80c0bce", "https://images.unsplash.com/photo-1610557892470-55d9e80c0bce"],  # Detergents & Fabric Care
    44: ["https://images.unsplash.com/photo-1563453392212-326f5e854473", "https://images.unsplash.com/photo-1563453392212-326f5e854473", "https://images.unsplash.com/photo-1563453392212-326f5e854473"],  # Cleaning Supplies
    45: ["https://images.unsplash.com/photo-1600353068440-6361ef3a86e8", "https://images.unsplash.com/photo-1600353068440-6361ef3a86e8", "https://images.unsplash.com/photo-1600353068440-6361ef3a86e8"],  # Dishwashing
    46: ["https://images.unsplash.com/photo-1625667626161-d18df8b36968", "https://images.unsplash.com/photo-1625667626161-d18df8b36968", "https://images.unsplash.com/photo-1625667626161-d18df8b36968"],  # Disposables & Garbage Bags
    47: ["https://images.unsplash.com/photo-1585128723678-e40485025c46", "https://images.unsplash.com/photo-1585128723678-e40485025c46", "https://images.unsplash.com/photo-1585128723678-e40485025c46"],  # Air Fresheners
    48: ["https://images.unsplash.com/photo-1551030173-122aabc4489c", "https://images.unsplash.com/photo-1551030173-122aabc4489c", "https://images.unsplash.com/photo-1551030173-122aabc4489c"],  # Batteries & Bulbs
    49: ["https://images.unsplash.com/photo-1617788138017-80ad40651399", "https://images.unsplash.com/photo-1617788138017-80ad40651399", "https://images.unsplash.com/photo-1617788138017-80ad40651399"],  # Diapers & Wipes
    50: ["https://images.unsplash.com/photo-1598639279843-644def8d82ad", "https://images.unsplash.com/photo-1598639279843-644def8d82ad", "https://images.unsplash.com/photo-1598639279843-644def8d82ad"],  # Baby Food
    51: ["https://images.unsplash.com/photo-1612349317150-e413f6a5b16d", "https://images.unsplash.com/photo-1612349317150-e413f6a5b16d", "https://images.unsplash.com/photo-1612349317150-e413f6a5b16d"],  # Baby Bath & Skin Care
    52: ["https://images.unsplash.com/photo-1596461404969-9ae70f2830c1", "https://images.unsplash.com/photo-1596461404969-9ae70f2830c1", "https://images.unsplash.com/photo-1596461404969-9ae70f2830c1"],  # Baby Accessories
    53: ["https://images.unsplash.com/photo-1587593810167-a84920ea0781", "https://images.unsplash.com/photo-1587593810167-a84920ea0781", "https://images.unsplash.com/photo-1587593810167-a84920ea0781"],  # Chicken & Poultry
    54: ["https://images.unsplash.com/photo-1602470520998-f4a52199a3d6", "https://images.unsplash.com/photo-1602470520998-f4a52199a3d6", "https://images.unsplash.com/photo-1602470520998-f4a52199a3d6"],  # Mutton & Lamb
    55: ["https://images.unsplash.com/photo-1535591273668-578e31182c4f", "https://images.unsplash.com/photo-1535591273668-578e31182c4f", "https://images.unsplash.com/photo-1535591273668-578e31182c4f"],  # Fish & Seafood
    56: ["https://images.unsplash.com/photo-1539274989475-c75edd2f81c9", "https://images.unsplash.com/photo-1539274989475-c75edd2f81c9", "https://images.unsplash.com/photo-1539274989475-c75edd2f81c9"],  # Sausages & Cold Cuts
    57: ["https://images.unsplash.com/photo-1578985545062-69928b1d9587", "https://images.unsplash.com/photo-1578985545062-69928b1d9587", "https://images.unsplash.com/photo-1578985545062-69928b1d9587"],  # Cakes & Pastries
    58: ["https://images.unsplash.com/photo-1499636136210-6f4ee915583e", "https://images.unsplash.com/photo-1499636136210-6f4ee915583e", "https://images.unsplash.com/photo-1499636136210-6f4ee915583e"],  # Cookies & Biscuits
    59: ["https://images.unsplash.com/photo-1549931319-a545dcf3bc73", "https://images.unsplash.com/photo-1549931319-a545dcf3bc73", "https://images.unsplash.com/photo-1549931319-a545dcf3bc73"],  # Bread & Rolls
    60: ["https://images.unsplash.com/photo-1513104890138-7c749659a591", "https://images.unsplash.com/photo-1513104890138-7c749659a591", "https://images.unsplash.com/photo-1513104890138-7c749659a591"],  # Pizza & Burger Buns
    61: ["https://images.unsplash.com/photo-1563805042-7684c019e1cb", "https://images.unsplash.com/photo-1563805042-7684c019e1cb", "https://images.unsplash.com/photo-1563805042-7684c019e1cb"],  # Ice Cream & Desserts
    62: ["https://images.unsplash.com/photo-1506617420156-8e4536971650", "https://images.unsplash.com/photo-1506617420156-8e4536971650", "https://images.unsplash.com/photo-1506617420156-8e4536971650"],  # Frozen Vegetables
    63: ["https://images.unsplash.com/photo-1630367462565-a355ddd6b3d8", "https://images.unsplash.com/photo-1630367462565-a355ddd6b3d8", "https://images.unsplash.com/photo-1630367462565-a355ddd6b3d8"],  # Frozen Snacks
    64: ["https://images.unsplash.com/photo-1603894584373-5ac82b2ae398", "https://images.unsplash.com/photo-1603894584373-5ac82b2ae398", "https://images.unsplash.com/photo-1603894584373-5ac82b2ae398"],  # Frozen Non-Veg
}

# Products: (sku, name, categoryId, basePrice, unitOfMeasure, currentStock, brand, packageSize)
PRODUCTS = [
    # Fresh Fruits (11) - 8 products
    ("FRUIT-APPLE-GALA-1KG", "Gala Apples 1kg", 11, 12.99, "1kg", 80, "Fresh Farms", "1kg"),
    ("FRUIT-BANANA-1DZ", "Bananas 1 Dozen", 11, 4.99, "dozen", 120, "Tropical", "12 pcs"),
    ("FRUIT-ORANGE-NAVEL-1KG", "Navel Oranges 1kg", 11, 8.99, "1kg", 60, "Citrus Fresh", "1kg"),
    ("FRUIT-MANGO-ALPHONSO-1KG", "Alphonso Mango 1kg", 11, 15.99, "1kg", 45, "Indian Harvest", "1kg"),
    ("FRUIT-GRAPES-GREEN-500G", "Green Grapes 500g", 11, 9.99, "500g", 70, "Vine Fresh", "500g"),
    ("FRUIT-WATERMELON-1PC", "Watermelon 1 Piece", 11, 6.99, "piece", 40, "Summer Fresh", "~3kg"),
    ("FRUIT-PAPAYA-1PC", "Papaya 1 Piece", 11, 5.99, "piece", 55, "Tropical", "~1kg"),
    ("FRUIT-POMEGRANATE-1KG", "Pomegranate 1kg", 11, 18.99, "1kg", 35, "Ruby Fresh", "1kg"),
    # Fresh Vegetables (12) - 10 products
    ("VEG-TOMATO-1KG", "Fresh Tomatoes 1kg", 12, 4.99, "1kg", 100, "Farm Fresh", "1kg"),
    ("VEG-ONION-1KG", "Onions 1kg", 12, 3.49, "1kg", 150, "Fresh Farms", "1kg"),
    ("VEG-POTATO-1KG", "Potatoes 1kg", 12, 3.99, "1kg", 120, "Farm Fresh", "1kg"),
    ("VEG-CARROT-1KG", "Carrots 1kg", 12, 5.49, "1kg", 80, "Organic Valley", "1kg"),
    ("VEG-CAPSICUM-MIX-500G", "Mixed Bell Peppers 500g", 12, 6.99, "500g", 60, "Fresh Farms", "500g"),
    ("VEG-BRINJAL-1KG", "Brinjal 1kg", 12, 5.99, "1kg", 50, "Farm Fresh", "1kg"),
    ("VEG-CAULIFLOWER-1PC", "Cauliflower 1 Piece", 12, 4.49, "piece", 45, "Fresh Farms", "~500g"),
    ("VEG-CUCUMBER-1KG", "Cucumber 1kg", 12, 3.99, "1kg", 90, "Farm Fresh", "1kg"),
    ("VEG-LADYFINGER-500G", "Lady Finger 500g", 12, 4.99, "500g", 65, "Farm Fresh", "500g"),
    ("VEG-GREEN-BEANS-500G", "Green Beans 500g", 12, 5.49, "500g", 55, "Fresh Farms", "500g"),
    # Exotic Fruits (13) - 5 products
    ("EXOTIC-AVOCADO-1PC", "Hass Avocado 1 Piece", 13, 4.99, "piece", 50, "Tropical", "~200g"),
    ("EXOTIC-KIWI-1KG", "Kiwi 1kg", 13, 12.99, "1kg", 40, "Zespri", "1kg"),
    ("EXOTIC-DRAGON-FRUIT-1PC", "Dragon Fruit 1 Piece", 13, 8.99, "piece", 30, "Exotic", "~400g"),
    ("EXOTIC-STARFRUIT-1KG", "Star Fruit 1kg", 13, 14.99, "1kg", 25, "Tropical", "1kg"),
    ("EXOTIC-LYCHEE-500G", "Lychee 500g", 13, 11.99, "500g", 35, "Asian Fresh", "500g"),
    # Leafy Greens (14) - 6 products
    ("LEAFY-SPINACH-250G", "Fresh Spinach 250g", 14, 3.99, "250g", 70, "Farm Fresh", "250g"),
    ("LEAFY-LETTUCE-1PC", "Iceberg Lettuce 1 Head", 14, 2.99, "piece", 80, "Fresh Farms", "~300g"),
    ("LEAFY-CORIANDER-100G", "Coriander 100g", 14, 1.99, "100g", 100, "Farm Fresh", "100g"),
    ("LEAFY-MINT-100G", "Fresh Mint 100g", 14, 2.49, "100g", 60, "Farm Fresh", "100g"),
    ("LEAFY-FENUGREEK-200G", "Fenugreek Leaves 200g", 14, 3.49, "200g", 45, "Farm Fresh", "200g"),
    ("LEAFY-KALE-200G", "Kale 200g", 14, 4.99, "200g", 40, "Organic Valley", "200g"),
    # Organic Produce (15) - 4 products
    ("ORG-TOMATO-1KG", "Organic Tomatoes 1kg", 15, 7.99, "1kg", 50, "Organic Valley", "1kg"),
    ("ORG-APPLE-1KG", "Organic Apples 1kg", 15, 15.99, "1kg", 40, "Organic Valley", "1kg"),
    ("ORG-CARROT-1KG", "Organic Carrots 1kg", 15, 7.49, "1kg", 45, "Organic Valley", "1kg"),
    ("ORG-SPINACH-250G", "Organic Spinach 250g", 15, 5.99, "250g", 35, "Organic Valley", "250g"),
    # Milk (16) - 6 products
    ("DAIRY-AMUL-TAAZA-1L", "Amul Taaza Milk 1L", 16, 2.49, "1L", 200, "Amul", "1L"),
    ("DAIRY-AMUL-GOLD-1L", "Amul Gold Full Cream Milk 1L", 16, 2.99, "1L", 150, "Amul", "1L"),
    ("DAIRY-MOTHER-DAIRY-1L", "Mother Dairy Toned Milk 1L", 16, 2.39, "1L", 180, "Mother Dairy", "1L"),
    ("DAIRY-BUFFALO-MILK-1L", "Buffalo Milk 1L", 16, 3.49, "1L", 80, "Local Dairy", "1L"),
    ("DAIRY-SOY-MILK-1L", "Soy Milk 1L", 16, 4.99, "1L", 60, "So Good", "1L"),
    ("DAIRY-ALMOND-MILK-1L", "Almond Milk 1L", 16, 5.99, "1L", 45, "Almond Breeze", "1L"),
    # Bread & Pav (17) - 5 products
    ("BAKERY-WHITE-BREAD-400G", "White Sandwich Bread 400g", 17, 1.49, "400g", 100, "Modern", "400g"),
    ("BAKERY-BROWN-BREAD-400G", "Brown Bread 400g", 17, 1.79, "400g", 80, "Modern", "400g"),
    ("BAKERY-PAV-6PC", "Pav 6 Pieces", 17, 0.99, "6 pcs", 150, "Local Bakery", "6 pcs"),
    ("BAKERY-BUNS-4PC", "Burger Buns 4 Pack", 17, 1.99, "4 pcs", 90, "Modern", "4 pcs"),
    ("BAKERY-MULTIGRAIN-400G", "Multigrain Bread 400g", 17, 2.49, "400g", 60, "Modern", "400g"),
    # Eggs (18) - 3 products
    ("DAIRY-EGGS-WHITE-12", "Farm Fresh White Eggs 12", 18, 2.99, "12 pcs", 120, "Farm Fresh", "12 pcs"),
    ("DAIRY-EGGS-BROWN-12", "Farm Fresh Brown Eggs 12", 18, 3.49, "12 pcs", 80, "Farm Fresh", "12 pcs"),
    ("DAIRY-EGGS-OMEGA-12", "Omega-3 Enriched Eggs 12", 18, 4.99, "12 pcs", 50, "Nutri Eggs", "12 pcs"),
    # Butter & Ghee (19) - 4 products
    ("DAIRY-AMUL-BUTTER-100G", "Amul Butter 100g", 19, 1.99, "100g", 150, "Amul", "100g"),
    ("DAIRY-AMUL-BUTTER-500G", "Amul Butter 500g", 19, 8.99, "500g", 60, "Amul", "500g"),
    ("DAIRY-GHEE-1L", "Pure Ghee 1L", 19, 18.99, "1L", 40, "Amul", "1L"),
    ("DAIRY-GHEE-500G", "Pure Ghee 500g", 19, 9.99, "500g", 70, "Amul", "500g"),
    # Cheese (20) - 4 products
    ("DAIRY-AMUL-CHEESE-200G", "Amul Processed Cheese 200g", 20, 3.99, "200g", 90, "Amul", "200g"),
    ("DAIRY-MOZZARELLA-200G", "Mozzarella Cheese 200g", 20, 5.99, "200g", 60, "Dairy Craft", "200g"),
    ("DAIRY-CHEDDAR-200G", "Cheddar Cheese 200g", 20, 5.49, "200g", 55, "Dairy Craft", "200g"),
    ("DAIRY-PIZZA-CHEESE-200G", "Pizza Cheese 200g", 20, 4.99, "200g", 75, "Amul", "200g"),
    # Paneer & Tofu (21) - 3 products
    ("DAIRY-PANEER-200G", "Fresh Paneer 200g", 21, 3.49, "200g", 80, "Amul", "200g"),
    ("DAIRY-PANEER-500G", "Fresh Paneer 500g", 21, 7.99, "500g", 50, "Amul", "500g"),
    ("DAIRY-TOFU-300G", "Firm Tofu 300g", 21, 2.99, "300g", 45, "Tofu House", "300g"),
    # Yogurt & Curd (22) - 4 products
    ("DAIRY-CURD-500G", "Fresh Curd 500g", 22, 1.99, "500g", 100, "Mother Dairy", "500g"),
    ("DAIRY-GREEK-YOGURT-200G", "Greek Yogurt 200g", 22, 3.99, "200g", 70, "Epigamia", "200g"),
    ("DAIRY-MANGO-YOGURT-90G", "Mango Yogurt 90g", 22, 0.99, "90g", 150, "Mother Dairy", "90g"),
    ("DAIRY-LASSI-200ML", "Sweet Lassi 200ml", 22, 1.49, "200ml", 80, "Mother Dairy", "200ml"),
    # Breakfast Cereals (23) - 5 products
    ("CEREAL-CORNFLAKES-500G", "Corn Flakes 500g", 23, 3.99, "500g", 90, "Kellogg's", "500g"),
    ("CEREAL-MUESLI-500G", "Muesli 500g", 23, 5.99, "500g", 60, "Kellogg's", "500g"),
    ("CEREAL-OATS-1KG", "Rolled Oats 1kg", 23, 4.99, "1kg", 70, "Quaker", "1kg"),
    ("CEREAL-CHOCOS-500G", "Chocos 500g", 23, 4.49, "500g", 55, "Kellogg's", "500g"),
    ("CEREAL-MILK-BIKIS-300G", "Milk Bikis 300g", 23, 2.99, "300g", 80, "Britannia", "300g"),
    # Soft Drinks (24) - 6 products
    ("BEV-COCA-COLA-2L", "Coca Cola 2L", 24, 2.49, "2L", 100, "Coca Cola", "2L"),
    ("BEV-PEPSI-2L", "Pepsi 2L", 24, 2.49, "2L", 95, "Pepsi", "2L"),
    ("BEV-SPRITE-2L", "Sprite 2L", 24, 2.49, "2L", 85, "Coca Cola", "2L"),
    ("BEV-THUMBS-UP-2L", "Thumbs Up 2L", 24, 2.49, "2L", 90, "Coca Cola", "2L"),
    ("BEV-LIMCA-600ML", "Limca 600ml", 24, 1.49, "600ml", 120, "Coca Cola", "600ml"),
    ("BEV-FANTA-2L", "Fanta Orange 2L", 24, 2.49, "2L", 75, "Coca Cola", "2L"),
    # Juices (25) - 5 products
    ("BEV-REAL-ORANGE-1L", "Real Orange Juice 1L", 25, 3.99, "1L", 70, "Dabur", "1L"),
    ("BEV-REAL-MANGO-1L", "Real Mango Juice 1L", 25, 3.99, "1L", 80, "Dabur", "1L"),
    ("BEV-TROPICANA-1L", "Tropicana Orange 1L", 25, 5.99, "1L", 50, "Tropicana", "1L"),
    ("BEV-FRESH-ORANGE-1L", "Fresh Orange Juice 1L", 25, 4.49, "1L", 45, "Local", "1L"),
    ("BEV-APPLE-JUICE-1L", "Apple Juice 1L", 25, 4.99, "1L", 55, "Dabur", "1L"),
    # Tea & Coffee (26) - 6 products
    ("BEV-TATA-TEA-500G", "Tata Tea 500g", 26, 4.99, "500g", 100, "Tata", "500g"),
    ("BEV-BROOKE-BOND-1KG", "Brooke Bond Red Label 1kg", 26, 8.99, "1kg", 60, "Brooke Bond", "1kg"),
    ("BEV-NESCAFE-200G", "Nescafe Classic 200g", 26, 9.99, "200g", 70, "Nescafe", "200g"),
    ("BEV-BRU-200G", "Bru Instant Coffee 200g", 26, 6.99, "200g", 65, "Bru", "200g"),
    ("BEV-GREEN-TEA-25BAG", "Green Tea 25 Bags", 26, 3.99, "25 bags", 80, "Tetley", "25 bags"),
    ("BEV-CHAI-MASALA-100G", "Chai Masala 100g", 26, 2.99, "100g", 90, "Everest", "100g"),
    # Energy Drinks (27) - 3 products
    ("BEV-RED-BULL-250ML", "Red Bull 250ml", 27, 2.99, "250ml", 60, "Red Bull", "250ml"),
    ("BEV-MONSTER-500ML", "Monster Energy 500ml", 27, 3.49, "500ml", 50, "Monster", "500ml"),
    ("BEV-STING-500ML", "Sting Energy Drink 500ml", 27, 1.49, "500ml", 100, "Pepsi", "500ml"),
    # Water (28) - 4 products
    ("BEV-BISLERI-1L", "Bisleri Water 1L", 28, 0.49, "1L", 300, "Bisleri", "1L"),
    ("BEV-BISLERI-2L", "Bisleri Water 2L", 28, 0.79, "2L", 250, "Bisleri", "2L"),
    ("BEV-KINLEY-1L", "Kinley Water 1L", 28, 0.49, "1L", 280, "Coca Cola", "1L"),
    ("BEV-AQUAFINA-1L", "Aquafina Water 1L", 28, 0.59, "1L", 200, "Pepsi", "1L"),
    # Health Drinks (29) - 4 products
    ("BEV-HORLICKS-500G", "Horlicks 500g", 29, 6.99, "500g", 60, "Horlicks", "500g"),
    ("BEV-BOURNVITA-500G", "Bournvita 500g", 29, 5.99, "500g", 70, "Cadbury", "500g"),
    ("BEV-COMFEED-500G", "Complan 500g", 29, 7.99, "500g", 45, "Heinz", "500g"),
    ("BEV-PROTEIN-SHAKE-500G", "Protein Powder 500g", 29, 24.99, "500g", 30, "MuscleBlaze", "500g"),
    # Chips & Crisps (30) - 6 products
    ("SNACK-LAYS-CLASSIC-50G", "Lay's Classic 50g", 30, 1.49, "50g", 150, "Lay's", "50g"),
    ("SNACK-LAYS-INDIA-50G", "Lay's India's Magic Masala 50g", 30, 1.49, "50g", 140, "Lay's", "50g"),
    ("SNACK-KURKURE-70G", "Kurkure Masala Munch 70g", 30, 1.29, "70g", 120, "PepsiCo", "70g"),
    ("SNACK-PRINGLES-107G", "Pringles Original 107g", 30, 2.99, "107g", 80, "Pringles", "107g"),
    ("SNACK-BIKANO-NAMKEEN-200G", "Bikano Aloo Bhujia 200g", 30, 1.99, "200g", 100, "Bikano", "200g"),
    ("SNACK-HALDIRAM-BHUJIA-400G", "Haldiram's Bhujia 400g", 30, 3.49, "400g", 70, "Haldiram's", "400g"),
    # Biscuits & Cookies (31) - 7 products
    ("SNACK-PARLE-G-200G", "Parle-G Gold 200g", 31, 0.99, "200g", 200, "Parle", "200g"),
    ("SNACK-PARLE-G-1KG", "Parle-G 1kg", 31, 3.99, "1kg", 80, "Parle", "1kg"),
    ("SNACK-MARIE-GOLD-300G", "Marie Gold 300g", 31, 1.49, "300g", 120, "Britannia", "300g"),
    ("SNACK-GOOD-DAY-200G", "Good Day Butter 200g", 31, 1.99, "200g", 100, "Britannia", "200g"),
    ("SNACK-OREO-133G", "Oreo 133g", 31, 2.49, "133g", 90, "Mondelez", "133g"),
    ("SNACK-NUTRI-CHOICE-300G", "Nutri Choice 300g", 31, 2.99, "300g", 75, "Britannia", "300g"),
    ("SNACK-DARK-FANTASY-150G", "Dark Fantasy 150g", 31, 3.99, "150g", 60, "ITC", "150g"),
    # Noodles & Pasta (32) - 6 products
    ("SNACK-MAGGI-2M-70G", "Maggi 2-Minute Noodles 70g", 32, 0.99, "70g", 200, "Nestle", "70g"),
    ("SNACK-MAGGI-5PACK-350G", "Maggi 5 Pack 350g", 32, 3.99, "350g", 100, "Nestle", "350g"),
    ("SNACK-TOP-RAMEN-70G", "Top Ramen Curry 70g", 32, 0.79, "70g", 150, "Nissin", "70g"),
    ("SNACK-YIPPEE-70G", "Yippee Noodles 70g", 32, 0.99, "70g", 120, "ITC", "70g"),
    ("SNACK-PASTA-PENNE-500G", "Penne Pasta 500g", 32, 2.99, "500g", 80, "Barilla", "500g"),
    ("SNACK-MACARONI-500G", "Macaroni 500g", 32, 2.49, "500g", 70, "Saffola", "500g"),
    # Ready to Eat (33) - 5 products
    ("SNACK-IDLY-MIX-500G", "Idli Mix 500g", 33, 3.99, "500g", 60, "MTR", "500g"),
    ("SNACK-DOSA-MIX-500G", "Dosa Mix 500g", 33, 3.99, "500g", 65, "MTR", "500g"),
    ("SNACK-UPMA-500G", "Upma Mix 500g", 33, 3.49, "500g", 55, "MTR", "500g"),
    ("SNACK-PULAV-500G", "Pulao Mix 500g", 33, 4.99, "500g", 50, "MTR", "500g"),
    ("SNACK-DAL-KHICHDI-500G", "Dal Khichdi 500g", 33, 4.49, "500g", 45, "MTR", "500g"),
    # Chocolates & Candies (34) - 7 products
    ("SNACK-DAIRY-MILK-60G", "Cadbury Dairy Milk 60g", 34, 1.49, "60g", 150, "Cadbury", "60g"),
    ("SNACK-DAIRY-MILK-150G", "Cadbury Dairy Milk 150g", 34, 3.49, "150g", 80, "Cadbury", "150g"),
    ("SNACK-5-STAR-50G", "5 Star 50g", 34, 1.29, "50g", 120, "Cadbury", "50g"),
    ("SNACK-PERK-45G", "Perk 45g", 34, 1.19, "45g", 100, "Cadbury", "45g"),
    ("SNACK-KITKAT-4F-42G", "KitKat 4 Finger 42g", 34, 1.49, "42g", 130, "Nestle", "42g"),
    ("SNACK-MUNCH-45G", "Munch 45g", 34, 1.19, "45g", 110, "Nestle", "45g"),
    ("SNACK-ECLAIRS-180G", "Eclairs 180g", 34, 0.99, "180g", 140, "Parle", "180g"),
    # Namkeen & Mixtures (35) - 4 products
    ("SNACK-HALDIRAM-ALOO-200G", "Haldiram's Aloo Bhujia 200g", 35, 1.99, "200g", 90, "Haldiram's", "200g"),
    ("SNACK-HALDIRAM-MIX-400G", "Haldiram's Mixed Namkeen 400g", 35, 4.99, "400g", 60, "Haldiram's", "400g"),
    ("SNACK-BIKANO-MIX-200G", "Bikano Mixture 200g", 35, 2.49, "200g", 75, "Bikano", "200g"),
    ("SNACK-KAJU-KATLI-200G", "Kaju Katli 200g", 35, 8.99, "200g", 40, "Haldiram's", "200g"),
    # Soaps & Body Wash (36) - 5 products
    ("CARE-DOVE-SOAP-100G", "Dove Cream Bar 100g", 36, 1.99, "100g", 100, "Dove", "100g"),
    ("CARE-LUX-SOAP-75G", "Lux Soap 75g", 36, 0.79, "75g", 150, "Lux", "75g"),
    ("CARE-LIFEBUOY-75G", "Lifebuoy Soap 75g", 36, 0.69, "75g", 120, "Lifebuoy", "75g"),
    ("CARE-DOVE-BODY-WASH-250ML", "Dove Body Wash 250ml", 36, 4.99, "250ml", 70, "Dove", "250ml"),
    ("CARE-PEARS-SOAP-125G", "Pears Soap 125g", 36, 1.49, "125g", 90, "Pears", "125g"),
    # Hair Care (37) - 4 products
    ("CARE-DOVE-SHAMPOO-180ML", "Dove Shampoo 180ml", 37, 3.99, "180ml", 80, "Dove", "180ml"),
    ("CARE-PANTENE-180ML", "Pantene Shampoo 180ml", 37, 4.49, "180ml", 65, "Pantene", "180ml"),
    ("CARE-PARACHUTE-200ML", "Parachute Coconut Oil 200ml", 37, 2.99, "200ml", 100, "Parachute", "200ml"),
    ("CARE-INDICA-100ML", "Indica Hair Oil 100ml", 37, 3.49, "100ml", 75, "Indica", "100ml"),
    # Oral Care (38) - 5 products
    ("CARE-COLGATE-150G", "Colgate Toothpaste 150g", 38, 1.99, "150g", 120, "Colgate", "150g"),
    ("CARE-PEPSODENT-150G", "Pepsodent 150g", 38, 1.49, "150g", 100, "Pepsodent", "150g"),
    ("CARE-SENSODYNE-100G", "Sensodyne 100g", 38, 4.99, "100g", 60, "Sensodyne", "100g"),
    ("CARE-COLGATE-BRUSH-1PC", "Colgate Toothbrush 1pc", 38, 0.99, "1 pc", 150, "Colgate", "1 pc"),
    ("CARE-MOUTHWASH-500ML", "Listerine Mouthwash 500ml", 38, 4.49, "500ml", 50, "Listerine", "500ml"),
    # Skin Care (39) - 4 products
    ("CARE-PONDS-CREAM-50G", "Ponds Cold Cream 50g", 39, 1.99, "50g", 90, "Ponds", "50g"),
    ("CARE-VASELINE-100ML", "Vaseline 100ml", 39, 2.99, "100ml", 80, "Vaseline", "100ml"),
    ("CARE-NIVEA-CREAM-50G", "Nivea Creme 50g", 39, 2.49, "50g", 70, "Nivea", "50g"),
    ("CARE-LOTUS-SCREEN-50ML", "Lotus Sunscreen 50ml", 39, 5.99, "50ml", 55, "Lotus", "50ml"),
    # Men's Grooming (40) - 3 products
    ("CARE-GILLETTE-FUSION-1PC", "Gillette Fusion 1 Blade", 40, 2.99, "1 pc", 80, "Gillette", "1 pc"),
    ("CARE-GILLETTE-FOAM-200G", "Gillette Shaving Foam 200g", 40, 3.49, "200g", 60, "Gillette", "200g"),
    ("CARE-PARK-AVENUE-100ML", "Park Avenue Deo 100ml", 40, 2.99, "100ml", 70, "Park Avenue", "100ml"),
    # Feminine Hygiene (41) - 3 products
    ("CARE-WHISPER-XXL-8", "Whisper Ultra 8 Pads", 41, 3.99, "8 pcs", 60, "Whisper", "8 pcs"),
    ("CARE-WHISPER-NIGHT-8", "Whisper Night 8 Pads", 41, 4.49, "8 pcs", 50, "Whisper", "8 pcs"),
    ("CARE-SOFY-10", "Sofy 10 Pads", 41, 2.99, "10 pcs", 55, "Sofy", "10 pcs"),
    # Deodorants (42) - 3 products
    ("CARE-AXE-150ML", "Axe Deodorant 150ml", 42, 3.99, "150ml", 70, "Axe", "150ml"),
    ("CARE-NIVEA-DEO-150ML", "Nivea Deo 150ml", 42, 3.49, "150ml", 65, "Nivea", "150ml"),
    ("CARE-FOGG-120ML", "Fogg Deo 120ml", 42, 2.99, "120ml", 80, "Fogg", "120ml"),
    # Detergents & Fabric Care (43) - 5 products
    ("HOME-SURF-1KG", "Surf Excel 1kg", 43, 12.99, "1kg", 60, "Surf", "1kg"),
    ("HOME-SURF-500G", "Surf Excel 500g", 43, 6.99, "500g", 80, "Surf", "500g"),
    ("HOME-ARIEL-1KG", "Ariel 1kg", 43, 11.99, "1kg", 55, "Ariel", "1kg"),
    ("HOME-SUNLIGHT-1KG", "Sunlight 1kg", 43, 4.99, "1kg", 90, "Sunlight", "1kg"),
    ("HOME-VIVID-500G", "Vivid 500g", 43, 3.99, "500g", 70, "Vivid", "500g"),
    # Cleaning Supplies (44) - 4 products
    ("HOME-HARPIC-1L", "Harpic 1L", 44, 3.99, "1L", 80, "Harpic", "1L"),
    ("HOME-LIZOL-500ML", "Lizol 500ml", 44, 4.49, "500ml", 65, "Lizol", "500ml"),
    ("HOME-PHENYL-500ML", "Phenyl 500ml", 44, 2.99, "500ml", 70, "Local", "500ml"),
    ("HOME-FLOOR-CLEANER-1L", "Floor Cleaner 1L", 44, 3.49, "1L", 60, "Harpic", "1L"),
    # Dishwashing (45) - 3 products
    ("HOME-VIM-500G", "Vim Bar 500g", 45, 1.49, "500g", 100, "Vim", "500g"),
    ("HOME-VIM-LIQUID-500ML", "Vim Liquid 500ml", 45, 2.99, "500ml", 75, "Vim", "500ml"),
    ("HOME-PRIL-500ML", "Pril 500ml", 45, 3.49, "500ml", 55, "Pril", "500ml"),
    # Disposables & Garbage Bags (46) - 3 products
    ("HOME-GARBAGE-BAG-L-10", "Garbage Bags Large 10", 46, 2.99, "10 pcs", 80, "Local", "10 pcs"),
    ("HOME-ALUMINIUM-FOIL-15M", "Aluminium Foil 15m", 46, 3.99, "15m", 60, "Local", "15m"),
    ("HOME-CLING-WRAP-30M", "Cling Wrap 30m", 46, 2.49, "30m", 70, "Local", "30m"),
    # Air Fresheners (47) - 3 products
    ("HOME-ODONIL-300G", "Odonil 300g", 47, 2.99, "300g", 90, "Godrej", "300g"),
    ("HOME-AMBI-PUR-250ML", "Ambi Pur 250ml", 47, 4.99, "250ml", 50, "Ambi Pur", "250ml"),
    ("HOME-AGARBATTI-1PACK", "Agarbatti 1 Pack", 47, 0.99, "1 pack", 100, "Local", "1 pack"),
    # Batteries & Bulbs (48) - 4 products
    ("HOME-DURACELL-AA-4", "Duracell AA 4 Pack", 48, 4.99, "4 pcs", 70, "Duracell", "4 pcs"),
    ("HOME-EVERREADY-AA-4", "Eveready AA 4 Pack", 48, 2.99, "4 pcs", 90, "Eveready", "4 pcs"),
    ("HOME-LED-BULB-9W", "LED Bulb 9W 1pc", 48, 2.49, "1 pc", 80, "Philips", "1 pc"),
    ("HOME-TUBELIGHT-20W", "Tube Light 20W 1pc", 48, 3.99, "1 pc", 55, "Philips", "1 pc"),
    # Diapers & Wipes (49) - 4 products
    ("BABY-PAMPERS-M-44", "Pampers M 44 Pcs", 49, 14.99, "44 pcs", 50, "Pampers", "44 pcs"),
    ("BABY-HUGGIES-M-44", "Huggies M 44 Pcs", 49, 12.99, "44 pcs", 55, "Huggies", "44 pcs"),
    ("BABY-WIPES-80", "Baby Wipes 80 Pcs", 49, 3.99, "80 pcs", 70, "Johnson's", "80 pcs"),
    ("BABY-WET-WIPES-100", "Wet Wipes 100 Pcs", 49, 2.99, "100 pcs", 80, "Local", "100 pcs"),
    # Baby Food (50) - 4 products
    ("BABY-CERELAC-300G", "Cerelac Wheat 300g", 50, 5.99, "300g", 60, "Nestle", "300g"),
    ("BABY-NAN-400G", "NAN Pro 400g", 50, 18.99, "400g", 40, "Nestle", "400g"),
    ("BABY-LACTOGEN-400G", "Lactogen 400g", 50, 12.99, "400g", 45, "Nestle", "400g"),
    ("BABY-FAREX-300G", "Farex 300g", 50, 4.99, "300g", 55, "Heinz", "300g"),
    # Baby Bath & Skin Care (51) - 4 products
    ("BABY-JOHNSONS-SOAP-100G", "Johnson's Baby Soap 100g", 51, 1.99, "100g", 80, "Johnson's", "100g"),
    ("BABY-JOHNSONS-LOTION-200ML", "Johnson's Baby Lotion 200ml", 51, 4.99, "200ml", 60, "Johnson's", "200ml"),
    ("BABY-JOHNSONS-POWDER-200G", "Johnson's Baby Powder 200g", 51, 2.99, "200g", 70, "Johnson's", "200g"),
    ("BABY-SEBA-MED-400ML", "Sebamed Baby 400ml", 51, 6.99, "400ml", 40, "Sebamed", "400ml"),
    # Baby Accessories (52) - 3 products
    ("BABY-BOTTLE-250ML", "Baby Bottle 250ml", 52, 4.99, "1 pc", 50, "Pigeon", "1 pc"),
    ("BABY-FEEDER-1PC", "Feeder 1pc", 52, 2.99, "1 pc", 60, "Pigeon", "1 pc"),
    ("BABY-TEETHER-1PC", "Teether 1pc", 52, 1.99, "1 pc", 80, "Pigeon", "1 pc"),
    # Chicken & Poultry (53) - 4 products
    ("MEAT-CHICKEN-BREAST-1KG", "Chicken Breast 1kg", 53, 8.99, "1kg", 40, "Fresh Poultry", "1kg"),
    ("MEAT-CHICKEN-THIGH-1KG", "Chicken Thigh 1kg", 53, 6.99, "1kg", 50, "Fresh Poultry", "1kg"),
    ("MEAT-CHICKEN-WHOLE-1KG", "Whole Chicken 1kg", 53, 5.99, "1kg", 35, "Fresh Poultry", "1kg"),
    ("MEAT-CHICKEN-CURRY-500G", "Chicken Curry Cut 500g", 53, 4.99, "500g", 60, "Fresh Poultry", "500g"),
    # Mutton & Lamb (54) - 3 products
    ("MEAT-MUTTON-1KG", "Mutton 1kg", 54, 18.99, "1kg", 25, "Fresh Meat", "1kg"),
    ("MEAT-MUTTON-CURRY-500G", "Mutton Curry Cut 500g", 54, 9.99, "500g", 35, "Fresh Meat", "500g"),
    ("MEAT-LAMB-CHOPS-500G", "Lamb Chops 500g", 54, 14.99, "500g", 20, "Fresh Meat", "500g"),
    # Fish & Seafood (55) - 4 products
    ("MEAT-FISH-ROHU-1KG", "Rohu Fish 1kg", 55, 7.99, "1kg", 30, "Fresh Catch", "1kg"),
    ("MEAT-PRAWN-500G", "Prawns 500g", 55, 12.99, "500g", 25, "Fresh Catch", "500g"),
    ("MEAT-SALMON-500G", "Salmon Fillet 500g", 55, 19.99, "500g", 20, "Fresh Catch", "500g"),
    ("MEAT-TILAPIA-500G", "Tilapia 500g", 55, 5.99, "500g", 40, "Fresh Catch", "500g"),
    # Sausages & Cold Cuts (56) - 3 products
    ("MEAT-SALAMI-200G", "Chicken Salami 200g", 56, 4.99, "200g", 50, "Venky's", "200g"),
    ("MEAT-SAUSAGES-6PC", "Chicken Sausages 6pc", 56, 3.99, "6 pcs", 60, "Venky's", "6 pcs"),
    ("MEAT-BACON-200G", "Bacon 200g", 56, 6.99, "200g", 35, "Fresh Meat", "200g"),
    # Cakes & Pastries (57) - 4 products
    ("BAKE-CAKE-PLAIN-500G", "Plain Cake 500g", 57, 4.99, "500g", 40, "Local Bakery", "500g"),
    ("BAKE-CAKE-CHOC-500G", "Chocolate Cake 500g", 57, 5.99, "500g", 35, "Local Bakery", "500g"),
    ("BAKE-PASTRY-1PC", "Pastry 1pc", 57, 2.99, "1 pc", 60, "Local Bakery", "1 pc"),
    ("BAKE-CUPCAKE-4PC", "Cupcakes 4pc", 57, 3.99, "4 pcs", 50, "Local Bakery", "4 pcs"),
    # Cookies & Biscuits (58) - 3 products
    ("BAKE-COOKIE-CHOC-200G", "Chocolate Cookies 200g", 58, 2.99, "200g", 80, "Britannia", "200g"),
    ("BAKE-CREAM-BISCUIT-400G", "Cream Biscuit 400g", 58, 3.49, "400g", 70, "Britannia", "400g"),
    ("BAKE-NUT-BISCUIT-300G", "Nut Biscuit 300g", 58, 4.99, "300g", 55, "Britannia", "300g"),
    # Bread & Rolls (59) - 4 products
    ("BAKE-BREAD-WHITE-400G", "White Bread 400g", 59, 1.49, "400g", 90, "Modern", "400g"),
    ("BAKE-BREAD-BROWN-400G", "Brown Bread 400g", 59, 1.79, "400g", 75, "Modern", "400g"),
    ("BAKE-DINNER-ROLL-6PC", "Dinner Rolls 6pc", 59, 2.49, "6 pcs", 65, "Local Bakery", "6 pcs"),
    ("BAKE-CROISSANT-4PC", "Croissants 4pc", 59, 3.99, "4 pcs", 50, "Local Bakery", "4 pcs"),
    # Pizza & Burger Buns (60) - 3 products
    ("BAKE-PIZZA-BASE-2PC", "Pizza Base 2pc", 60, 1.99, "2 pcs", 80, "Local", "2 pcs"),
    ("BAKE-BURGER-BUN-4PC", "Burger Buns 4pc", 60, 1.99, "4 pcs", 90, "Modern", "4 pcs"),
    ("BAKE-HOTDOG-BUN-4PC", "Hot Dog Buns 4pc", 60, 1.79, "4 pcs", 70, "Modern", "4 pcs"),
    # Ice Cream & Desserts (61) - 4 products
    ("FROZEN-AMUL-IC-1L", "Amul Ice Cream 1L", 61, 4.99, "1L", 60, "Amul", "1L"),
    ("FROZEN-HAVMOR-500ML", "Havmor Ice Cream 500ml", 61, 3.99, "500ml", 70, "Havmor", "500ml"),
    ("FROZEN-KULFI-500G", "Kulfi 500g", 61, 5.99, "500g", 45, "Amul", "500g"),
    ("FROZEN-GELATO-500ML", "Gelato 500ml", 61, 6.99, "500ml", 35, "Local", "500ml"),
    # Frozen Vegetables (62) - 4 products
    ("FROZEN-PEAS-500G", "Frozen Peas 500g", 62, 2.99, "500g", 80, "Birds Eye", "500g"),
    ("FROZEN-CORN-500G", "Frozen Corn 500g", 62, 2.49, "500g", 75, "Birds Eye", "500g"),
    ("FROZEN-MIX-VEG-500G", "Mixed Vegetables 500g", 62, 3.49, "500g", 65, "Birds Eye", "500g"),
    ("FROZEN-BROCCOLI-500G", "Frozen Broccoli 500g", 62, 3.99, "500g", 50, "Birds Eye", "500g"),
    # Frozen Snacks (63) - 4 products
    ("FROZEN-SAMOSA-12PC", "Frozen Samosa 12pc", 63, 4.99, "12 pcs", 60, "Haldiram's", "12 pcs"),
    ("FROZEN-NUGGETS-500G", "Chicken Nuggets 500g", 63, 5.99, "500g", 55, "McCain", "500g"),
    ("FROZEN-FRIES-500G", "French Fries 500g", 63, 2.99, "500g", 70, "McCain", "500g"),
    ("FROZEN-SPRING-ROLL-10", "Spring Rolls 10pc", 63, 4.49, "10 pcs", 45, "Local", "10 pcs"),
    # Frozen Non-Veg (64) - 3 products
    ("FROZEN-CHICKEN-1KG", "Frozen Chicken 1kg", 64, 6.49, "1kg", 40, "Venky's", "1kg"),
    ("FROZEN-FISH-FILLET-500G", "Frozen Fish Fillet 500g", 64, 7.99, "500g", 35, "Local", "500g"),
    ("FROZEN-PRAWN-500G", "Frozen Prawns 500g", 64, 11.99, "500g", 30, "Local", "500g"),
]


def build_items():
    items = []
    cat_product_index = {}  # track product index per category for cycling images
    for p in PRODUCTS:
        sku, name, cat_id, price, uom, stock, brand, pkg = p
        # Pick image from category pool (cycle through if multiple products in same category)
        img_list = CATEGORY_IMAGES.get(cat_id, ["https://images.unsplash.com/photo-1556742049-0cfed4f6a45d"])
        idx = cat_product_index.get(cat_id, 0) % len(img_list)
        cat_product_index[cat_id] = cat_product_index.get(cat_id, 0) + 1
        image_url = img_list[idx]
        images_json = json.dumps({"front": image_url, "back": None, "side": None})

        items.append({
            "sku": sku,
            "name": name,
            "categoryId": cat_id,
            "basePrice": float(price),
            "unitOfMeasure": uom,
            "currentStock": int(stock),
            "brand": brand,
            "packageSize": pkg,
            "images": images_json,
            "isActive": True
        })
    return items


def sync_batch(items):
    payload = json.dumps({"storeId": STORE_ID, "items": items}).encode("utf-8")
    req = urllib.request.Request(
        API_URL,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST"
    )
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.load(resp)


def main():
    print(f"🛒 Bulk syncing products to: {API_URL} (storeId={STORE_ID})")
    print("=" * 50)

    items = build_items()
    # Use smaller batches (50) to avoid connection pool exhaustion on server
    batch_size = 50
    total_success = 0
    total_fail = 0

    for i in range(0, len(items), batch_size):
        batch = items[i:i + batch_size]
        batch_num = (i // batch_size) + 1
        print(f"\nBatch {batch_num}: {len(batch)} items...")
        try:
            result = sync_batch(batch)
            success = result.get("successCount", 0)
            fail = result.get("failureCount", 0)
            total_success += success
            total_fail += fail
            print(f"  ✓ Success: {success}, Failed: {fail}, Time: {result.get('processingTimeMs', 0)}ms")
            if fail > 0:
                for r in result.get("results", []):
                    if r.get("status") == "FAILED":
                        print(f"    ✗ {r.get('sku')}: {r.get('errorMessage', 'Unknown')}")
        except urllib.error.HTTPError as e:
            body = e.read().decode() if e.fp else ""
            print(f"  ✗ HTTP {e.code}: {body[:200]}")
            sys.exit(1)
        except Exception as e:
            print(f"  ✗ Error: {e}")
            sys.exit(1)
        # Brief pause between batches to avoid connection pool exhaustion
        if i + batch_size < len(items):
            time.sleep(0.5)

    print("\n" + "=" * 50)
    print(f"✓ Done! Total: {total_success} synced, {total_fail} failed")
    print(f"\nVerify: curl -s \"{BASE_URL}/api/v1/catalog/products/all\" | head -500")


if __name__ == "__main__":
    main()
