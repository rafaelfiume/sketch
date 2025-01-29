CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

SET timezone = 'UTC';

CREATE OR REPLACE FUNCTION update_updated_at()
  RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at := NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE SCHEMA system;

CREATE TABLE system.dynamic_configs (
  namespace TEXT NOT NULL,                                                      -- logically groups configs for different services or modules
  key TEXT NOT NULL CHECk (length(key) <= 255 AND key ~ '^[a-zA-Z0-9_.]+$'),
  value JSONB NOT NULL,                                                         -- Stores scalar (e.g. strings, booleans) or structured data (e.g. arrays), offering flexibility for diverse config values
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (namespace, key)
);

INSERT INTO system.dynamic_configs (namespace, key, value)
VALUES(
  'sketch', 'account.deletion.notification.recipients', '["sketch"]'::jsonb -- TODO access-control
);

CREATE SCHEMA auth;

CREATE TABLE auth.users (
  uuid UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  username VARCHAR(60) NOT NULL UNIQUE,
  -- supports 32 bytes Base64 encoded (padding '=' plus each char representing 6 bits) password = 1 + (32 * 8) / 6 = 44
  -- note that the actual password size depends on the algorithm used to generate it
  -- actual size of bcrypt hash is 60 chars length
  password_hash VARCHAR(60) NOT NULL,
  -- supports 32 bytes Base64 encoded (padding '=' plus each char representing 6 bits) salt = 1 + (32 * 8) / 6 = 44
  -- note that the actual salt size depends on the algorithm used to generate it
  -- jbcrypt uses 16 bytes salt, and its actual size is 29 chars length
  -- UNIQUE salts helps to prevent precomputed hash attacks
  salt VARCHAR(50) NOT NULL UNIQUE,
  state VARCHAR(20) NOT NULL CHECK (state IN ('Active', 'PendingDeletion')) DEFAULT 'Active',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  activated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  soft_deleted_at TIMESTAMPTZ
);
CREATE INDEX idx_users_username ON auth.users (username);

-- Trigger to update `updated_at` timestamp on row update
CREATE TRIGGER set_users_updated_at
  BEFORE UPDATE ON auth.users
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at();

CREATE TABLE auth.account_deletion_scheduled_events (
    uuid UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    permanent_deletion_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_account_deletion_scheduled_events_user_id ON auth.account_deletion_scheduled_events (user_id);

CREATE TABLE auth.account_deleted_notifications (
    uuid UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
    user_id UUID NOT NULL,                            -- The ID of the deleted account
    recipient VARCHAR(50) NOT NULL,                   -- The consumer or group of consumers responsible for processing the event
    created_at TIMESTAMP DEFAULT NOW()                -- When the event was created
);

-- Stores global roles
CREATE TABLE auth.global_access_control (
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('Admin', 'Superuser')),
    PRIMARY KEY (user_id)
    --FOREIGN KEY (user_id) REFERENCES users(user_id) ?
);

-- Stores contextual roles
CREATE TABLE auth.access_control (
    user_id UUID NOT NULL,
    entity_id UUID NOT NULL,
    entity_type VARCHAR(30) NOT NULL CHECK (entity_type IN (
      'DocumentEntity', 'UserEntity'
    )),
    role VARCHAR(20) NOT NULL CHECK (role IN ('Owner')),
    PRIMARY KEY (user_id, entity_id)
    --FOREIGN KEY (user_id) REFERENCES users(user_id) ?
);

-- TODO Create index to improve performance of selectAllAuthorisedEntityIds and measure results
-- CREATE INDEX idx_access_control_user_id_entity_type ON auth.access_control (user_id, entity_type);
