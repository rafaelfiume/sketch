CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
  id UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  username VARCHAR(50) NOT NULL UNIQUE,
  -- 32 bytes Base64 encoded (each char representing 6 bits) password = 32 * 8 / 6 = 43 + extra space = 50
  password_hash VARCHAR(50) NOT NULL,
  -- 32 bytes Base64 encoded (each char representing 6 bits) salt = 32 * 8 / 6 = 43 + extra space = 50
  salt VARCHAR(50) NOT NULL,
  first_name VARCHAR(50) NOT NULL,
  last_name VARCHAR(50) NOT NULL,
  email VARCHAR(50) NOT NULL UNIQUE,
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ
);

CREATE INDEX idx_users_username ON users (username);
CREATE INDEX idx_users_email ON users (email);

-- Trigger to update `updated_at` timestamp on row update
CREATE OR REPLACE FUNCTION update_updated_at()
  RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at := NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to set `created_at` timestamp on row creation
CREATE TRIGGER set_created_at
  BEFORE INSERT ON users
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at();

-- Trigger to update `updated_at` timestamp on row update
CREATE TRIGGER set_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at();
