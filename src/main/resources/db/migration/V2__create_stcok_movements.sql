ALTER TABLE inventories
DROP COLUMN movement_type;

-- 5. Stock movements
CREATE TABLE stock_movements (
    id UUID PRIMARY KEY ,
    product_id UUID NOT NULL REFERENCES products (id) ON DELETE CASCADE ,
    source_warehouse_id UUID REFERENCES warehouses (id) ON DELETE CASCADE ,
    destination_warehouse_id UUID REFERENCES warehouses (id) ON DELETE CASCADE ,
    quantity INT NOT NULL ,
    movement_type VARCHAR(60) NOT NULL ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_stock_quantity
                             CHECK (quantity > 0),

    CONSTRAINT chk_stock_movement_type
                             CHECK ( movement_type IN ('SALE', 'TRANSFER', 'PURCHASE')),

    CONSTRAINT chk_stock_movement_warehouses
                             CHECK (
                                 (movement_type = 'PURCHASE'
                                     AND source_warehouse_id IS NULL
                                     AND destination_warehouse_id IS NOT NULL)

                                 OR

                                 (movement_type = 'SALE'
                                     AND source_warehouse_id IS NOT NULL
                                     AND destination_warehouse_id IS NULL)

                                 OR

                                 (movement_type = 'TRANSFER'
                                     AND source_warehouse_id IS NOT NULL
                                     AND destination_warehouse_id IS NOT NULL)
                                 )
);