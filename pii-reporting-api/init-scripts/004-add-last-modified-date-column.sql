-- Migration script to add last_modified_date column to existing confluence_spaces table
-- This script is idempotent and can be run multiple times safely

-- Add the column if it doesn't exist
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'confluence_spaces' 
        AND column_name = 'last_modified_date'
    ) THEN
        ALTER TABLE confluence_spaces 
        ADD COLUMN last_modified_date TIMESTAMP;
        
        RAISE NOTICE 'Column last_modified_date added to confluence_spaces table';
    ELSE
        RAISE NOTICE 'Column last_modified_date already exists in confluence_spaces table';
    END IF;
END $$;
