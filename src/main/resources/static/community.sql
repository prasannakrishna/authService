CREATE TABLE community (
    community_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_type ENUM('Consumer', 'Producer', 'Service provider'),
    community_name VARCHAR(255) NOT NULL,
    community_uid VARCHAR(100) UNIQUE NOT NULL,
    community_origin VARCHAR(255) NOT NULL,
    created_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_date TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    number_of_customers INT DEFAULT 0,
    matching_sellers_count INT DEFAULT 0,
    total_order_value DECIMAL(15, 2) DEFAULT 0.0,
    order_value_day DECIMAL(15, 2) DEFAULT 0.0,
    order_value_month DECIMAL(15, 2) DEFAULT 0.0,
    order_value_year DECIMAL(15, 2) DEFAULT 0.0,
    customer_location_histogram JSON,
    seller_location_histogram JSON,
    average_time_for_delivery_in_hours DECIMAL(10, 2) DEFAULT 0.0,
    overall_buyer_experience_rating DECIMAL(3, 2) DEFAULT 0.0,
    hash_key_count TINYINT DEFAULT 0
);


CREATE TABLE community_sku_mapping (
    community_id BIGINT NOT NULL,                     -- Foreign Key referencing community table
    sku_id BIGINT NOT NULL,                           -- Foreign Key referencing sku table
    PRIMARY KEY (community_id, sku_id),               -- Composite Primary Key to ensure unique combinations
    FOREIGN KEY (community_id) REFERENCES community(community_id) ON DELETE CASCADE,  -- Ensure referential integrity
    FOREIGN KEY (sku_id) REFERENCES sku(sku_id) ON DELETE CASCADE                     -- Ensure referential integrity
);



comment below
------
CREATE TABLE community_hash_key_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    community_id BIGINT NOT NULL,                      -- Foreign Key referencing community table
    hash_key_id BIGINT NOT NULL,                       -- Foreign Key referencing hash_key table
    hash_order TINYINT NOT NULL,                       -- Position of the hash key (1 to 7)
    PRIMARY KEY (community_id, hash_key_id),           -- Composite Primary Key to avoid duplicates
    UNIQUE (community_id, hash_order),                 -- Ensure only 7 hash keys per community
    FOREIGN KEY (community_id) REFERENCES community(community_id) ON DELETE CASCADE,
    FOREIGN KEY (hash_key_id) REFERENCES hash_key(hash_key_id) ON DELETE CASCADE,
    CHECK (hash_order BETWEEN 1 AND 7)                 -- Enforce max 7 hash keys per community
);

-- Check if the community already has 7 hash keys
SELECT COUNT(*) INTO @key_count
FROM community_hash_key_mapping
WHERE community_id = <COMMUNITY_ID>;

-- If count is less than 7, proceed with insert
IF @key_count < 7 THEN
    INSERT INTO community_hash_key_mapping (community_id, hash_key_id, hash_order)
    VALUES (<COMMUNITY_ID>, <HASH_KEY_ID>, @key_count + 1);
ELSE
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Maximum 7 hash keys allowed per community';
END IF;


DELIMITER $$

CREATE TRIGGER update_hash_key_count AFTER INSERT ON community_hash_key_mapping
FOR EACH ROW
BEGIN
    UPDATE community
    SET hash_key_count = hash_key_count + 1
    WHERE community_id = NEW.community_id;
END$$

DELIMITER ;
----------------
comment above


CREATE TABLE community_search_key_mapping (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,              -- Unique Identifier for each mapping
    root_node_id BIGINT,                               -- ID of the root node in the trie
    last_node_id BIGINT,                               -- ID of the last node in the trie
    previous_node_id BIGINT,                           -- ID of the previous node in the trie
    community_id BIGINT NOT NULL,                      -- Foreign Key referencing the community table
    hash_key_id BIGINT NOT NULL,                       -- Foreign Key referencing the hash_key table
    depth INT NOT NULL,                                -- Depth of the node in the trie
    is_end_node BOOLEAN DEFAULT FALSE,                 -- Indicates if this is the end of a key
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,    -- Timestamp for creation
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, -- Timestamp for last update
    FOREIGN KEY (community_id) REFERENCES community(community_id) ON DELETE CASCADE,
    FOREIGN KEY (hash_key_id) REFERENCES hash_key(hash_key_id) ON DELETE CASCADE
);
