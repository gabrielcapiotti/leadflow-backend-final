
DO $$
BEGIN
	IF NOT EXISTS (
		SELECT 1 FROM information_schema.columns 
		WHERE table_name='vendors' AND column_name='slug'
	) THEN
		ALTER TABLE vendors ADD COLUMN slug VARCHAR(255);
	END IF;
END$$;

DO $$
BEGIN
	IF NOT EXISTS (
		SELECT 1 FROM pg_indexes 
		WHERE tablename = 'vendors' AND indexname = 'idx_vendors_slug'
	) THEN
		CREATE UNIQUE INDEX idx_vendors_slug ON vendors(slug);
	END IF;
END$$;