-- V1.6.0: Add company relationship to members and member_join_requests

-- Add company_id column to members table
ALTER TABLE members ADD COLUMN company_id BIGINT;

-- Add foreign key constraint
ALTER TABLE members ADD CONSTRAINT fk_members_company 
    FOREIGN KEY (company_id) REFERENCES company(id);

-- Add index for performance
CREATE INDEX idx_members_company_id ON members(company_id);

-- Add company_id column to member_join_requests table
ALTER TABLE member_join_requests ADD COLUMN company_id BIGINT;

-- Add foreign key constraint
ALTER TABLE member_join_requests ADD CONSTRAINT fk_member_join_requests_company 
    FOREIGN KEY (company_id) REFERENCES company(id);

-- Add index for performance
CREATE INDEX idx_member_join_requests_company_id ON member_join_requests(company_id); 