SET NAMES utf8mb4;

-- =========================================================
-- sunday_attendance
-- One row per cell × service_date. Mirrors department_attendance
-- pattern but scoped by cell instead of committee.
-- Lock window: 30 days after creation. Admins can override.
-- =========================================================
CREATE TABLE sunday_attendance (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  cell_id         BIGINT NOT NULL,
  service_date    DATE NOT NULL,
  entry_mode      VARCHAR(20) NOT NULL DEFAULT 'AFTER' COMMENT 'AFTER (전원 출석 시작), LIVE (전원 결석 시작)',
  notes           TEXT NULL,
  locked_at       DATETIME NULL,
  created_by      BIGINT NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sa_cell_date (cell_id, service_date),
  KEY idx_sa_cell (cell_id),
  KEY idx_sa_date (service_date),
  CONSTRAINT fk_sa_cell FOREIGN KEY (cell_id) REFERENCES cells(id) ON DELETE CASCADE,
  CONSTRAINT fk_sa_user FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- sunday_attendance_entries
-- One row per member per Sunday check-in. The data captured here
-- powers future attendance streak / reward computations.
-- =========================================================
CREATE TABLE sunday_attendance_entries (
  sunday_attendance_id BIGINT NOT NULL,
  member_id            BIGINT NOT NULL,
  present              BOOLEAN NOT NULL DEFAULT FALSE,
  note                 VARCHAR(200) NULL,
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (sunday_attendance_id, member_id),
  KEY idx_sae_member (member_id),
  CONSTRAINT fk_sae_att    FOREIGN KEY (sunday_attendance_id) REFERENCES sunday_attendance(id) ON DELETE CASCADE,
  CONSTRAINT fk_sae_member FOREIGN KEY (member_id)            REFERENCES members(id)           ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
