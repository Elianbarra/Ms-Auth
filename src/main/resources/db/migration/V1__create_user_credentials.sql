CREATE TABLE user_credentials (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email      VARCHAR(255) UNIQUE NOT NULL,
    password   VARCHAR(255) NOT NULL,
    role       VARCHAR(50)  NOT NULL,
    user_id    UUID         NOT NULL,
    is_active  BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP
);
