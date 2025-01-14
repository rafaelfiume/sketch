CREATE schema domain;

CREATE TABLE domain.documents (
  uuid UUID DEFAULT uuid_generate_v4() PRIMARY KEY,
  name VARCHAR NOT NULL,
  description VARCHAR,
  bytes BYTEA,
  user_id UUID NOT NULL,                              -- the documents's owner
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_documents_name ON domain.documents (name);

-- Trigger to update `updated_at` timestamp on row update
CREATE TRIGGER set_documents_updated_at
  BEFORE UPDATE ON domain.documents
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at();
