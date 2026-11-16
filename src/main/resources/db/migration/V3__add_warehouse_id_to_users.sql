ALTER TABLE users
ADD COLUMN warehouse_id UUID;

ALTER TABLE users
ADD CONSTRAINT fk_users_warehouse
FOREIGN KEY (warehouse_id)
REFERENCES warehouses (id)
ON DELETE SET NULL