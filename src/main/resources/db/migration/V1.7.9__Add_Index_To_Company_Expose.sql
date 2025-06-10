-- Add index on expose column for better query performance
CREATE INDEX idx_company_expose ON company(expose); 