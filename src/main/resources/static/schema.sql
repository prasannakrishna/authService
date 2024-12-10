CREATE TABLE address (
    address_id BIGINT AUTO_INCREMENT PRIMARY KEY,  -- Primary key for the address table
    user_id BIGINT,                                -- Optional user_id foreign key reference
    address_line1 VARCHAR(255) NOT NULL,           -- Required field for primary address line
    address_line2 VARCHAR(255),                    -- Optional second address line
    city VARCHAR(100) NOT NULL,                    -- Required field for city
    state VARCHAR(100) NOT NULL,                   -- Required field for state/province
    country VARCHAR(100) NOT NULL,                 -- Required field for country
    postal_code VARCHAR(20) NOT NULL,              -- Required field for zip or postal code
    latitude DECIMAL(10, 7),                       -- Optional latitude for geo-location
    longitude DECIMAL(10, 7),                      -- Optional longitude for geo-location
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,-- Timestamp for record creation
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Timestamp for record update
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

INSERT INTO address (
    user_id,
    address_line1,
    address_line2,
    city,
    state,
    country,
    postal_code,
    latitude,
    longitude
)
VALUES (
    NULL,                                    -- user_id (can be set to NULL if not applicable)
    '123 Main St',                         -- address_line1
    'Apt 4B',                              -- address_line2 (optional)
    'New York',                            -- city
    'New York',                            -- state
    'USA',                                 -- country
    '10001',                               -- postal_code
    40.712776,                             -- latitude (optional)
    -74.005974                             -- longitude (optional)
);



CREATE TABLE user (
    user_id BIGINT AUTO_INCREMENT PRIMARY KEY,          -- Primary Key (User Id)
    user_name VARCHAR(100) NOT NULL,                    -- Username (e.g., login username)
    email VARCHAR(255) NOT NULL UNIQUE,                  -- User's email address
    phone_number VARCHAR(15),                            -- User's phone number
    address_id BIGINT,                                   -- Foreign key referencing the address table
    user_community_association VARCHAR(255),            -- User Community Association
    id BIGINT,                                          -- Some Id (possibly a secondary identifier)
    password VARCHAR(64) NOT NULL,                      -- Encrypted Password (SHA-256, stored as 64-character hex)
    community_id BIGINT,                                -- Community ID associated with the user
    is_admin_flag BOOLEAN DEFAULT FALSE,                -- Boolean flag to indicate if user is an admin
    role VARCHAR(64) NOT NULL,                          -- User role
    is_org_user BOOLEAN DEFAULT FALSE,                  -- Flag to indicate if the user is an organization user
    org_id BIGINT,                                      -- Organization ID (if the user is linked to an organization)
    is_app_user BOOLEAN DEFAULT FALSE,                  -- Flag to indicate if the user is an application user

    -- Constraints
    CONSTRAINT uk_username UNIQUE (user_name),          -- Ensure that username is unique
    CONSTRAINT uk_email UNIQUE (email),                  -- Ensure that email is unique

    -- Foreign key constraint linking to the business_org table
    CONSTRAINT fk_org_id FOREIGN KEY (org_id) REFERENCES business_org(id),

    -- Foreign key constraint linking to the address table
    CONSTRAINT fk_address_id FOREIGN KEY (address_id) REFERENCES address(address_id)
);


CREATE TABLE business_org (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,                                   -- Unique identifier for each organization
    party_type ENUM('Consumer', 'Retailer', 'Distribution centre',           -- Type of business entity
                    'LSP', 'Carrier', 'Shipper', 'Consignee',
                    'Supplier', 'Seller', 'Manufacturer',
                    'Warehouse', 'Store', 'Finance') NOT NULL,
    org_name VARCHAR(255) NOT NULL,                                          -- Organization name
    description TEXT,                                                        -- Organization description
    address_id BIGINT,                                                       -- Reference to address table
    GSTIN VARCHAR(15),                                                       -- GST Identification Number
    TAN VARCHAR(10),                                                         -- Tax Deduction and Collection Account Number
    CEO VARCHAR(255),                                                        -- Name of the CEO
    is_registered BOOLEAN DEFAULT FALSE,                                     -- Registration status flag
    is_verified BOOLEAN DEFAULT FALSE,                                       -- Verification status flag
    total_employees INT,                                                     -- Number of employees in the organization
    catalog_id BIGINT,                                                       -- Reference to catalog if applicable
    is_public BOOLEAN DEFAULT FALSE,                                         -- Indicates if the organization is public
    IS_PAN_INDIA BOOLEAN DEFAULT FALSE,                                      -- Indicates if the organization operates PAN India
    operating_city_PIN VARCHAR(10),                                          -- City PIN code of main operating city
    established_date DATE,                                                   -- Date when the organization was established
    is_ISO_certified BOOLEAN DEFAULT FALSE,                                  -- Indicates if the organization is ISO certified
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,                          -- Timestamp for record creation
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Timestamp for record update

    -- Foreign Key Constraints
    CONSTRAINT fk_address FOREIGN KEY (address_id) REFERENCES address(address_id) ON DELETE SET NULL  -- Foreign key to the address table
);

INSERT INTO business_org (
    party_type,
    org_name,
    description,
    address_id,
    GSTIN,
    TAN,
    CEO,
    is_registered,
    is_verified,
    total_employees,
    catalog_id,
    is_public,
    IS_PAN_INDIA,
    operating_city_PIN,
    established_date,
    is_ISO_certified
)
VALUES (
    'Retailer',                 -- party_type
    'ABC Retail Pvt. Ltd.',      -- org_name
    'Leading retail chain in India', -- description
    NULL,                       -- address_id (no address associated yet)
    '22AAAAA0000A1Z5',          -- GSTIN
    'ABCDE1234F',               -- TAN
    'John Doe',                 -- CEO
    TRUE,                       -- is_registered
    FALSE,                      -- is_verified
    500,                        -- total_employees
    NULL,                       -- catalog_id
    TRUE,                       -- is_public
    TRUE,                       -- IS_PAN_INDIA
    '110001',                   -- operating_city_PIN (New Delhi PIN code)
    '2005-08-15',               -- established_date
    TRUE                        -- is_ISO_certified
);


ALTER TABLE user
    -- Add unique constraint for `user_name`
    ADD CONSTRAINT uk_username UNIQUE (user_name),

    -- Add unique constraint for `email`
    ADD CONSTRAINT uk_email UNIQUE (email),

    -- Add foreign key constraint for `org_id` referencing the `business_org` table
    ADD CONSTRAINT fk_org_id FOREIGN KEY (org_id) REFERENCES business_org(id),

    -- Add foreign key constraint for `address_id` referencing the `address` table
    ADD CONSTRAINT fk_address_id FOREIGN KEY (address_id) REFERENCES address(address_id);



CREATE TABLE divisions (
    div_id BIGINT AUTO_INCREMENT PRIMARY KEY,              -- Unique identifier for each division
    org_id BIGINT NOT NULL,                                -- Foreign key reference to the business_org table
    is_registered BOOLEAN NOT NULL DEFAULT FALSE,          -- Registration status
    address_id BIGINT,                                     -- Foreign key reference to the address table
    type ENUM('store', 'hub', 'warehouse', 'retail_outlet') NOT NULL,  -- Type of division
    is_merchandise BOOLEAN DEFAULT FALSE,                  -- Flag to indicate if it's merchandise
    merchandise_id BIGINT,                                 -- Reference to business_org if merchandise
    is_franchised BOOLEAN DEFAULT FALSE,                   -- Flag to indicate if it's franchised
    franchise_id BIGINT,                                   -- Reference to business_org if franchised
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,        -- Timestamp for record creation
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Timestamp for record update

    -- Foreign key constraints
    CONSTRAINT fk_div_org FOREIGN KEY (org_id) REFERENCES business_org(id) ON DELETE CASCADE,
    CONSTRAINT fk_div__merchandise FOREIGN KEY (merchandise_id) REFERENCES business_org(id) ON DELETE SET NULL,
    CONSTRAINT fk_div_franchise FOREIGN KEY (franchise_id) REFERENCES business_org(id) ON DELETE SET NULL,
    CONSTRAINT fk__div_address FOREIGN KEY (address_id) REFERENCES address(address_id) ON DELETE SET NULL
);

CREATE TABLE app_services (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,              -- Unique identifier for the app service
    app_name VARCHAR(255) NOT NULL,                    -- Name of the application/service
    app_domain VARCHAR(255) NOT NULL,                  -- Domain of the application/service
    app_description TEXT,                              -- Description of the application/service
    app_status ENUM('Active', 'Inactive') NOT NULL,    -- Status of the application (Active/Inactive)
    app_id BIGINT,                                     -- Reference to `app_id` from the `app_service` table

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,    -- Timestamp for record creation
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP -- Timestamp for record update

);

INSERT INTO app_services (
    app_name,
    app_domain,
    app_description,
    app_status,
    app_id
)
VALUES (
    'User Management Service',                          -- app_name
    'usermanagement.example.com',                       -- app_domain
    'Service to handle user registration and profiles', -- app_description (optional)
    'Active',                                           -- app_status ('Active' or 'Inactive')
    NULL                                                -- app_id (can be NULL if no reference)
);



CREATE TABLE app_subscription (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,                 -- Unique identifier for the subscription
    app_id BIGINT NOT NULL,                               -- Foreign key referencing the `id` of `app_services` table
    user_id BIGINT NOT NULL,                              -- User ID associated with the subscription
    user_group_id BIGINT,                                 -- Group ID if applicable
    subscription_key VARCHAR(255),                        -- Subscription key or token for identification
    sync_user_job_trigger BOOLEAN DEFAULT FALSE,          -- Boolean flag for syncing jobs

    -- Sync frequency based on `sync_user_job_trigger` flag
    sync_frequency ENUM('monthly', 'quarterly', 'annually'),

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,        -- Timestamp for record creation
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Timestamp for record update

    -- Establishing foreign key relationship with `app_services` table
    FOREIGN KEY (app_id) REFERENCES app_services(id)
);

INSERT INTO app_subscription (
    app_id,
    user_id,
    user_group_id,
    subscription_key,
    sync_user_job_trigger,
    sync_frequency
)
VALUES (
    1,                                     -- app_id: ID of the related app in `app_services` table
    101,                                    -- user_id: User ID associated with this subscription
    NULL,                                   -- user_group_id: Can be NULL if not linked to a group
    'SUBSCRIPTION-2024-001',                -- subscription_key: Unique key for the subscription
    TRUE,                                   -- sync_user_job_trigger: Boolean flag for job sync (TRUE or FALSE)
    'monthly'                               -- sync_frequency: Can be 'monthly', 'quarterly', or 'annually'
);


