SET NAMES utf8mb4;

-- =========================================================
-- sermon_paragraphs — structured bilingual transcript
--
-- Replaces the opaque sermons.transcript_kr / transcript_en LONGTEXT columns
-- with a per-paragraph row that the new bilingual reader (a redesigned mobile-
-- friendly /sermons/:id page targeting elderly congregants) renders. Each row
-- belongs to a sermon, occupies a position in the document, optionally
-- attaches to a numbered section and a translation pair, and has a `kind`:
--
--   paragraph        — body of the sermon
--   scripture        — Bible verse block (carries scripture_ref)
--   section_heading  — numbered section title
--   preacher_meta    — title + preacher + date line at the top
--
-- pair_key: when an EN paragraph and its KR translation are immediately
-- adjacent in the source PDF, the parser stamps both rows with the same UUID.
-- The reader can collapse them into a single block in "show both" mode.
-- =========================================================
CREATE TABLE sermon_paragraphs (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  sermon_id         BIGINT NOT NULL,
  order_idx         INT NOT NULL COMMENT '0-based position in document',
  section_idx       INT NULL COMMENT 'numbered section (0 = pre-section preamble)',
  section_title_kr  VARCHAR(500) NULL,
  section_title_en  VARCHAR(500) NULL,
  kind              VARCHAR(20) NOT NULL
                    COMMENT 'paragraph, scripture, section_heading, preacher_meta',
  language          VARCHAR(2) NOT NULL COMMENT 'kr, en',
  text              LONGTEXT NOT NULL,
  scripture_ref     VARCHAR(120) NULL COMMENT 'e.g. John 14:15 / 요한복음 14:15',
  pair_key          VARCHAR(40) NULL COMMENT 'shared between an EN/KR translation pair',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_sp_sermon_order (sermon_id, order_idx),
  KEY idx_sp_pair (sermon_id, pair_key),
  CONSTRAINT fk_sp_sermon FOREIGN KEY (sermon_id) REFERENCES sermons(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- A flag on the sermon row tells the API whether the parsed transcript is
-- single-language (only KR or only EN) so the reader can hide the language
-- toggle entirely. transcript_pdf_url is the original PDF served behind auth
-- — the reader's "원본 PDF 보기" fallback always points here.
ALTER TABLE sermons
  ADD COLUMN transcript_pdf_url     VARCHAR(500) NULL                                              AFTER audio_url,
  ADD COLUMN transcript_single_lang BOOLEAN      NULL COMMENT 'NULL = no PDF parsed yet'           AFTER transcript_pdf_url;
