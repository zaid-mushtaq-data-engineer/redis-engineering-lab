CREATE TABLE products (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100)  NOT NULL,
    description VARCHAR(500),
    price       DECIMAL(10,2) NOT NULL,
    status      VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    version     BIGINT        NOT NULL DEFAULT 0,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    CONSTRAINT chk_product_price  CHECK (price > 0),
    CONSTRAINT chk_product_status CHECK (status IN ('ACTIVE','INACTIVE'))
);
