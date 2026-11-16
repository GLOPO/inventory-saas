-- 1. Companies table
CREATE TABLE companies (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. Warehouse table
CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    code VARCHAR(50) NOT NULL,
    company_id UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. Products
CREATE TABLE products (
    id UUID PRIMARY KEY,
    sku VARCHAR(255) NOT NULL ,
    name VARCHAR(255) NOT NULL ,
    company_id UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. Inventories Junction
CREATE TABLE inventories (
    id UUID PRIMARY KEY ,
    warehouse_id UUID NOT NULL REFERENCES warehouses (id) ON DELETE CASCADE ,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE ,
    quantity INT NOT NULL ,
    movement_type VARCHAR(50) NOT NULL , -- e.g SALE, TRANSFER, PURCHASE;
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. users
CREATE TABLE users (
    id UUID PRIMARY KEY ,
    email VARCHAR(255) NOT NULL ,
    password VARCHAR(150) NOT NULL ,
    company_id UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE ,
    role VARCHAR(50) NOT NULL ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);