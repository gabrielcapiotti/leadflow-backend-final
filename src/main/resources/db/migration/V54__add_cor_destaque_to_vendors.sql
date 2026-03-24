DO $$
BEGIN
	IF NOT EXISTS (
		SELECT 1 FROM information_schema.columns 
		WHERE table_name='vendors' AND column_name='cor_destaque'
	) THEN
		ALTER TABLE public.vendors ADD COLUMN cor_destaque VARCHAR(20);
	END IF;
END$$;