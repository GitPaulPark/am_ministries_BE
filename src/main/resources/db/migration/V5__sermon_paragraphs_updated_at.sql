SET NAMES utf8mb4;

-- BaseEntity carries both created_at + updated_at; V4 only added created_at,
-- so Hibernate's schema validator fails on startup. Add the missing column.
ALTER TABLE sermon_paragraphs
  ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
             AFTER created_at;
