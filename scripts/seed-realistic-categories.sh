#!/bin/bash

# Seed Realistic Categories Script
# Creates hierarchical category structure for Quick Commerce platform
# Uses PRODUCT_SERVICE_URL from .env (repo root)
# Usage: ./seed-realistic-categories.sh [base_url]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
if [ -f "$REPO_ROOT/.env" ]; then
  set -a
  source "$REPO_ROOT/.env"
  set +a
fi

BASE_URL="${1:-${PRODUCT_SERVICE_URL:-http://localhost:8081}}"
API_ENDPOINT="${BASE_URL}/api/v1/catalog/categories"

echo "🌱 Seeding realistic categories to: $API_ENDPOINT"
echo "================================================"

# Color codes for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Function to create category and extract ID
create_category() {
    local name="$1"
    local slug="$2"
    local description="$3"
    local parent_id="$4"
    local display_order="$5"
    local image_url="$6"
    
    local payload
    if [ -z "$parent_id" ] || [ "$parent_id" == "null" ]; then
        payload=$(cat <<EOF
{
  "name": "$name",
  "slug": "$slug",
  "description": "$description",
  "parentId": null,
  "displayOrder": $display_order,
  "isActive": true,
  "imageUrl": "$image_url"
}
EOF
)
    else
        payload=$(cat <<EOF
{
  "name": "$name",
  "slug": "$slug",
  "description": "$description",
  "parentId": $parent_id,
  "displayOrder": $display_order,
  "isActive": true,
  "imageUrl": "$image_url"
}
EOF
)
    fi
    
    response=$(curl -s -X POST "$API_ENDPOINT" \
        -H "Content-Type: application/json" \
        -d "$payload")
    
    # Extract ID from response
    category_id=$(echo "$response" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)
    
    if [ -n "$category_id" ]; then
        if [ -z "$parent_id" ] || [ "$parent_id" == "null" ]; then
            echo -e "${GREEN}✓${NC} Created parent category: ${BLUE}$name${NC} (ID: $category_id)" >&2
        else
            echo -e "${GREEN}✓${NC}   Created child category: $name (ID: $category_id, Parent: $parent_id)" >&2
        fi
        echo "$category_id"
    else
        echo -e "${RED}✗${NC} Failed to create category: $name" >&2
        echo "Response: $response" >&2
        echo "0"
    fi
}

echo ""
echo "Creating Parent Categories..."
echo "================================================"

# 1. Fruits & Vegetables
fruits_veg_id=$(create_category \
    "Fruits & Vegetables" \
    "fruits-vegetables" \
    "Fresh fruits and vegetables delivered daily" \
    "null" \
    1 \
    "https://images.unsplash.com/photo-1610348725531-843dff563e2c")

# 2. Dairy & Breakfast
dairy_id=$(create_category \
    "Dairy & Breakfast" \
    "dairy-breakfast" \
    "Milk, eggs, bread, and breakfast essentials" \
    "null" \
    2 \
    "https://images.unsplash.com/photo-1628088062854-d1870b4553da")

# 3. Beverages
beverages_id=$(create_category \
    "Beverages" \
    "beverages" \
    "Soft drinks, juices, tea, coffee, and health drinks" \
    "null" \
    3 \
    "https://images.unsplash.com/photo-1437418747212-8d9709afab22")

# 4. Snacks & Packaged Foods
snacks_id=$(create_category \
    "Snacks & Packaged Foods" \
    "snacks-packaged-foods" \
    "Chips, biscuits, noodles, and ready-to-eat meals" \
    "null" \
    4 \
    "https://images.unsplash.com/photo-1599490659213-e2b9527bd087")

# 5. Personal Care
personal_care_id=$(create_category \
    "Personal Care" \
    "personal-care" \
    "Health, hygiene, and beauty products" \
    "null" \
    5 \
    "https://images.unsplash.com/photo-1556228578-0d85b1a4d571")

# 6. Household Essentials
household_id=$(create_category \
    "Household Essentials" \
    "household-essentials" \
    "Cleaning supplies, detergents, and home care" \
    "null" \
    6 \
    "https://images.unsplash.com/photo-1584820927498-cfe5211fd8bf")

# 7. Baby Care
baby_care_id=$(create_category \
    "Baby Care" \
    "baby-care" \
    "Diapers, baby food, and infant care products" \
    "null" \
    7 \
    "https://images.unsplash.com/photo-1515488042361-ee00e0ddd4e4")

# 8. Meat, Fish & Eggs
meat_id=$(create_category \
    "Meat, Fish & Eggs" \
    "meat-fish-eggs" \
    "Fresh meat, seafood, and farm eggs" \
    "null" \
    8 \
    "https://images.unsplash.com/photo-1607623814075-e51df1bdc82f")

# 9. Bakery & Baked Goods
bakery_id=$(create_category \
    "Bakery & Baked Goods" \
    "bakery-baked-goods" \
    "Fresh breads, cakes, cookies, and pastries" \
    "null" \
    9 \
    "https://images.unsplash.com/photo-1509440159596-0249088772ff")

# 10. Frozen Foods
frozen_id=$(create_category \
    "Frozen Foods" \
    "frozen-foods" \
    "Ice cream, frozen vegetables, and ready meals" \
    "null" \
    10 \
    "https://images.unsplash.com/photo-1563245372-f21724e3856d")

echo ""
echo "Creating Child Categories..."
echo "================================================"

# Fruits & Vegetables - Children
if [ "$fruits_veg_id" != "0" ]; then
    create_category "Fresh Fruits" "fresh-fruits" "Seasonal and exotic fruits" "$fruits_veg_id" 1 "https://images.unsplash.com/photo-1619566636858-adf3ef46400b"
    create_category "Fresh Vegetables" "fresh-vegetables" "Farm-fresh vegetables" "$fruits_veg_id" 2 "https://images.unsplash.com/photo-1597362925123-77861d3fbac7"
    create_category "Exotic Fruits" "exotic-fruits" "Imported and exotic fruits" "$fruits_veg_id" 3 "https://images.unsplash.com/photo-1528825871115-3581a5387919"
    create_category "Leafy Greens" "leafy-greens" "Spinach, lettuce, and herbs" "$fruits_veg_id" 4 "https://images.unsplash.com/photo-1622206151226-18ca2c9ab4a1"
    create_category "Organic Produce" "organic-produce" "Certified organic fruits and vegetables" "$fruits_veg_id" 5 "https://images.unsplash.com/photo-1488459716781-31db52582fe9"
fi

# Dairy & Breakfast - Children
if [ "$dairy_id" != "0" ]; then
    create_category "Milk" "milk" "Fresh cow, buffalo, and plant-based milk" "$dairy_id" 1 "https://images.unsplash.com/photo-1563636619-e9143da7973b"
    create_category "Bread & Pav" "bread-pav" "Sandwich bread, pav, and buns" "$dairy_id" 2 "https://images.unsplash.com/photo-1509440159596-0249088772ff"
    create_category "Eggs" "eggs" "Farm-fresh white and brown eggs" "$dairy_id" 3 "https://images.unsplash.com/photo-1582722872445-44dc5f7e3c8f"
    create_category "Butter & Ghee" "butter-ghee" "Table butter and pure ghee" "$dairy_id" 4 "https://images.unsplash.com/photo-1589985270826-4b7bb135bc9d"
    create_category "Cheese" "cheese" "Processed, mozzarella, and cheddar cheese" "$dairy_id" 5 "https://images.unsplash.com/photo-1486297678162-eb2a19b0a32d"
    create_category "Paneer & Tofu" "paneer-tofu" "Fresh paneer and tofu" "$dairy_id" 6 "https://images.unsplash.com/photo-1631452180519-c014fe946bc7"
    create_category "Yogurt & Curd" "yogurt-curd" "Dahi, Greek yogurt, and flavored yogurt" "$dairy_id" 7 "https://images.unsplash.com/photo-1488477181946-6428a0291777"
    create_category "Breakfast Cereals" "breakfast-cereals" "Cornflakes, oats, and muesli" "$dairy_id" 8 "https://images.unsplash.com/photo-1621939514649-280e2ee25f60"
fi

# Beverages - Children
if [ "$beverages_id" != "0" ]; then
    create_category "Soft Drinks" "soft-drinks" "Carbonated drinks and sodas" "$beverages_id" 1 "https://images.unsplash.com/photo-1581006852262-e4307cf6283a"
    create_category "Juices" "juices" "Fresh and packaged fruit juices" "$beverages_id" 2 "https://images.unsplash.com/photo-1600271886742-f049cd451bba"
    create_category "Tea & Coffee" "tea-coffee" "Tea leaves, coffee powder, and instant mixes" "$beverages_id" 3 "https://images.unsplash.com/photo-1544787219-7f47ccb76574"
    create_category "Energy Drinks" "energy-drinks" "Energy and sports drinks" "$beverages_id" 4 "https://images.unsplash.com/photo-1622483767028-3f66f32aef97"
    create_category "Water" "water" "Mineral and packaged drinking water" "$beverages_id" 5 "https://images.unsplash.com/photo-1548839140-29a749e1cf4d"
    create_category "Health Drinks" "health-drinks" "Protein shakes and nutrition drinks" "$beverages_id" 6 "https://images.unsplash.com/photo-1517466787929-bc90951d0974"
fi

# Snacks & Packaged Foods - Children
if [ "$snacks_id" != "0" ]; then
    create_category "Chips & Crisps" "chips-crisps" "Potato chips and namkeen" "$snacks_id" 1 "https://images.unsplash.com/photo-1566478989037-eec170784d0b"
    create_category "Biscuits & Cookies" "biscuits-cookies" "Sweet and savory biscuits" "$snacks_id" 2 "https://images.unsplash.com/photo-1558961363-fa8fdf82db35"
    create_category "Noodles & Pasta" "noodles-pasta" "Instant noodles and pasta" "$snacks_id" 3 "https://images.unsplash.com/photo-1551892374-ecf8754cf8b0"
    create_category "Ready to Eat" "ready-to-eat" "Instant meals and ready-to-cook" "$snacks_id" 4 "https://images.unsplash.com/photo-1609501676725-7186f017a4b7"
    create_category "Chocolates & Candies" "chocolates-candies" "Chocolates, toffees, and sweets" "$snacks_id" 5 "https://images.unsplash.com/photo-1481391319762-47dff72954d9"
    create_category "Namkeen & Mixtures" "namkeen-mixtures" "Traditional Indian snacks" "$snacks_id" 6 "https://images.unsplash.com/photo-1601524909162-ae8725290836"
fi

# Personal Care - Children
if [ "$personal_care_id" != "0" ]; then
    create_category "Soaps & Body Wash" "soaps-body-wash" "Bathing soaps and shower gels" "$personal_care_id" 1 "https://images.unsplash.com/photo-1600857062241-98e5dba60f2f"
    create_category "Hair Care" "hair-care" "Shampoos, conditioners, and hair oils" "$personal_care_id" 2 "https://images.unsplash.com/photo-1535585209827-a15fcdbc4c2d"
    create_category "Oral Care" "oral-care" "Toothpaste, brushes, and mouthwash" "$personal_care_id" 3 "https://images.unsplash.com/photo-1607613009820-a29f7bb81c04"
    create_category "Skin Care" "skin-care" "Lotions, creams, and face care" "$personal_care_id" 4 "https://images.unsplash.com/photo-1556228578-8c89e6adf883"
    create_category "Men's Grooming" "mens-grooming" "Shaving and grooming essentials" "$personal_care_id" 5 "https://images.unsplash.com/photo-1621607512214-68297480165e"
    create_category "Feminine Hygiene" "feminine-hygiene" "Sanitary pads and hygiene products" "$personal_care_id" 6 "https://images.unsplash.com/photo-1583947581924-860bda6a26df"
    create_category "Deodorants" "deodorants" "Body sprays and deodorants" "$personal_care_id" 7 "https://images.unsplash.com/photo-1571875257727-256c39da42af"
fi

# Household Essentials - Children
if [ "$household_id" != "0" ]; then
    create_category "Detergents & Fabric Care" "detergents-fabric-care" "Washing powder and liquid detergents" "$household_id" 1 "https://images.unsplash.com/photo-1610557892470-55d9e80c0bce"
    create_category "Cleaning Supplies" "cleaning-supplies" "Floor cleaners and surface cleaners" "$household_id" 2 "https://images.unsplash.com/photo-1563453392212-326f5e854473"
    create_category "Dishwashing" "dishwashing" "Dishwash bars, liquids, and scrubbers" "$household_id" 3 "https://images.unsplash.com/photo-1600353068440-6361ef3a86e8"
    create_category "Disposables & Garbage Bags" "disposables-garbage-bags" "Garbage bags, foils, and wraps" "$household_id" 4 "https://images.unsplash.com/photo-1625667626161-d18df8b36968"
    create_category "Air Fresheners" "air-fresheners" "Room fresheners and incense" "$household_id" 5 "https://images.unsplash.com/photo-1585128723678-e40485025c46"
    create_category "Batteries & Bulbs" "batteries-bulbs" "Batteries, LED bulbs, and tube lights" "$household_id" 6 "https://images.unsplash.com/photo-1551030173-122aabc4489c"
fi

# Baby Care - Children
if [ "$baby_care_id" != "0" ]; then
    create_category "Diapers & Wipes" "diapers-wipes" "Baby diapers and wet wipes" "$baby_care_id" 1 "https://images.unsplash.com/photo-1617788138017-80ad40651399"
    create_category "Baby Food" "baby-food" "Infant formula and baby cereals" "$baby_care_id" 2 "https://images.unsplash.com/photo-1598639279843-644def8d82ad"
    create_category "Baby Bath & Skin Care" "baby-bath-skin-care" "Baby soaps, lotions, and powders" "$baby_care_id" 3 "https://images.unsplash.com/photo-1612349317150-e413f6a5b16d"
    create_category "Baby Accessories" "baby-accessories" "Bottles, feeders, and teethers" "$baby_care_id" 4 "https://images.unsplash.com/photo-1596461404969-9ae70f2830c1"
fi

# Meat, Fish & Eggs - Children
if [ "$meat_id" != "0" ]; then
    create_category "Chicken & Poultry" "chicken-poultry" "Fresh and frozen chicken" "$meat_id" 1 "https://images.unsplash.com/photo-1587593810167-a84920ea0781"
    create_category "Mutton & Lamb" "mutton-lamb" "Fresh mutton and lamb cuts" "$meat_id" 2 "https://images.unsplash.com/photo-1602470520998-f4a52199a3d6"
    create_category "Fish & Seafood" "fish-seafood" "Fresh fish, prawns, and crabs" "$meat_id" 3 "https://images.unsplash.com/photo-1535591273668-578e31182c4f"
    create_category "Sausages & Cold Cuts" "sausages-cold-cuts" "Processed meat and salami" "$meat_id" 4 "https://images.unsplash.com/photo-1539274989475-c75edd2f81c9"
fi

# Bakery & Baked Goods - Children
if [ "$bakery_id" != "0" ]; then
    create_category "Cakes & Pastries" "cakes-pastries" "Fresh cakes and pastries" "$bakery_id" 1 "https://images.unsplash.com/photo-1578985545062-69928b1d9587"
    create_category "Cookies & Biscuits" "cookies-biscuits" "Homemade and artisan cookies" "$bakery_id" 2 "https://images.unsplash.com/photo-1499636136210-6f4ee915583e"
    create_category "Bread & Rolls" "bread-rolls" "Artisan breads and dinner rolls" "$bakery_id" 3 "https://images.unsplash.com/photo-1549931319-a545dcf3bc73"
    create_category "Pizza & Burger Buns" "pizza-burger-buns" "Pizza bases and burger buns" "$bakery_id" 4 "https://images.unsplash.com/photo-1513104890138-7c749659a591"
fi

# Frozen Foods - Children
if [ "$frozen_id" != "0" ]; then
    create_category "Ice Cream & Desserts" "ice-cream-desserts" "Ice cream tubs and frozen desserts" "$frozen_id" 1 "https://images.unsplash.com/photo-1563805042-7684c019e1cb"
    create_category "Frozen Vegetables" "frozen-vegetables" "Frozen peas, corn, and mixed vegetables" "$frozen_id" 2 "https://images.unsplash.com/photo-1506617420156-8e4536971650"
    create_category "Frozen Snacks" "frozen-snacks" "Frozen samosas, nuggets, and fries" "$frozen_id" 3 "https://images.unsplash.com/photo-1630367462565-a355ddd6b3d8"
    create_category "Frozen Non-Veg" "frozen-non-veg" "Frozen chicken and fish products" "$frozen_id" 4 "https://images.unsplash.com/photo-1603894584373-5ac82b2ae398"
fi

echo ""
echo "================================================"
echo -e "${GREEN}✓ Category seeding completed!${NC}"
echo "================================================"
echo ""
echo "To view the hierarchical category tree, run:"
echo "curl ${BASE_URL}/api/v1/catalog/categories/tree | jq"
echo ""
