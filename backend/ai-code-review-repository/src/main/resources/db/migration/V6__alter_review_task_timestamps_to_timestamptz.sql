-- Migration V6: Alter review_task timestamp columns to use TIMESTAMPTZ
-- This fixes timezone data loss issue from V5 migration
-- TIMESTAMP does not store timezone information, causing bugs in multi-timezone deployments
-- TIMESTAMPTZ stores timezone information and converts all times to UTC internally

-- Alter timestamp columns to timestamptz
ALTER TABLE review_task
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN started_at TYPE TIMESTAMPTZ USING started_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMPTZ USING completed_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- Note: USING clause converts existing TIMESTAMP data to TIMESTAMPTZ
-- AT TIME ZONE 'UTC' tells PostgreSQL to interpret existing timestamps as UTC
-- This is safe because Java Instant.now() always stores times in UTC
