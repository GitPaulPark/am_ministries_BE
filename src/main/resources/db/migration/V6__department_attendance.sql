SET NAMES utf8mb4;

-- =========================================================
-- department_attendance
-- One row per committee × event_date × event_label.
-- Lock window: 30 days after created_at. Admins can override.
-- =========================================================
CREATE TABLE department_attendance (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  committee_id    BIGINT NOT NULL,
  meeting_id      BIGINT NULL COMMENT 'optional link to a formal meeting',
  event_date      DATE NOT NULL,
  event_label     VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'e.g., "Wed practice"; "" for default',
  entry_mode      VARCHAR(20) NOT NULL DEFAULT 'AFTER' COMMENT 'AFTER, LIVE',
  notes           TEXT NULL,
  locked_at       DATETIME NULL,
  created_by      BIGINT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_da (committee_id, event_date, event_label),
  KEY idx_da_committee (committee_id),
  KEY idx_da_event_date (event_date),
  CONSTRAINT fk_da_committee FOREIGN KEY (committee_id) REFERENCES committees(id) ON DELETE CASCADE,
  CONSTRAINT fk_da_meeting   FOREIGN KEY (meeting_id)   REFERENCES meetings(id)   ON DELETE SET NULL,
  CONSTRAINT fk_da_user      FOREIGN KEY (created_by)   REFERENCES users(id)      ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- department_attendance_entries
-- One row per member per attendance event.
-- =========================================================
CREATE TABLE department_attendance_entries (
  attendance_id   BIGINT NOT NULL,
  member_id       BIGINT NOT NULL,
  present         BOOLEAN NOT NULL DEFAULT FALSE,
  note            VARCHAR(200) NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (attendance_id, member_id),
  KEY idx_dae_member (member_id),
  CONSTRAINT fk_dae_attendance FOREIGN KEY (attendance_id) REFERENCES department_attendance(id) ON DELETE CASCADE,
  CONSTRAINT fk_dae_member     FOREIGN KEY (member_id)     REFERENCES members(id)               ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- HQ_BOARD committee — a special committee whose members
-- bypass per-department scoping. Anyone with active membership
-- in this committee can view all departments' attendance.
-- =========================================================
INSERT INTO committees (code, name_kr, name_en, description, active)
VALUES ('HQ_BOARD', 'HQ 운영위원회', 'HQ Board',
        'Members of this committee see all departments. Used for cross-org governance.',
        TRUE);
