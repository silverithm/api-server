-- V1.6.1: Add last_login_at column to members table

-- Add last_login_at column to track member login times
ALTER TABLE members ADD COLUMN last_login_at TIMESTAMP; 