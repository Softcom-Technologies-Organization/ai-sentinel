INSERT INTO confluence_spaces (
    id,
    key,
    name,
    url,
    description,
    cache_timestamp,
    last_updated
) VALUES (
             'test-space-' || EXTRACT(EPOCH FROM NOW())::TEXT,  -- ID unique basé sur timestamp
             'TEST_' || TO_CHAR(NOW(), 'YYYYMMDDHH24MISS'),    -- Clé unique avec timestamp
             'Espace de Test - ' || TO_CHAR(NOW(), 'DD/MM/YYYY HH24:MI'),
             'https://confluence.example.com/display/TEST',
             'Espace créé automatiquement pour tester le polling silencieux et la notification UI',
             NOW(),
             NOW()
         );

SELECT
    id,
    key,
    name,
    cache_timestamp,
    last_updated
FROM confluence_spaces
WHERE key LIKE 'TEST_%'
ORDER BY last_updated DESC
LIMIT 5;
