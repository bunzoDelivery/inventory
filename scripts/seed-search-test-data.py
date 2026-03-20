#!/usr/bin/env python3
"""
Seeds products with rich searchKeywords for comprehensive search testing.
Products are assigned to L2 (child) categories following Blinkit-style hierarchy.
Uses POST /api/v1/catalog/products/sync.
"""
import json, sys, time, urllib.request

BASE_URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8081"
STORE_ID = 1
API_URL = f"{BASE_URL}/api/v1/catalog/products/sync"

IMG = lambda url: json.dumps({"front": url, "back": None, "side": None})
PH = "https://images.unsplash.com/photo-1556742049-0cfed4f6a45d"

# L2 category IDs (child categories — products belong here, NOT to L1 parents)
# L1: Vegetables & Fruits (1) → L2: 101=Fresh Fruits, 102=Fresh Vegetables, 103=Leafies & Herbs
# L1: Dairy, Bread & Eggs (2) → L2: 201=Milk, 202=Bread & Pav, 203=Eggs, 204=Paneer & Tofu, 205=Butter & Ghee, 206=Cheese, 207=Curd & Yogurt
# L1: Munchies (3) → L2: 301=Chips & Crisps, 302=Namkeen & Mixtures
# L1: Sweet Tooth (4) → L2: 401=Chocolates, 402=Biscuits & Cookies
# L1: Cold Drinks & Juices (5) → L2: 501=Soft Drinks, 502=Fruit Juices, 503=Water
# L1: Tea, Coffee & Health Drinks (6) → L2: 601=Tea, 602=Coffee
# L1: Breakfast & Instant Food (7) → L2: 701=Noodles & Pasta
# L1: Atta, Rice & Dal (8) → L2: 801=Atta & Flours, 802=Rice, 803=Dals & Pulses
# L1: Masala, Oil & More (9) → L2: 901=Cooking Oil, 902=Spices, 903=Salt & Sugar
# L1: Personal Care (10) → L2: 1001=Soaps & Body Wash, 1002=Hair Care, 1003=Oral Care
# L1: Cleaning Essentials (11) → L2: 1101=Detergents, 1102=Dishwash, 1103=Cleaners
# L1: Baby Care (12) → L2: 1201=Diapers & Wipes, 1202=Baby Food, 1203=Baby Bath & Skin
# L1: Chicken, Meat & Fish (13) → L2: 1301=Chicken, 1302=Mutton, 1303=Fish & Seafood
# L1: Frozen Food (15) → L2: 1501=Ice Cream, 1502=Frozen Snacks, 1503=Frozen Vegetables

# (sku, name, L2_categoryId, price, uom, stock, brand, pkgSize, searchKeywords, searchPriority, isBestseller, orderCount, description)
PRODUCTS = [
    # ===== Fresh Fruits (L2=101) =====
    ("FRT-APPLE-1KG", "Apples 1kg", 101, 12.99, "1kg", 80, "Fresh Farms", "1kg",
     "apple,apples,seb,fruit,fruits,phal,red apple,green apple", 80, True, 450,
     "Fresh crispy apples, perfect for snacking"),
    ("FRT-BANANA-DOZ", "Bananas 1 Dozen", 101, 4.99, "dozen", 120, "Tropical", "12 pcs",
     "banana,bananas,kela,fruit,fruits,phal,yellow banana", 90, True, 620,
     "Fresh ripe bananas, great source of energy"),
    ("FRT-ORANGE-1KG", "Oranges 1kg", 101, 8.99, "1kg", 60, "Citrus Fresh", "1kg",
     "orange,oranges,santra,narangi,fruit,fruits,phal,citrus", 75, True, 380,
     "Juicy navel oranges, rich in Vitamin C"),
    ("FRT-MANGO-1KG", "Alphonso Mango 1kg", 101, 15.99, "1kg", 45, "Indian Harvest", "1kg",
     "mango,mangoes,aam,hapus,alphonso,fruit,fruits,phal", 85, True, 520,
     "Premium Alphonso mangoes, sweet and aromatic"),
    ("FRT-GRAPES-500G", "Green Grapes 500g", 101, 9.99, "500g", 70, "Vine Fresh", "500g",
     "grapes,grape,angoor,angur,fruit,fruits,phal,green grapes", 60, False, 280,
     "Seedless green grapes, sweet and crunchy"),
    ("FRT-WATERMELON-1PC", "Watermelon 1 Piece", 101, 6.99, "piece", 40, "Summer Fresh", "~3kg",
     "watermelon,tarbuz,tarbooj,fruit,fruits,phal", 55, False, 190,
     "Fresh whole watermelon, sweet and refreshing"),
    ("FRT-PAPAYA-1PC", "Papaya 1 Piece", 101, 5.99, "piece", 55, "Tropical", "~1kg",
     "papaya,papita,papeeta,fruit,fruits,phal", 50, False, 160,
     "Ripe papaya, rich in digestive enzymes"),
    ("FRT-POMEGRANATE-1KG", "Pomegranate 1kg", 101, 18.99, "1kg", 35, "Ruby Fresh", "1kg",
     "pomegranate,anaar,anar,fruit,fruits,phal", 65, False, 220,
     "Fresh pomegranates with ruby-red seeds"),
    ("FRT-GUAVA-1KG", "Guava 1kg", 101, 6.49, "1kg", 50, "Fresh Farms", "1kg",
     "guava,amrood,amrud,peru,fruit,fruits,phal", 55, False, 200,
     "Fresh guavas, rich in Vitamin C"),
    ("FRT-PINEAPPLE-1PC", "Pineapple 1 Piece", 101, 7.99, "piece", 30, "Tropical", "~1.5kg",
     "pineapple,ananas,fruit,fruits,phal", 50, False, 150,
     "Sweet and tangy fresh pineapple"),

    # ===== Fresh Vegetables (L2=102) =====
    ("VEG-TOMATO-1KG", "Tomatoes 1kg", 102, 4.99, "1kg", 100, "Farm Fresh", "1kg",
     "tomato,tomatoes,tamatar,vegetable,vegetables,sabzi,sabji", 90, True, 580,
     "Fresh red tomatoes for cooking"),
    ("VEG-ONION-1KG", "Onions 1kg", 102, 3.49, "1kg", 150, "Fresh Farms", "1kg",
     "onion,onions,pyaz,pyaaz,kanda,vegetable,vegetables,sabzi,sabji", 95, True, 650,
     "Essential cooking onions"),
    ("VEG-POTATO-1KG", "Potatoes 1kg", 102, 3.99, "1kg", 120, "Farm Fresh", "1kg",
     "potato,potatoes,aloo,alu,vegetable,vegetables,sabzi,sabji", 92, True, 600,
     "Fresh potatoes for all cooking needs"),
    ("VEG-CARROT-1KG", "Carrots 1kg", 102, 5.49, "1kg", 80, "Organic Valley", "1kg",
     "carrot,carrots,gajar,vegetable,vegetables,sabzi,sabji", 65, False, 250,
     "Fresh orange carrots, crunchy and sweet"),
    ("VEG-CAPSICUM-500G", "Bell Peppers 500g", 102, 6.99, "500g", 60, "Fresh Farms", "500g",
     "capsicum,shimla mirch,bell pepper,vegetable,vegetables,sabzi", 50, False, 180,
     "Mixed color bell peppers"),
    ("VEG-CAULIFLOWER-1PC", "Cauliflower 1 Piece", 102, 4.49, "piece", 45, "Fresh Farms", "~500g",
     "cauliflower,gobhi,gobi,phool gobi,vegetable,vegetables,sabzi", 60, False, 220,
     "Fresh white cauliflower"),
    ("VEG-CUCUMBER-1KG", "Cucumber 1kg", 102, 3.99, "1kg", 90, "Farm Fresh", "1kg",
     "cucumber,kheera,kakdi,vegetable,vegetables,sabzi", 50, False, 170,
     "Cool fresh cucumbers"),
    ("VEG-LADYFINGER-500G", "Lady Finger 500g", 102, 4.99, "500g", 65, "Farm Fresh", "500g",
     "ladyfinger,bhindi,okra,vegetable,vegetables,sabzi", 45, False, 140,
     "Fresh tender lady finger"),
    ("VEG-BRINJAL-1KG", "Brinjal 1kg", 102, 5.99, "1kg", 50, "Farm Fresh", "1kg",
     "brinjal,baingan,eggplant,aubergine,vegetable,vegetables,sabzi", 45, False, 130,
     "Fresh purple brinjal"),
    ("VEG-BEANS-500G", "Green Beans 500g", 102, 5.49, "500g", 55, "Fresh Farms", "500g",
     "green beans,beans,french beans,vegetable,vegetables,sabzi", 40, False, 120,
     "Tender fresh green beans"),

    # ===== Leafies & Herbs (L2=103) =====
    ("LEAF-SPINACH-250G", "Fresh Spinach 250g", 103, 3.99, "250g", 70, "Farm Fresh", "250g",
     "spinach,palak,saag,leafy,greens,vegetable,vegetables,sabzi", 55, False, 200,
     "Fresh baby spinach leaves"),
    ("LEAF-CORIANDER-100G", "Coriander 100g", 103, 1.99, "100g", 100, "Farm Fresh", "100g",
     "coriander,dhaniya,dhania,hara dhaniya,herbs,leafy", 40, False, 300,
     "Fresh green coriander leaves"),
    ("LEAF-MINT-100G", "Fresh Mint 100g", 103, 2.49, "100g", 60, "Farm Fresh", "100g",
     "mint,pudina,herbs,leafy", 35, False, 180,
     "Fresh mint leaves for chutney and tea"),
    ("LEAF-FENUGREEK-200G", "Fenugreek Leaves 200g", 103, 3.49, "200g", 45, "Farm Fresh", "200g",
     "fenugreek,methi,herbs,leafy,sabzi", 30, False, 120,
     "Fresh fenugreek leaves (methi)"),

    # ===== Milk (L2=201) =====
    ("DRY-AMUL-MILK-1L", "Amul Taaza Milk 1L", 201, 2.49, "1L", 200, "Amul", "1L",
     "milk,doodh,dudh,amul,taaza,taza,dairy", 95, True, 700,
     "Amul Taaza toned milk, pasteurized"),
    ("DRY-AMUL-GOLD-1L", "Amul Gold Full Cream Milk 1L", 201, 2.99, "1L", 150, "Amul", "1L",
     "milk,doodh,dudh,amul,gold,full cream,dairy", 88, True, 500,
     "Amul Gold full cream milk"),
    ("DRY-MOTHER-DAIRY-1L", "Mother Dairy Toned Milk 1L", 201, 2.39, "1L", 180, "Mother Dairy", "1L",
     "milk,doodh,dudh,mother dairy,toned milk,dairy", 82, True, 420,
     "Mother Dairy toned milk"),

    # ===== Bread & Pav (L2=202) =====
    ("BKR-BREAD-WHITE", "White Sandwich Bread 400g", 202, 1.49, "400g", 100, "Modern", "400g",
     "bread,roti,white bread,sandwich bread", 85, True, 480,
     "Soft white sandwich bread"),
    ("BKR-BREAD-BROWN", "Brown Bread 400g", 202, 1.79, "400g", 80, "Modern", "400g",
     "bread,brown bread,wheat bread,whole wheat", 70, False, 280,
     "Healthy whole wheat brown bread"),
    ("BKR-PAV-6PC", "Pav 6 Pieces", 202, 0.99, "6 pcs", 150, "Local Bakery", "6 pcs",
     "pav,ladi pav,bun,bread roll,pav bhaji", 75, True, 400,
     "Soft pav buns for vada pav and pav bhaji"),

    # ===== Eggs (L2=203) =====
    ("DRY-EGGS-12", "Farm Fresh White Eggs 12", 203, 2.99, "12 pcs", 120, "Farm Fresh", "12 pcs",
     "eggs,anda,ande,egg,white eggs,protein", 80, True, 400,
     "Farm fresh white eggs, 12 pack"),
    ("DRY-EGGS-BROWN-12", "Brown Eggs 12", 203, 3.49, "12 pcs", 80, "Farm Fresh", "12 pcs",
     "eggs,anda,brown eggs,free range,protein", 60, False, 250,
     "Farm fresh brown eggs"),

    # ===== Paneer & Tofu (L2=204) =====
    ("DRY-PANEER-200G", "Amul Fresh Paneer 200g", 204, 3.49, "200g", 80, "Amul", "200g",
     "paneer,cottage cheese,amul paneer,dairy", 72, True, 340,
     "Fresh soft paneer for cooking"),

    # ===== Butter & Ghee (L2=205) =====
    ("DRY-AMUL-BUTTER-100G", "Amul Butter 100g", 205, 1.99, "100g", 150, "Amul", "100g",
     "butter,makhan,makkhan,amul butter,dairy", 80, True, 380,
     "Amul pasteurized butter"),
    ("DRY-AMUL-BUTTER-500G", "Amul Butter 500g", 205, 8.99, "500g", 60, "Amul", "500g",
     "butter,makhan,makkhan,amul butter,dairy", 70, False, 200,
     "Amul butter family pack"),
    ("DRY-GHEE-1L", "Amul Pure Ghee 1L", 205, 18.99, "1L", 40, "Amul", "1L",
     "ghee,desi ghee,amul ghee,clarified butter,dairy", 75, True, 320,
     "Amul pure cow ghee"),

    # ===== Cheese (L2=206) =====
    ("DRY-CHEESE-200G", "Amul Processed Cheese 200g", 206, 3.99, "200g", 90, "Amul", "200g",
     "cheese,amul cheese,processed cheese,dairy", 60, False, 220,
     "Amul processed cheese slices"),

    # ===== Curd & Yogurt (L2=207) =====
    ("DRY-CURD-500G", "Mother Dairy Fresh Curd 500g", 207, 1.99, "500g", 100, "Mother Dairy", "500g",
     "curd,dahi,yogurt,mother dairy,dairy", 70, True, 350,
     "Fresh set curd"),

    # ===== Chips & Crisps (L2=301) =====
    ("SNK-LAYS-CLASSIC-50G", "Lay's Classic Salted 50g", 301, 1.49, "50g", 150, "Lay's", "50g",
     "chips,lays,lay's,potato chips,wafers,namkeen,snack,snacks", 82, True, 510,
     "Lay's classic salted potato chips"),
    ("SNK-LAYS-MASALA-50G", "Lay's India's Magic Masala 50g", 301, 1.49, "50g", 140, "Lay's", "50g",
     "chips,lays,lay's,masala chips,magic masala,snack,snacks", 78, True, 460,
     "Lay's India's Magic Masala flavour"),
    ("SNK-KURKURE-70G", "Kurkure Masala Munch 70g", 301, 1.29, "70g", 120, "PepsiCo", "70g",
     "kurkure,masala,namkeen,snack,snacks,chips", 75, True, 395,
     "Kurkure crunchy masala snack"),

    # ===== Namkeen & Mixtures (L2=302) =====
    ("SNK-HALDIRAM-BHUJIA-400G", "Haldiram's Bhujia 400g", 302, 3.49, "400g", 70, "Haldiram's", "400g",
     "bhujia,haldiram,namkeen,snack,snacks,mixture", 60, False, 250,
     "Haldiram's famous bikaneri bhujia"),

    # ===== Chocolates (L2=401) =====
    ("SNK-DAIRY-MILK-60G", "Cadbury Dairy Milk 60g", 401, 1.49, "60g", 150, "Cadbury", "60g",
     "chocolate,dairy milk,cadbury,mithai,meetha,snack,snacks", 85, True, 520,
     "Cadbury Dairy Milk chocolate"),
    ("SNK-KITKAT-42G", "KitKat 4 Finger 42g", 401, 1.49, "42g", 130, "Nestle", "42g",
     "chocolate,kitkat,kit kat,nestle,wafer,snack,snacks", 70, False, 300,
     "KitKat crispy wafer chocolate"),
    ("SNK-5STAR-50G", "5 Star 50g", 401, 1.29, "50g", 120, "Cadbury", "50g",
     "chocolate,5 star,five star,cadbury,snack,snacks", 55, False, 200,
     "Cadbury 5 Star caramel nougat bar"),

    # ===== Biscuits & Cookies (L2=402) =====
    ("BKR-BISCUIT-PARLEG-200G", "Parle-G 200g", 402, 0.99, "200g", 200, "Parle", "200g",
     "biscuit,biscuits,parle g,parle-g,glucose biscuit,cookies", 88, True, 550,
     "India's favourite glucose biscuit"),
    ("BKR-BISCUIT-GOODDAY-200G", "Good Day Butter 200g", 402, 1.99, "200g", 100, "Britannia", "200g",
     "biscuit,biscuits,good day,britannia,butter biscuit,cookies", 65, False, 280,
     "Britannia Good Day butter cookies"),
    ("SNK-OREO-133G", "Oreo 133g", 402, 2.49, "133g", 90, "Mondelez", "133g",
     "oreo,biscuit,cookies,cream biscuit,chocolate biscuit,snack,snacks", 62, False, 260,
     "Oreo chocolate sandwich cookies"),

    # ===== Soft Drinks (L2=501) =====
    ("BEV-COCACOLA-2L", "Coca Cola 2L", 501, 2.49, "2L", 100, "Coca Cola", "2L",
     "coca cola,coke,cola,cold drink,soft drink,soda,beverage", 80, True, 450,
     "Coca Cola carbonated soft drink"),
    ("BEV-PEPSI-2L", "Pepsi 2L", 501, 2.49, "2L", 95, "Pepsi", "2L",
     "pepsi,cola,cold drink,soft drink,soda,beverage", 75, True, 400,
     "Pepsi carbonated soft drink"),
    ("BEV-SPRITE-2L", "Sprite 2L", 501, 2.49, "2L", 85, "Coca Cola", "2L",
     "sprite,lemon,lime,cold drink,soft drink,soda,beverage", 65, False, 280,
     "Sprite clear lemon lime drink"),

    # ===== Fruit Juices (L2=502) =====
    ("BEV-REAL-MANGO-1L", "Real Mango Juice 1L", 502, 3.99, "1L", 80, "Dabur", "1L",
     "mango juice,real juice,dabur,juice,fruit juice,beverage", 70, True, 320,
     "Real fruit power mango juice"),
    ("BEV-OJ-1L", "Tropicana Orange 1L", 502, 5.99, "1L", 50, "Tropicana", "1L",
     "orange juice,tropicana,juice,fruit juice,beverage", 55, False, 180,
     "Tropicana 100% orange juice"),

    # ===== Water (L2=503) =====
    ("BEV-BISLERI-1L", "Bisleri Water 1L", 503, 0.49, "1L", 300, "Bisleri", "1L",
     "water,pani,mineral water,bisleri,packaged water,beverage", 60, False, 500,
     "Bisleri packaged drinking water"),

    # ===== Tea (L2=601) =====
    ("BEV-TATA-TEA-500G", "Tata Tea 500g", 601, 4.99, "500g", 100, "Tata", "500g",
     "tea,chai,tata tea,black tea,beverage", 82, True, 480,
     "Premium Tata Tea gold"),

    # ===== Coffee (L2=602) =====
    ("BEV-NESCAFE-200G", "Nescafe Classic 200g", 602, 9.99, "200g", 70, "Nescafe", "200g",
     "coffee,nescafe,instant coffee,beverage,cafe", 78, True, 350,
     "Nescafe Classic instant coffee"),

    # ===== Noodles & Pasta (L2=701) =====
    ("NP-MAGGI-70G", "Maggi 2-Minute Noodles 70g", 701, 0.99, "70g", 200, "Nestle", "70g",
     "maggi,noodles,instant noodles,2 minute noodles,nestle", 90, True, 600,
     "Maggi masala 2-minute instant noodles"),
    ("NP-MAGGI-5PACK", "Maggi 5 Pack 350g", 701, 3.99, "350g", 100, "Nestle", "350g",
     "maggi,noodles,instant noodles,family pack,nestle", 80, True, 350,
     "Maggi 2-minute noodles family pack"),
    ("NP-YIPPEE-70G", "Yippee Noodles 70g", 701, 0.99, "70g", 120, "ITC", "70g",
     "yippee,noodles,instant noodles,itc", 65, False, 250,
     "Sunfeast Yippee magic masala noodles"),
    ("NP-PASTA-500G", "Penne Pasta 500g", 701, 2.99, "500g", 80, "Barilla", "500g",
     "pasta,penne,italian,macaroni,noodles", 50, False, 150,
     "Barilla penne rigate pasta"),

    # ===== Atta & Flours (L2=801) =====
    ("STP-ATTA-5KG", "Aashirvaad Atta 5kg", 801, 8.99, "5kg", 60, "Aashirvaad", "5kg",
     "atta,aata,wheat flour,aashirvaad,flour,chapati,roti,staples", 90, True, 500,
     "Aashirvaad whole wheat atta"),

    # ===== Rice (L2=802) =====
    ("STP-RICE-5KG", "India Gate Basmati Rice 5kg", 802, 14.99, "5kg", 50, "India Gate", "5kg",
     "rice,chawal,basmati,india gate,staples", 85, True, 400,
     "Premium aged basmati rice"),

    # ===== Dals & Pulses (L2=803) =====
    ("STP-TOOR-DAL-1KG", "Toor Dal 1kg", 803, 4.99, "1kg", 80, "Tata Sampann", "1kg",
     "dal,daal,toor dal,arhar dal,pulses,lentils,staples", 78, True, 380,
     "Premium unpolished toor dal"),

    # ===== Cooking Oil (L2=901) =====
    ("STP-OIL-FORTUNE-1L", "Fortune Sunflower Oil 1L", 901, 3.49, "1L", 70, "Fortune", "1L",
     "oil,tel,cooking oil,sunflower oil,fortune,staples", 80, True, 450,
     "Fortune refined sunflower oil"),

    # ===== Spices (L2=902) =====
    ("STP-HALDI-100G", "Everest Turmeric 100g", 902, 1.49, "100g", 90, "Everest", "100g",
     "turmeric,haldi,spice,masala,everest", 55, False, 280,
     "Pure turmeric powder"),
    ("STP-MIRCH-100G", "Everest Red Chilli 100g", 902, 1.49, "100g", 85, "Everest", "100g",
     "red chilli,lal mirch,mirchi,spice,masala,everest", 50, False, 260,
     "Pure red chilli powder"),

    # ===== Salt & Sugar (L2=903) =====
    ("STP-SUGAR-1KG", "Sugar 1kg", 903, 1.99, "1kg", 100, "Local", "1kg",
     "sugar,cheeni,chini,shakkar", 72, True, 420,
     "Fine grain white sugar"),
    ("STP-SALT-1KG", "Tata Salt 1kg", 903, 0.49, "1kg", 150, "Tata", "1kg",
     "salt,namak,tata salt,iodized salt", 65, True, 550,
     "Tata iodized salt"),

    # ===== Soaps & Body Wash (L2=1001) =====
    ("PC-DOVE-SOAP-100G", "Dove Cream Bar 100g", 1001, 1.99, "100g", 100, "Dove", "100g",
     "soap,sabun,dove,bath soap,body wash,personal care", 72, True, 350,
     "Dove moisturizing cream beauty bar"),
    ("PC-LIFEBUOY-75G", "Lifebuoy Soap 75g", 1001, 0.69, "75g", 120, "Lifebuoy", "75g",
     "soap,sabun,lifebuoy,bath soap,germ protection,personal care", 65, True, 400,
     "Lifebuoy germ protection soap"),

    # ===== Hair Care (L2=1002) =====
    ("PC-DOVE-SHAMPOO-180ML", "Dove Shampoo 180ml", 1002, 3.99, "180ml", 80, "Dove", "180ml",
     "shampoo,dove,hair care,hair wash,personal care", 60, False, 250,
     "Dove intense repair shampoo"),
    ("PC-PARACHUTE-200ML", "Parachute Coconut Oil 200ml", 1002, 2.99, "200ml", 100, "Parachute", "200ml",
     "coconut oil,hair oil,parachute,nariyal tel,personal care", 68, True, 380,
     "Parachute pure coconut oil"),

    # ===== Oral Care (L2=1003) =====
    ("PC-COLGATE-150G", "Colgate Toothpaste 150g", 1003, 1.99, "150g", 120, "Colgate", "150g",
     "toothpaste,colgate,dant manjan,oral care,personal care", 80, True, 500,
     "Colgate strong teeth toothpaste"),

    # ===== Detergents (L2=1101) =====
    ("HH-SURF-1KG", "Surf Excel 1kg", 1101, 12.99, "1kg", 60, "Surf Excel", "1kg",
     "detergent,surf,surf excel,washing powder,kapde dhone ka,household", 78, True, 350,
     "Surf Excel easy wash detergent"),

    # ===== Dishwash (L2=1102) =====
    ("HH-VIM-500G", "Vim Bar 500g", 1102, 1.49, "500g", 100, "Vim", "500g",
     "dishwash,vim,bartan,utensil cleaner,household", 70, True, 400,
     "Vim dish wash bar"),

    # ===== Cleaners (L2=1103) =====
    ("HH-HARPIC-1L", "Harpic 1L", 1103, 3.99, "1L", 80, "Harpic", "1L",
     "toilet cleaner,harpic,bathroom cleaner,household", 62, False, 280,
     "Harpic toilet cleaning liquid"),
    ("HH-LIZOL-500ML", "Lizol Floor Cleaner 500ml", 1103, 4.49, "500ml", 65, "Lizol", "500ml",
     "floor cleaner,lizol,phenyl,mopping,household", 55, False, 220,
     "Lizol disinfectant surface cleaner"),

    # ===== Diapers & Wipes (L2=1201) =====
    ("BB-PAMPERS-M-44", "Pampers M 44 Pcs", 1201, 14.99, "44 pcs", 50, "Pampers", "44 pcs",
     "diaper,diapers,pampers,nappy,baby,baby care", 75, True, 280,
     "Pampers all round protection diapers"),

    # ===== Baby Food (L2=1202) =====
    ("BB-CERELAC-300G", "Cerelac Wheat 300g", 1202, 5.99, "300g", 60, "Nestle", "300g",
     "cerelac,baby food,nestle,infant food,baby care", 68, True, 200,
     "Nestle Cerelac baby cereal"),

    # ===== Baby Bath & Skin (L2=1203) =====
    ("BB-JOHNSONS-SOAP-100G", "Johnson's Baby Soap 100g", 1203, 1.99, "100g", 80, "Johnson's", "100g",
     "baby soap,johnsons,johnson's,baby bath,baby care", 60, False, 180,
     "Gentle baby soap by Johnson's"),

    # ===== Chicken (L2=1301) =====
    ("MT-CHICKEN-1KG", "Chicken Curry Cut 1kg", 1301, 6.99, "1kg", 40, "Fresh Poultry", "1kg",
     "chicken,murga,murgi,meat,non veg,poultry", 80, True, 350,
     "Fresh chicken curry cut pieces"),

    # ===== Mutton (L2=1302) =====
    ("MT-MUTTON-1KG", "Mutton 1kg", 1302, 18.99, "1kg", 25, "Fresh Meat", "1kg",
     "mutton,gosht,goat meat,meat,non veg", 70, False, 200,
     "Fresh mutton curry cut"),

    # ===== Fish & Seafood (L2=1303) =====
    ("MT-FISH-ROHU-1KG", "Rohu Fish 1kg", 1303, 7.99, "1kg", 30, "Fresh Catch", "1kg",
     "fish,rohu,machli,machhi,seafood,meat,non veg", 65, False, 180,
     "Fresh Rohu fish, cleaned"),

    # ===== Ice Cream (L2=1501) =====
    ("FRZ-AMUL-IC-1L", "Amul Ice Cream 1L", 1501, 4.99, "1L", 60, "Amul", "1L",
     "ice cream,amul,frozen,dessert,kulfi", 72, True, 300,
     "Amul vanilla ice cream"),

    # ===== Frozen Snacks (L2=1502) =====
    ("FRZ-FRIES-500G", "French Fries 500g", 1502, 2.99, "500g", 70, "McCain", "500g",
     "french fries,fries,frozen fries,mccain,frozen snacks", 60, False, 220,
     "McCain crispy french fries"),

    # ===== Frozen Vegetables (L2=1503) =====
    ("FRZ-PEAS-500G", "Frozen Peas 500g", 1503, 2.99, "500g", 80, "Safal", "500g",
     "frozen peas,matar,peas,frozen vegetables", 55, False, 180,
     "Safal frozen green peas"),
]


def build_items():
    items = []
    for p in PRODUCTS:
        sku, name, cat_id, price, uom, stock, brand, pkg, kw, priority, bestseller, orders, desc = p
        img = IMG(PH)
        items.append({
            "sku": sku, "name": name, "categoryId": cat_id,
            "basePrice": float(price), "unitOfMeasure": uom,
            "currentStock": int(stock), "brand": brand, "packageSize": pkg,
            "images": img, "isActive": True, "searchKeywords": kw,
            "searchPriority": priority, "isBestseller": bestseller,
            "orderCount": orders, "description": desc,
        })
    return items


def sync_batch(items):
    payload = json.dumps({"storeId": STORE_ID, "items": items}).encode("utf-8")
    req = urllib.request.Request(API_URL, data=payload, headers={"Content-Type": "application/json"}, method="POST")
    with urllib.request.urlopen(req, timeout=120) as resp:
        return json.load(resp)


def main():
    print(f"Seeding {len(PRODUCTS)} products to {API_URL} (storeId={STORE_ID})")
    items = build_items()
    batch_size = 50
    total_ok = total_fail = 0
    for i in range(0, len(items), batch_size):
        batch = items[i:i + batch_size]
        try:
            result = sync_batch(batch)
            ok, fail = result.get("successCount", 0), result.get("failureCount", 0)
            total_ok += ok; total_fail += fail
            print(f"  Batch {i // batch_size + 1}: {ok} ok, {fail} fail")
            if fail:
                for r in result.get("results", []):
                    if r.get("status") == "FAILED":
                        print(f"    FAIL {r.get('sku')}: {r.get('errorMessage', '?')}")
        except Exception as e:
            print(f"  ERROR: {e}"); sys.exit(1)
        if i + batch_size < len(items): time.sleep(0.3)
    print(f"\nDone: {total_ok} synced, {total_fail} failed")


if __name__ == "__main__":
    main()
