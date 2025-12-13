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


