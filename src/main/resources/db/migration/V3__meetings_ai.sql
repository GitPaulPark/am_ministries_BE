SET NAMES utf8mb4;

-- =========================================================
-- meetings: add AI fields (audio, transcript, processing status)
-- =========================================================
ALTER TABLE meetings
  ADD COLUMN audio_url          VARCHAR(500) NULL  AFTER minutes,
  ADD COLUMN audio_size_bytes   BIGINT       NULL  AFTER audio_url,
  ADD COLUMN audio_duration_sec INT          NULL  AFTER audio_size_bytes,
  ADD COLUMN transcript         LONGTEXT     NULL  AFTER audio_duration_sec
                                COMMENT 'Whisper output, full text',
  ADD COLUMN ai_summary         LONGTEXT     NULL  AFTER transcript
                                COMMENT 'Claude rollup, formatted Korean summary',
  ADD COLUMN processing_status  VARCHAR(20)  NOT NULL DEFAULT 'NONE' AFTER ai_summary
                                COMMENT 'NONE, QUEUED, TRANSCRIBING, ANALYZING, COMPLETE, FAILED',
  ADD COLUMN processing_error   TEXT         NULL  AFTER processing_status,
  ADD COLUMN processed_at       DATETIME     NULL  AFTER processing_error,
  ADD KEY idx_meetings_processing_status (processing_status);

-- =========================================================
-- meeting_topics — first-class agenda/discussion items
--   Replaces free-text `agenda` as the source of truth when populated.
--   parent_topic_id self-references for cross-meeting recurring-topic
--   tracking (e.g. "이 안건은 5/3 회의에서도 논의됨").
-- =========================================================
CREATE TABLE meeting_topics (
  id                  BIGINT NOT NULL AUTO_INCREMENT,
  meeting_id          BIGINT NOT NULL,
  parent_topic_id     BIGINT NULL
                      COMMENT 'cross-meeting link: prior topic this is a continuation of',
  title               VARCHAR(500) NOT NULL,
  summary             LONGTEXT NULL
                      COMMENT 'AI-generated discussion summary (▪-bullet Korean)',
  decision            LONGTEXT NULL
                      COMMENT 'AI-extracted decision/outcome',
  status              VARCHAR(20) NOT NULL DEFAULT 'DISCUSSED'
                      COMMENT 'DISCUSSED, PENDING, RESOLVED, DEFERRED, FOLLOW_UP',
  comment             TEXT NULL
                      COMMENT 'pastor/leader free-form note',
  transcript_excerpt  TEXT NULL
                      COMMENT 'snippet of source transcript backing this topic',
  order_idx           INT NOT NULL DEFAULT 0,
  ai_generated        BOOLEAN NOT NULL DEFAULT FALSE,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_mt_meeting (meeting_id, order_idx),
  KEY idx_mt_parent (parent_topic_id),
  KEY idx_mt_status (status),
  CONSTRAINT fk_mt_meeting FOREIGN KEY (meeting_id)        REFERENCES meetings(id)        ON DELETE CASCADE,
  CONSTRAINT fk_mt_parent  FOREIGN KEY (parent_topic_id)   REFERENCES meeting_topics(id)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- action_items: tag AI-generated rows so the UI can flag them
-- =========================================================
ALTER TABLE action_items
  ADD COLUMN ai_generated BOOLEAN NOT NULL DEFAULT FALSE AFTER notes,
  ADD COLUMN topic_id     BIGINT  NULL AFTER ai_generated
             COMMENT 'optional link to the meeting_topic this came out of',
  ADD KEY idx_ai_topic (topic_id),
  ADD CONSTRAINT fk_ai_topic FOREIGN KEY (topic_id) REFERENCES meeting_topics(id) ON DELETE SET NULL;

-- =========================================================
-- meeting_processing_jobs — audit row per AI run
--   One row per upload; replaced (status updated) as the pipeline
--   advances. Keeps token/cost metrics for observability.
-- =========================================================
CREATE TABLE meeting_processing_jobs (
  id                          BIGINT NOT NULL AUTO_INCREMENT,
  meeting_id                  BIGINT NOT NULL,
  status                      VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
                              COMMENT 'QUEUED, TRANSCRIBING, ANALYZING, COMPLETE, FAILED',
  started_at                  DATETIME NULL,
  completed_at                DATETIME NULL,
  error_message               TEXT NULL,
  audio_seconds               INT NULL,
  transcription_provider      VARCHAR(40) NULL COMMENT 'groq, whisper-cpp, openai, ...',
  transcription_model         VARCHAR(80) NULL,
  transcription_seconds       INT NULL COMMENT 'wall-clock for transcription',
  analysis_provider           VARCHAR(40) NULL COMMENT 'anthropic',
  analysis_model              VARCHAR(80) NULL COMMENT 'claude-opus-4-7',
  analysis_input_tokens       INT NULL,
  analysis_output_tokens      INT NULL,
  analysis_cache_read_tokens  INT NULL,
  analysis_cache_write_tokens INT NULL,
  triggered_by_user_id        BIGINT NULL,
  created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_mpj_meeting (meeting_id, created_at),
  KEY idx_mpj_status (status),
  CONSTRAINT fk_mpj_meeting FOREIGN KEY (meeting_id)         REFERENCES meetings(id) ON DELETE CASCADE,
  CONSTRAINT fk_mpj_user    FOREIGN KEY (triggered_by_user_id) REFERENCES users(id)  ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
