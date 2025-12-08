-- Create databases if they don't exist
SELECT 'CREATE DATABASE msstoreconsumer'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'msstoreconsumer')\gexec

SELECT 'CREATE DATABASE msstoreproducer'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'msstoreproducer')\gexec