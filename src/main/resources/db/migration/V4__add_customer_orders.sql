CREATE TABLE customer_orders (
                                 id UUID PRIMARY KEY ,
                                 company_id UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE ,
                                 matched_product_id UUID REFERENCES products (id) ON DELETE SET NULL ,
                                 fulfilled_warehouse_id UUID REFERENCES warehouses (id) ON DELETE SET NULL ,
                                 requested_product_name TEXT NOT NULL ,
                                 quantity INT NOT NULL CHECK ( quantity > 0 ),
                                 customer_email VARCHAR(150) NOT NULL ,
                                 delivery_location TEXT NOT NULL ,
                                 status VARCHAR(150) NOT NULL,
                                 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
)