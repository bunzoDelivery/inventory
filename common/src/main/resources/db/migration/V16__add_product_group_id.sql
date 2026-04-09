-- Add group_id to connect variant products
ALTER TABLE products ADD COLUMN group_id VARCHAR(255) COMMENT 'Used to group variant products together (e.g. sizes)';
CREATE INDEX idx_group_id ON products(group_id);
