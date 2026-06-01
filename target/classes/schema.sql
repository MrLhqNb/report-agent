CREATE TABLE IF NOT EXISTS db_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    db_type VARCHAR(20) NOT NULL,
    host VARCHAR(255),
    port INT,
    database_name VARCHAR(255),
    username VARCHAR(100),
    password VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS table_relationship (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    db_config_id BIGINT NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    table_comment VARCHAR(500),
    related_table VARCHAR(255) NOT NULL,
    relation_type VARCHAR(20) NOT NULL,
    join_condition VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS table_column_override (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    db_config_id BIGINT NOT NULL,
    table_name VARCHAR(255) NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    column_comment VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_key_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    api_base VARCHAR(500),
    api_key VARCHAR(500),
    model VARCHAR(100),
    is_active BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
