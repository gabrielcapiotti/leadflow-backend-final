
DO $$
BEGIN
	IF NOT EXISTS (
		SELECT 1 FROM information_schema.columns 
		WHERE table_name='vendors' AND column_name='schema_name'
	) THEN
		ALTER TABLE vendors ADD COLUMN schema_name VARCHAR(63);
	END IF;
END$$;

DO $$
BEGIN
	IF NOT EXISTS (
		SELECT 1 FROM pg_indexes 
		WHERE tablename = 'vendors' AND indexname = 'uq_vendors_schema_name'
	) THEN
		CREATE UNIQUE INDEX uq_vendors_schema_name ON vendors(schema_name);
	END IF;
END$$;

-- Uncomment the following line to make the column mandatory
-- ALTER TABLE vendors
-- ALTER COLUMN schema_name SET NOT NULL;