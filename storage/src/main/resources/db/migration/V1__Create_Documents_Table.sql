CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

SET timezone = 'UTC';

CREATE TABLE documents (
  uuid UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  name VARCHAR NOT NULL,
  description VARCHAR,
  bytes BYTEA,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_name ON documents (name);

-- Trigger to update `updated_at` timestamp on row update
CREATE OR REPLACE FUNCTION update_updated_at()
  RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at := NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to update `updated_at` timestamp on row update
CREATE TRIGGER set_updated_at
  BEFORE UPDATE ON documents
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at();
