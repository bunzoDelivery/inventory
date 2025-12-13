-- Create categories table for hierarchical product categorization
CREATE TABLE IF NOT EXISTS categories (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    parent_id BIGINT DEFAULT NULL COMMENT 'Parent category for hierarchy (NULL for root)',
    slug VARCHAR(255) NOT NULL UNIQUE COMMENT 'URL-friendly identifier',
    display_order INT NOT NULL DEFAULT 0 COMMENT 'Sort order for display',
    is_active BOOLEAN DEFAULT TRUE COMMENT 'Whether category is visible',
    image_url VARCHAR(500) COMMENT 'Category image/icon URL',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    -- Indexes for performance
    INDEX idx_parent_id (parent_id),
    INDEX idx_slug (slug),
    INDEX idx_active (is_active),
    INDEX idx_display_order (display_order),

    -- Self-referencing foreign key for hierarchy
    FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Insert initial categories for Lusaka quick commerce
INSERT IGNORE INTO categories (id, name, description, parent_id, slug, display_order, is_active, image_url) VALUES
(1, 'Fresh Produce', 'Fresh fruits and vegetables', NULL, 'fresh-produce', 1, TRUE, NULL),
(2, 'Dairy & Eggs', 'Milk, cheese, yogurt, and eggs', NULL, 'dairy-eggs', 2, TRUE, NULL),
(3, 'Meat & Poultry', 'Fresh meat, chicken, and fish', NULL, 'meat-poultry', 3, TRUE, NULL),
(4, 'Bakery', 'Bread, pastries, and baked goods', NULL, 'bakery', 4, TRUE, NULL),
(5, 'Pantry Staples', 'Rice, flour, cooking oil, spices', NULL, 'pantry-staples', 5, TRUE, NULL),
(6, 'Beverages', 'Water, juice, soft drinks, tea, coffee', NULL, 'beverages', 6, TRUE, NULL),
(7, 'Snacks', 'Chips, biscuits, chocolates', NULL, 'snacks', 7, TRUE, NULL),
(8, 'Household', 'Cleaning supplies, toiletries', NULL, 'household', 8, TRUE, NULL);

-- Insert subcategories
INSERT IGNORE INTO categories (id, name, description, parent_id, slug, display_order, is_active) VALUES
(11, 'Fruits', 'Fresh seasonal fruits', 1, 'fruits', 1, TRUE),
(12, 'Vegetables', 'Fresh vegetables', 1, 'vegetables', 2, TRUE),
(21, 'Milk', 'Fresh and long-life milk', 2, 'milk', 1, TRUE),
(22, 'Cheese', 'Local and imported cheese', 2, 'cheese', 2, TRUE),
(31, 'Chicken', 'Fresh chicken cuts', 3, 'chicken', 1, TRUE),
(32, 'Beef', 'Fresh beef cuts', 3, 'beef', 2, TRUE);
