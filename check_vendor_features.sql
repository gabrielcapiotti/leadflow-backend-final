-- Verificar colunas da tabela vendor_features
SELECT column_name, data_type, is_nullable
FROM information_schema.columns 
WHERE table_name = 'vendor_features' 
ORDER BY ordinal_position;