ALTER TABLE subscribers ADD COLUMN phone_number TEXT;
ALTER TABLE subscribers ADD CONSTRAINT uq_subscribers_phone_number UNIQUE (phone_number);
