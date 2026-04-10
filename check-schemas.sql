-- Verificar schemas com tabela vendors
SELECT DISTINCT table_schema, table_name
FROM information_schema.tables 
WHERE table_name = 'vendors'
ORDER BY table_schema;

-- Verificar colunas em cada schema
SELECT table_schema, column_name
FROM information_schema.columns
WHERE table_name = 'vendors'
ORDER BY table_schema, column_name;

-- Verificar se email_invalid existe em cada schema
SELECT table_schema, 
       COUNT(*) as total_columns,
       SUM(CASE WHEN column_name = 'email_invalid' THEN 1 ELSE 0 END) as has_email_invalid
FROM information_schema.columns
WHERE table_name = 'vendors'
GROUP BY table_schema
ORDER BY table_schema;
