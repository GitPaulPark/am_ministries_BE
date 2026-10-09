SET NAMES utf8mb4;

-- =========================================================
-- bulletins.pdf_url
-- Optional public URL to a weekly-bulletin PDF uploaded by staff.
-- When set, the bulletin can be published without the structured
-- liturgy/scripture/presider fields (members read the PDF directly).
-- Served by WebConfig's /uploads/** static handler.
-- =========================================================
ALTER TABLE bulletins
  ADD COLUMN pdf_url VARCHAR(500) NULL AFTER memory_verse_text_en;
