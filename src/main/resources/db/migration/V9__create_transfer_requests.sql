CREATE TABLE transfer_requests (
    id UUID PRIMARY KEY ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    company_id UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE ,
    product_id UUID NOT NULL REFERENCES products (id) ,
    source_warehouse_id UUID NOT NULL REFERENCES warehouses (id),
    destination_warehouse_id UUID NOT NULL REFERENCES warehouses (id)  ,
    quantity INT NOT NULL CHECK ( quantity > 0 ),
    status VARCHAR(50) NOT NULL ,
    order_id UUID REFERENCES customer_orders (id) ON DELETE SET NULL,

    CHECK ( source_warehouse_id <> destination_warehouse_id )
);