-- V10: Add threshold_result JSONB column to review_result table
-- Story 6.2: Threshold Validation Engine
-- Stores the threshold validation result (passed/violations/action) for each review.
-- Nullable because historical review results don't have threshold validation.

ALTER TABLE review_result
ADD COLUMN threshold_result JSONB;
