-- Create products table for catalog items
CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(255) NOT NULL UNIQUE COMMENT 'Stock Keeping Unit - unique identifier',
    name VARCHAR(255) NOT NULL COMMENT 'Product name',
    description TEXT COMMENT 'Detailed product description',
    short_description VARCHAR(500) COMMENT 'Brief description for listings',
    category_id BIGINT NOT NULL COMMENT 'Category this product belongs to',
    brand VARCHAR(255) COMMENT 'Brand name',
    base_price DECIMAL(10, 2) NOT NULL COMMENT 'Base price before discounts',
    unit_of_measure VARCHAR(50) NOT NULL COMMENT 'e.g., kg, piece, liter',
    package_size VARCHAR(100) COMMENT 'e.g., 500g, 1L, 12 pieces',
    images TEXT COMMENT 'JSON array of image URLs',
    tags VARCHAR(500) COMMENT 'Comma-separated tags for search',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Product active in catalog',
    is_available BOOLEAN DEFAULT TRUE COMMENT 'Currently available for purchase',
    slug VARCHAR(255) NOT NULL UNIQUE COMMENT 'URL-friendly identifier',
    nutritional_info TEXT COMMENT 'Nutritional information (JSON)',
    weight_grams INT COMMENT 'Product weight for shipping',
    barcode VARCHAR(100) COMMENT 'Barcode/UPC/EAN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Foreign key to categories
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE RESTRICT,

    -- Indexes for performance
    INDEX idx_sku (sku),
    INDEX idx_category_id (category_id),
    INDEX idx_brand (brand),
    INDEX idx_slug (slug),
    INDEX idx_is_active (is_active),
    INDEX idx_is_available (is_available),
    INDEX idx_base_price (base_price),

    -- Full-text search index for product search
    FULLTEXT INDEX ft_search (name, description, tags)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert sample products for testing
INSERT IGNORE INTO products
(id, sku, name, description, short_description, category_id, brand, base_price, unit_of_measure, package_size, tags, is_active, is_available, slug, weight_grams)
VALUES
-- Fruits
(1, 'FRUIT-001', 'Bananas', 'Fresh ripe bananas from local farms', 'Fresh ripe bananas', 11, 'Local Farm', 15.00, 'kg', '1kg', 'fruit,fresh,banana,local', TRUE, TRUE, 'bananas', 1000),
(2, 'FRUIT-002', 'Apples', 'Crisp red apples', 'Fresh red apples', 11, 'Import', 25.00, 'kg', '1kg', 'fruit,fresh,apple,imported', TRUE, TRUE, 'apples', 1000),
(3, 'FRUIT-003', 'Oranges', 'Juicy oranges perfect for juice', 'Fresh oranges', 11, 'Local Farm', 20.00, 'kg', '1kg', 'fruit,fresh,orange,citrus', TRUE, TRUE, 'oranges', 1000),

-- Vegetables
(4, 'VEG-001', 'Tomatoes', 'Fresh tomatoes', 'Fresh ripe tomatoes', 12, 'Local Farm', 18.00, 'kg', '1kg', 'vegetable,fresh,tomato', TRUE, TRUE, 'tomatoes', 1000),
(5, 'VEG-002', 'Onions', 'Fresh onions', 'Quality onions', 12, 'Local Farm', 12.00, 'kg', '1kg', 'vegetable,fresh,onion', TRUE, TRUE, 'onions', 1000),
(6, 'VEG-003', 'Potatoes', 'Fresh potatoes', 'Local potatoes', 12, 'Local Farm', 10.00, 'kg', '2kg', 'vegetable,fresh,potato', TRUE, TRUE, 'potatoes', 2000),

-- Dairy
(7, 'DAIRY-001', 'Fresh Milk', 'Farm fresh whole milk', 'Fresh whole milk', 21, 'Dairy Best', 12.00, 'liter', '1L', 'dairy,milk,fresh', TRUE, TRUE, 'fresh-milk', 1050),
(8, 'DAIRY-002', 'Cheddar Cheese', 'Mature cheddar cheese', 'Cheddar cheese block', 22, 'Dairy Best', 45.00, 'piece', '500g', 'dairy,cheese,cheddar', TRUE, TRUE, 'cheddar-cheese', 500),

-- Meat
(9, 'MEAT-001', 'Chicken Breast', 'Boneless chicken breast', 'Fresh chicken breast', 31, 'Poultry Fresh', 65.00, 'kg', '1kg', 'meat,chicken,breast,fresh', TRUE, TRUE, 'chicken-breast', 1000),
(10, 'MEAT-002', 'Beef Mince', 'Premium beef mince', 'Fresh beef mince', 32, 'Butcher Best', 85.00, 'kg', '500g', 'meat,beef,mince,fresh', TRUE, TRUE, 'beef-mince', 500);
