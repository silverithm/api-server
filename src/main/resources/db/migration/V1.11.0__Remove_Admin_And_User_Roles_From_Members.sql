-- Remove ADMIN and USER roles from members table
-- First, update any existing ADMIN or USER roles to OFFICE
UPDATE members 
SET role = 'OFFICE' 
WHERE role IN ('ADMIN', 'USER');

-- Add a check constraint to ensure only CAREGIVER and OFFICE roles are allowed
-- Note: MySQL doesn't support CHECK constraints before 8.0.16
-- For older versions, this would need to be handled at the application level
-- ALTER TABLE members ADD CONSTRAINT chk_member_role CHECK (role IN ('CAREGIVER', 'OFFICE'));