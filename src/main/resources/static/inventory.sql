CREATE TABLE sku (
    sku_id BIGINT AUTO_INCREMENT PRIMARY KEY,         -- Primary Key (SKU Id)
    sku_name VARCHAR(255) NOT NULL,                   -- SKU Name
    description TEXT,                                  -- SKU Description
    product_group VARCHAR(100),                        -- Product Group
    each_volume DECIMAL(10, 2),                       -- Each Volume
    each_weight DECIMAL(10, 2),                       -- Each Weight
    each_height DECIMAL(10, 2),                       -- Each Height
    each_depth DECIMAL(10, 2),                        -- Each Depth
    tag_volume DECIMAL(10, 2),                        -- Tag Volume
    beam_rail_item_length DECIMAL(10, 2),            -- Beam/Rail Item - Each Length
    client VARCHAR(100),                              -- Client Name
    label VARCHAR(100),                               -- Label
    color ENUM('Red', 'Green', 'Blue', 'Yellow', 'Black', 'White', 'Orange', 'Purple', 'Pink', 'Gray'),  -- Color Enum
    is_plant_based BOOLEAN DEFAULT FALSE,             -- Is Plant Based
    is_medicinal BOOLEAN DEFAULT FALSE,               -- Is Medicinal
    is_hazardous BOOLEAN DEFAULT FALSE,               -- Is Hazardous
    is_flammable BOOLEAN DEFAULT FALSE,               -- Is Flammable
    split_lowest DECIMAL(10, 2),                     -- Split Lowest
    is_edible BOOLEAN DEFAULT FALSE,                  -- Is Edible
    is_batch_controlled BOOLEAN DEFAULT FALSE,        -- Is Batch Controlled
    is_org_sku BOOLEAN DEFAULT FALSE,                 -- Is SKU attached to an organization
    org_id BIGINT,                                    -- Organization ID from business_org table
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP, -- Created Date
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Updated Date
    created_by BIGINT,                                -- User ID of the creator (foreign key from user table)
    updated_by BIGINT,                                -- User ID of the last updater (foreign key from user table)
    user_defined_text_1 VARCHAR(255),                 -- User Defined Text 1
    user_defined_text_2 VARCHAR(255),                 -- User Defined Text 2
    user_defined_text_3 VARCHAR(255),                 -- User Defined Text 3
    user_defined_text_4 VARCHAR(255),                 -- User Defined Text 4
    user_defined_text_5 VARCHAR(255),                 -- User Defined Text 5
    user_defined_check_1 BOOLEAN DEFAULT FALSE,       -- User Defined Check 1
    user_defined_check_2 BOOLEAN DEFAULT FALSE,       -- User Defined Check 2
    user_defined_check_3 BOOLEAN DEFAULT FALSE,       -- User Defined Check 3
    user_defined_check_4 BOOLEAN DEFAULT FALSE,       -- User Defined Check 4
    user_defined_check_5 BOOLEAN DEFAULT FALSE,       -- User Defined Check 5
    user_defined_date_1 DATE,                          -- User Defined Date 1
    user_defined_date_2 DATE,                          -- User Defined Date 2
    user_defined_date_3 DATE,                          -- User Defined Date 3
    user_defined_date_4 DATE,                          -- User Defined Date 4
    user_defined_date_5 DATE,                          -- User Defined Date 5
    CONSTRAINT fk_sku_org FOREIGN KEY (org_id) REFERENCES business_org(id), -- Foreign key constraint to business_org
    CONSTRAINT fk_sku_created_by FOREIGN KEY (created_by) REFERENCES user(user_id), -- Foreign key constraint to user
    CONSTRAINT fk_sku_updated_by FOREIGN KEY (updated_by) REFERENCES user(user_id)  -- Foreign key constraint to user
);

CREATE TABLE packing_configuration (
    config_id BIGINT AUTO_INCREMENT PRIMARY KEY,         -- Primary Key (Configuration Id)
    tracking_level_1 ENUM('Each', 'Case', 'Box', 'Pallet', 'Layer', 'Tier', 'Container', 'Master', 'Bundle', 'Carton', 'Display'), -- Tracking Level 1
    tracking_level_2 ENUM('Each', 'Case', 'Box', 'Pallet', 'Layer', 'Tier', 'Container', 'Master', 'Bundle', 'Carton', 'Display'), -- Tracking Level 2
    tracking_ratio_1_to_2 INT,                           -- Tracking Ratio 1 to 2
    tracking_level_3 ENUM('Each', 'Case', 'Box', 'Pallet', 'Layer', 'Tier', 'Container', 'Master', 'Bundle', 'Carton', 'Display'), -- Tracking Level 3
    tracking_ratio_2_to_3 INT,                           -- Tracking Ratio 2 to 3
    tracking_level_4 ENUM('Each', 'Case', 'Box', 'Pallet', 'Layer', 'Tier', 'Container', 'Master', 'Bundle', 'Carton', 'Display'), -- Tracking Level 4
    tracking_ratio_3_to_4 INT,                           -- Tracking Ratio 3 to 4
    tracking_level_5 ENUM('Each', 'Case', 'Box', 'Pallet', 'Layer', 'Tier', 'Container', 'Master', 'Bundle', 'Carton', 'Display'), -- Tracking Level 5
    tracking_ratio_4_to_5 INT,                           -- Tracking Ratio 4 to 5
    lowest_unit_weight DECIMAL(10, 2),                   -- Lowest Unit Weight
    tag_volume_weight DECIMAL(10, 2),                    -- Tag Volume Weight
    tagged_at ENUM('Each', 'Case', 'Box', 'Pallet', 'Layer', 'Tier', 'Container', 'Master', 'Bundle', 'Carton', 'Display'), -- Tagged at each level
    handling_unit ENUM('Each', 'Case', 'Box', 'Pallet', 'Layer', 'Tier', 'Container', 'Master', 'Bundle', 'Carton', 'Display')        -- Handling Unit type
);


#########

1. Each
Definition: The individual unit of the product (e.g., a single bottle, a single piece of clothing).
Tracking Purpose: Useful for retail environments where products are sold individually.
2. Inner Case (or Inner Pack)
Definition: A box containing a specific number of individual units (e.g., a box of 12 bottles).
Tracking Purpose: Helps in managing inventory at a higher level than individual items, often used in wholesale or bulk sales.
3. Master Case (or Outer Pack)
Definition: A larger box that contains multiple inner cases (e.g., a master case of 5 inner cases, each containing 12 bottles).
Tracking Purpose: Facilitates bulk handling and distribution, typically used in warehouses and during shipping.
4. Pallet
Definition: A flat transport structure that supports goods in a stable fashion while being lifted by forklifts, pallet jacks, or other jacking devices (e.g., a pallet containing 48 master cases).
Tracking Purpose: Used for shipping large quantities and is essential in warehouse logistics for optimizing space and handling.
5. Container
Definition: A large shipping container used for transporting goods over long distances (e.g., a shipping container that holds multiple pallets).
Tracking Purpose: Essential for international shipping and tracking large shipments across various modes of transport.
6. Box
Definition: A standard shipping box that can hold various quantities of inner cases or products depending on the size and configuration.
Tracking Purpose: Useful for direct-to-consumer shipments or retail packaging.
7. Bundle
Definition: A collection of items grouped together for sale or shipping (e.g., a bundle of 5 shirts).
Tracking Purpose: Often used in promotions or sales, helping to streamline sales processes.
8. Roll
Definition: A cylindrical package, often used for materials like fabric or paper (e.g., a roll of fabric).
Tracking Purpose: Useful for industries that deal with flexible materials.
9. Crate
Definition: A large container typically used for transporting bulk items, often made of wood or plastic.
Tracking Purpose: Ideal for agricultural products or larger items that need protection during transport.
10. Skid
Definition: A platform for supporting goods that allows easy handling (similar to a pallet but typically has no bottom).
Tracking Purpose: Useful for items that are heavy or bulky and require stable transport.
11. Carton
Definition: A box typically used for shipping and packaging that contains various items.
Tracking Purpose: Commonly used for shipping multiple units of products.
###########


CREATE TABLE order (
    order_id BIGINT AUTO_INCREMENT PRIMARY KEY,                   -- Primary Key (Order ID)
    order_type ENUM('Customer Order', 'Demand Order', 'Replenishment Order', 'Supply Order', 'Bulk Order') NOT NULL,  -- Order Type
    order_status ENUM('Created', 'Released', 'Allocated', 'Picked', 'Packed', 'Shipped', 'In Transit', 'Delivered', 'Cancelled', 'Error') NOT NULL, -- Order Status
    is_live_tracking_enabled BOOLEAN DEFAULT FALSE,               -- Is Live Tracking Enabled
    client_id BIGINT NOT NULL,                                    -- Client ID (foreign key from business_org table)
    seller_id BIGINT NOT NULL,                                    -- Seller ID (foreign key from business_org table)
    shipping_address_id BIGINT NOT NULL,                          -- Shipping Address ID (foreign key from address table)
    from_site_id BIGINT NOT NULL,                                 -- Site ID (foreign key from warehouse table)
    to_site_id BIGINT NOT NULL,                                   -- To Site/Store/Customer ID (could reference another warehouse or address)
    order_value DECIMAL(10, 2) NOT NULL,                         -- Order Value (Amount)
    number_of_lines INT NOT NULL,                                 -- Number of Lines in the Order
    carrier_details VARCHAR(255),                                 -- Carrier Details
    is_last_mile_delivery_order BOOLEAN DEFAULT FALSE,           -- Is Last Mile Delivery Order
    payment_id BIGINT,                                 -- Payment Details
    billing_address_id BIGINT NOT NULL,                           -- Billing Address ID (foreign key from address table)
    is_partially_shippable BOOLEAN DEFAULT FALSE,                -- Is Partially Shippable
    is_allocatable BOOLEAN DEFAULT FALSE,                         -- Is Allocatable
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    shipby_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deliverby_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (client_id) REFERENCES business_org(id),         -- Foreign Key to business_org (Client)
    FOREIGN KEY (seller_id) REFERENCES business_org(id),         -- Foreign Key to business_org (Seller)
    FOREIGN KEY (shipping_address_id) REFERENCES address(address_id), -- Foreign Key to address table
    FOREIGN KEY (from_site_id) REFERENCES warehouse(site_id),    -- Foreign Key to warehouse table (From Site)
     FOREIGN KEY (payment_id) REFERENCES Payment(payment_id),
    FOREIGN KEY (to_site_id) REFERENCES warehouse(site_id),      -- Foreign Key to warehouse table (To Site)
    FOREIGN KEY (billing_address_id) REFERENCES address(address_id) -- Foreign Key to address table (Billing Address)
);

CREATE TABLE payment (
    payment_id BIGINT AUTO_INCREMENT PRIMARY KEY,                          -- Primary Key (Payment ID)
    order_id BIGINT NOT NULL,                                             -- Associated Order ID (foreign key)
    payment_mode ENUM('UPI', 'Cash', 'Direct Account Transfer') NOT NULL, -- Payment Mode
    amount DECIMAL(10, 2) NOT NULL,                                      -- Payment Amount
    transaction_id VARCHAR(255) NOT NULL,                                -- Unique Transaction ID
    payer VARCHAR(255) NOT NULL,                                         -- Identifier for the payer (account/UPI)
    payee VARCHAR(255) NOT NULL,                                         -- Identifier for the payee (account/UPI)
    from_date DATETIME NOT NULL,                                         -- Payment Initiation Date
    to_date DATETIME,                                                   -- Payment Completion Date (can be null if in progress)
    status ENUM('Initiated', 'In Progress', 'Cancelled', 'Aborted', 'Success') NOT NULL, -- Payment Status
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                   -- Created Date
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Updated Date
    payer_id BIGINT,                                                    -- User ID of the payer (foreign key from user table)
    payer_email VARCHAR(100),                                           -- Payer Email
    payer_phone VARCHAR(15),                                            -- Payer Phone Number
    FOREIGN KEY (order_id) REFERENCES orders(order_id),                 -- Foreign Key to orders table
    FOREIGN KEY (payer_id) REFERENCES user(user_id)                     -- Foreign Key to user table
);

