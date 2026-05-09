SET NAMES utf8mb4;

-- =========================================================
-- committees (Board, Finance, Worship, Mission, etc.)
-- =========================================================
CREATE TABLE committees (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  code            VARCHAR(20) NOT NULL COMMENT 'BOARD, FIN, WOR, MIS, etc.',
  name_kr         VARCHAR(100) NOT NULL,
  name_en         VARCHAR(100) NULL,
  description     TEXT NULL,
  active          BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_committees_code (code),
  KEY idx_committees_active (active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- committee_memberships
-- =========================================================
CREATE TABLE committee_memberships (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  committee_id    BIGINT NOT NULL,
  member_id       BIGINT NOT NULL,
  role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER'
                  COMMENT 'CHAIR, SECRETARY, MEMBER',
  joined_at       DATE NULL,
  left_at         DATE NULL,
  active          BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cmm_committee_member (committee_id, member_id),
  KEY idx_cmm_committee (committee_id),
  KEY idx_cmm_member (member_id),
  CONSTRAINT fk_cmm_committee FOREIGN KEY (committee_id) REFERENCES committees(id) ON DELETE CASCADE,
  CONSTRAINT fk_cmm_member    FOREIGN KEY (member_id)    REFERENCES members(id)    ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- meetings (one per committee per date)
-- =========================================================
CREATE TABLE meetings (
  id                  BIGINT NOT NULL AUTO_INCREMENT,
  committee_id        BIGINT NOT NULL,
  meeting_date        DATE NOT NULL,
  presider_member_id  BIGINT NULL,
  title               VARCHAR(255) NULL COMMENT 'optional override / theme',
  agenda              LONGTEXT NULL COMMENT 'markdown',
  minutes             LONGTEXT NULL COMMENT 'markdown',
  status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
                      COMMENT 'DRAFT, PUBLISHED',
  published_at        DATETIME NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_meetings_committee_date (committee_id, meeting_date),
  KEY idx_meetings_committee (committee_id),
  KEY idx_meetings_status (status),
  KEY idx_meetings_date (meeting_date),
  CONSTRAINT fk_meetings_committee FOREIGN KEY (committee_id) REFERENCES committees(id),
  CONSTRAINT fk_meetings_presider  FOREIGN KEY (presider_member_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- meeting_attendees
-- =========================================================
CREATE TABLE meeting_attendees (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  meeting_id      BIGINT NOT NULL,
  member_id       BIGINT NOT NULL,
  attended        BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ma_meeting_member (meeting_id, member_id),
  KEY idx_ma_meeting (meeting_id),
  KEY idx_ma_member (member_id),
  CONSTRAINT fk_ma_meeting FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE,
  CONSTRAINT fk_ma_member  FOREIGN KEY (member_id)  REFERENCES members(id)  ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- action_items
-- =========================================================
CREATE TABLE action_items (
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  meeting_id              BIGINT NOT NULL,
  description             TEXT NOT NULL,
  assignee_member_id      BIGINT NULL,
  due_date                DATE NULL,
  status                  VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                          COMMENT 'OPEN, DONE, CANCELLED',
  completed_at            DATETIME NULL,
  completed_by_user_id    BIGINT NULL,
  notes                   TEXT NULL,
  order_idx               INT NOT NULL DEFAULT 0,
  created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_ai_meeting (meeting_id),
  KEY idx_ai_assignee (assignee_member_id),
  KEY idx_ai_status (status),
  KEY idx_ai_due (due_date),
  CONSTRAINT fk_ai_meeting  FOREIGN KEY (meeting_id) REFERENCES meetings(id) ON DELETE CASCADE,
  CONSTRAINT fk_ai_assignee FOREIGN KEY (assignee_member_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
