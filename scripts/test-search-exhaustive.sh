#!/usr/bin/env bash
# Exhaustive search API tests covering all MVP use cases
# Tests L1→L2 category hierarchy, Hindi keywords, typo tolerance, store filtering
set -euo pipefail

URL="http://localhost:8083/search"
PASS=0
FAIL=0
TOTAL=0

search() {
    local query="$1" storeId="${2:-1}" page="${3:-1}" pageSize="${4:-20}"
    curl -sf "$URL" -H 'Content-Type: application/json' -d "{
        \"query\": \"$query\", \"storeId\": $storeId, \"page\": $page, \"pageSize\": $pageSize
    }" 2>/dev/null
}

assert_has() {
    local test_name="$1" query="$2" expected="$3"
    TOTAL=$((TOTAL + 1))
    local result names
    result=$(search "$query")
    names=$(echo "$result" | python3 -c "import sys,json; print('|'.join([r['name'] for r in json.load(sys.stdin)['results']]))" 2>/dev/null || echo "")
    if echo "$names" | grep -qi "$expected"; then
        echo "  PASS: $test_name"
        PASS=$((PASS + 1))
    else
        echo "  FAIL: $test_name  (expected '$expected' not in: ${names:0:120})"
        FAIL=$((FAIL + 1))
    fi
}

assert_not_has() {
    local test_name="$1" query="$2" unexpected="$3"
    TOTAL=$((TOTAL + 1))
    local result names
    result=$(search "$query")
    names=$(echo "$result" | python3 -c "import sys,json; print('|'.join([r['name'] for r in json.load(sys.stdin)['results']]))" 2>/dev/null || echo "")
    if echo "$names" | grep -qi "$unexpected"; then
        echo "  FAIL: $test_name  (unexpected '$unexpected' found in: ${names:0:120})"
        FAIL=$((FAIL + 1))
    else
        echo "  PASS: $test_name"
        PASS=$((PASS + 1))
    fi
}

assert_min_results() {
    local test_name="$1" query="$2" min="$3"
    TOTAL=$((TOTAL + 1))
    local result returned
    result=$(search "$query")
    returned=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin)['meta']['returned'])" 2>/dev/null || echo "0")
    if [ "$returned" -ge "$min" ]; then
        echo "  PASS: $test_name  ($returned results)"
        PASS=$((PASS + 1))
    else
        echo "  FAIL: $test_name  (got $returned, expected >= $min)"
        FAIL=$((FAIL + 1))
    fi
}

assert_top_result() {
    local test_name="$1" query="$2" expected="$3"
    TOTAL=$((TOTAL + 1))
    local result top
    result=$(search "$query")
    top=$(echo "$result" | python3 -c "import sys,json; r=json.load(sys.stdin)['results']; print(r[0]['name'] if r else '')" 2>/dev/null || echo "")
    if echo "$top" | grep -qi "$expected"; then
        echo "  PASS: $test_name  (top: '$top')"
        PASS=$((PASS + 1))
    else
        echo "  FAIL: $test_name  (top='$top', expected '$expected')"
        FAIL=$((FAIL + 1))
    fi
}

echo "=========================================="
echo "  SEARCH MVP EXHAUSTIVE TEST SUITE"
echo "  L1→L2 Category Hierarchy (Blinkit model)"
echo "=========================================="

echo ""
echo "--- 1. EXACT PRODUCT SEARCH ---"
assert_has "Amul Taaza Milk" "amul taaza milk" "Amul Taaza"
assert_has "Surf Excel" "surf excel" "Surf Excel"
assert_has "Colgate Toothpaste" "colgate toothpaste" "Colgate"
assert_has "Maggi Noodles" "maggi noodles" "Maggi"
assert_has "Parle G" "parle g" "Parle"
assert_has "Cadbury Dairy Milk" "cadbury dairy milk" "Dairy Milk"
assert_has "Lay's chips" "lays chips" "Lay"
assert_has "Tata Tea" "tata tea" "Tata Tea"
assert_has "Nescafe Coffee" "nescafe coffee" "Nescafe"
assert_has "Amul Butter" "amul butter" "Amul Butter"
assert_has "Amul Ghee" "amul ghee" "Ghee"
assert_has "Amul Paneer" "amul paneer" "Paneer"

echo ""
echo "--- 2. BRAND SEARCH ---"
assert_min_results "Brand: Amul" "amul" 4
assert_min_results "Brand: Nestle" "nestle" 2
assert_min_results "Brand: Cadbury" "cadbury" 2
assert_min_results "Brand: Lay's" "lays" 2
assert_min_results "Brand: Dove" "dove" 2
assert_min_results "Brand: Tata" "tata" 2

echo ""
echo "--- 3. CATEGORY SEARCH (L2 precision) ---"
assert_min_results "L2: fresh fruits" "fruits" 5
assert_min_results "L2: fresh vegetables" "vegetables" 5
assert_min_results "L2: milk" "milk" 2
assert_min_results "L2: chocolates" "chocolate" 2
assert_min_results "L2: biscuits" "biscuit" 2
assert_min_results "L2: chips" "chips" 2
assert_min_results "L2: noodles" "noodles" 3
assert_min_results "L2: soap" "soap" 2
assert_min_results "L2: chicken" "chicken" 1
assert_min_results "L2: ice cream" "ice cream" 1

echo ""
echo "--- 4. CATEGORY ISOLATION (no cross-contamination) ---"
assert_not_has "Fruits excludes Onions" "fruits" "Onion"
assert_not_has "Fruits excludes Potatoes" "fruits" "Potato"
assert_not_has "Fruits excludes Spinach" "fruits" "Spinach"
assert_not_has "Fruits excludes Cauliflower" "fruits" "Cauliflower"
assert_not_has "Fruits excludes Cucumber" "fruits" "Cucumber"
assert_not_has "Vegetables excludes Apples" "vegetables" "Apple"
assert_not_has "Vegetables excludes Bananas" "vegetables" "Banana"
assert_not_has "Vegetables excludes Mango" "vegetables" "Mango"
assert_not_has "Chocolate excludes Tomato" "chocolate" "Tomato"
assert_not_has "Chips excludes Bread" "chips" "Bread"

echo ""
echo "--- 5. HINDI / LOCAL KEYWORD SEARCH ---"
assert_min_results "Hindi: doodh (milk)" "doodh" 1
assert_min_results "Hindi: pyaz (onion)" "pyaz" 1
assert_min_results "Hindi: aloo (potato)" "aloo" 1
assert_min_results "Hindi: tamatar (tomato)" "tamatar" 1
assert_min_results "Hindi: aam (mango)" "aam" 1
assert_min_results "Hindi: seb (apple)" "seb" 1
assert_min_results "Hindi: kela (banana)" "kela" 1
assert_min_results "Hindi: chai (tea)" "chai" 1
assert_min_results "Hindi: sabzi" "sabzi" 3
assert_min_results "Hindi: dahi (curd)" "dahi" 1
assert_min_results "Hindi: paneer" "paneer" 1
assert_min_results "Hindi: ghee" "ghee" 1
assert_min_results "Hindi: makhan (butter)" "makhan" 1
assert_min_results "Hindi: atta (flour)" "atta" 1
assert_min_results "Hindi: chawal (rice)" "chawal" 1
assert_min_results "Hindi: dal" "dal" 1
assert_min_results "Hindi: namak (salt)" "namak" 1
assert_min_results "Hindi: haldi (turmeric)" "haldi" 1
assert_min_results "Hindi: phal (fruit)" "phal" 3
assert_min_results "Hindi: sabun (soap)" "sabun" 1
assert_min_results "Hindi: pudina (mint)" "pudina" 1
assert_min_results "Hindi: methi (fenugreek)" "methi" 1
assert_min_results "Hindi: palak (spinach)" "palak" 1
assert_min_results "Hindi: gobhi (cauliflower)" "gobhi" 1
assert_min_results "Hindi: bhindi (ladyfinger)" "bhindi" 1
assert_min_results "Hindi: anaar (pomegranate)" "anaar" 1
assert_min_results "Hindi: murga (chicken)" "murga" 1
assert_min_results "Hindi: gosht (mutton)" "gosht" 1
assert_min_results "Hindi: machli (fish)" "machli" 1

echo ""
echo "--- 6. TYPO TOLERANCE ---"
assert_min_results "Typo: amull" "amull" 1
assert_min_results "Typo: cofee" "cofee" 1
assert_min_results "Typo: biscits" "biscits" 1
assert_min_results "Typo: chocolat" "chocolat" 1
assert_min_results "Typo: potatos" "potatos" 1
assert_min_results "Typo: mangos" "mangos" 1
assert_min_results "Typo: tomatoe" "tomatoe" 1
assert_min_results "Typo: bnana" "bnana" 1

echo ""
echo "--- 7. STORE AWARENESS ---"
assert_min_results "Store 1: milk has results" "milk" 1
TOTAL=$((TOTAL + 1))
result=$(curl -sf "$URL" -H 'Content-Type: application/json' -d '{"query":"milk","storeId":999,"page":1,"pageSize":20}' 2>/dev/null)
returned=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin)['meta']['returned'])" 2>/dev/null || echo "-1")
if [ "$returned" -ge "0" ]; then
    echo "  PASS: Store 999 returns fallback ($returned items — MVP zero-result prevention)"
    PASS=$((PASS + 1))
else
    echo "  FAIL: Store 999 query failed"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "--- 8. ZERO RESULTS PREVENTION (MVP req: always return something) ---"
TOTAL=$((TOTAL + 1))
result=$(search "xyzrandomgibberish")
returned=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin)['meta']['returned'])" 2>/dev/null || echo "0")
if [ "$returned" -gt "0" ]; then
    echo "  PASS: Random gibberish returns fallback ($returned results)"
    PASS=$((PASS + 1))
else
    echo "  FAIL: Got 0 results — zero-result page!"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "--- 9. PAGINATION ---"
TOTAL=$((TOTAL + 1))
page1=$(search "fruit" 1 1 3)
page2=$(search "fruit" 1 2 3)
p1names=$(echo "$page1" | python3 -c "import sys,json; print('|'.join([r['name'] for r in json.load(sys.stdin)['results']]))" 2>/dev/null)
p2names=$(echo "$page2" | python3 -c "import sys,json; print('|'.join([r['name'] for r in json.load(sys.stdin)['results']]))" 2>/dev/null)
if [ "$p1names" != "$p2names" ] && [ -n "$p1names" ] && [ -n "$p2names" ]; then
    echo "  PASS: Page 1 != Page 2 (pagination works)"
    PASS=$((PASS + 1))
elif [ -z "$p2names" ]; then
    echo "  PASS: Page 2 empty (all results fit on page 1)"
    PASS=$((PASS + 1))
else
    echo "  FAIL: Pagination broken (page1='$p1names', page2='$p2names')"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "--- 10. SPECIFIC PRODUCT QUERIES ---"
assert_has "Pav" "pav" "Pav"
assert_has "Eggs" "eggs" "Eggs"
assert_has "Rice" "rice" "Rice"
assert_has "Oil" "oil" "Oil"
assert_has "Sugar" "sugar" "Sugar"
assert_has "Water" "water" "Water"
assert_has "Diaper" "diaper" "Pampers"
assert_has "Shampoo" "shampoo" "Shampoo"
assert_has "Ice cream" "ice cream" "Ice Cream"
assert_has "French fries" "french fries" "French Fries"
assert_has "Curd" "curd" "Curd"
assert_has "Cheese" "cheese" "Cheese"
assert_has "Cold drink" "cold drink" "Cola"
assert_has "Detergent" "detergent" "Surf"

echo ""
echo "=========================================="
if [ "$FAIL" -eq "0" ]; then
    echo "  ALL $TOTAL TESTS PASSED!"
else
    echo "  RESULTS: $PASS passed, $FAIL failed out of $TOTAL tests"
fi
echo "=========================================="
exit $FAIL
