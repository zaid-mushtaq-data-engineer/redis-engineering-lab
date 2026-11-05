CREATE TABLE inventory (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id     BIGINT      NOT NULL UNIQUE,
    stock_quantity INT         NOT NULL DEFAULT 0,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     DATETIME(6) NOT NULL,
    updated_at     DATETIME(6) NOT NULL,
    CONSTRAINT chk_inventory_quantity CHECK (stock_quantity >= 0),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT
);
