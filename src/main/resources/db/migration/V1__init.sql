SET NAMES utf8mb4;

-- =========================================================
-- members
-- =========================================================
CREATE TABLE members (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  name_kr         VARCHAR(100) NOT NULL,
  name_en         VARCHAR(100) NULL,
  email           VARCHAR(255) NULL,
  phone           VARCHAR(30) NULL,
  role_label      VARCHAR(50) NULL COMMENT 'Dcn., Ksn., Pastor, JDSN, etc.',
  birthdate       DATE NULL,
  gender          CHAR(1) NULL COMMENT 'M / F',
  baptized        BOOLEAN NOT NULL DEFAULT FALSE,
  baptized_at     DATE NULL,
  joined_at       DATE NULL,
  status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                  COMMENT 'ACTIVE, INACTIVE, TRANSFERRED, DECEASED',
  preferred_locale VARCHAR(10) NULL COMMENT 'ko or en',
  notes           TEXT NULL COMMENT 'pastor-only notes',
  deleted_at      DATETIME NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_members_email (email),
  KEY idx_members_status (status),
  KEY idx_members_deleted (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- cells (fellowship cells + service teams + special-purpose groups)
-- =========================================================
CREATE TABLE cells (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  code            VARCHAR(20) NOT NULL COMMENT 'C, D, F, G, Nec, TeH, Sho, Bcst, etc.',
  name_kr         VARCHAR(100) NOT NULL,
  name_en         VARCHAR(100) NULL,
  type            VARCHAR(50) NOT NULL
                  COMMENT 'REGULAR, NEWCOMER, PRAISE_TEAM, CHOIR, MEDIA, INTERCESSOR, MISSION',
  leader_member_id BIGINT NULL,
  meeting_day     VARCHAR(20) NULL COMMENT 'only meaningful for REGULAR/NEWCOMER',
  meeting_time    VARCHAR(20) NULL,
  meeting_location VARCHAR(255) NULL,
  description     TEXT NULL,
  active          BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cells_code (code),
  KEY idx_cells_type (type),
  KEY idx_cells_active (active),
  KEY idx_cells_leader (leader_member_id),
  CONSTRAINT fk_cells_leader FOREIGN KEY (leader_member_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- cell_memberships (many-to-many, primary flag)
-- =========================================================
CREATE TABLE cell_memberships (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  member_id       BIGINT NOT NULL,
  cell_id         BIGINT NOT NULL,
  is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
  role            VARCHAR(50) NULL
                  COMMENT 'LEADER, MEMBER, VOCALIST, KEYS, GUITAR, BASS, DRUMS, SOUND, CAMERA, etc.',
  joined_at       DATE NULL,
  left_at         DATE NULL,
  active          BOOLEAN NOT NULL DEFAULT TRUE,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_cm_member_cell (member_id, cell_id),
  KEY idx_cm_member (member_id),
  KEY idx_cm_cell (cell_id),
  KEY idx_cm_primary (member_id, is_primary),
  CONSTRAINT fk_cm_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
  CONSTRAINT fk_cm_cell FOREIGN KEY (cell_id) REFERENCES cells(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- cell_transfer_suggestions (newcomer graduation workflow)
-- =========================================================
CREATE TABLE cell_transfer_suggestions (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  member_id       BIGINT NOT NULL,
  from_cell_id    BIGINT NOT NULL,
  suggested_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                  COMMENT 'PENDING, APPROVED, DISMISSED',
  to_cell_id      BIGINT NULL,
  approved_by     BIGINT NULL COMMENT 'user_id of approving pastor',
  approved_at     DATETIME NULL,
  notes           TEXT NULL,
  PRIMARY KEY (id),
  KEY idx_cts_status (status),
  KEY idx_cts_member (member_id),
  CONSTRAINT fk_cts_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE,
  CONSTRAINT fk_cts_from FOREIGN KEY (from_cell_id) REFERENCES cells(id) ON DELETE CASCADE,
  CONSTRAINT fk_cts_to FOREIGN KEY (to_cell_id) REFERENCES cells(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- users (auth accounts)
-- =========================================================
CREATE TABLE users (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  email           VARCHAR(255) NOT NULL,
  password_hash   VARCHAR(255) NOT NULL,
  member_id       BIGINT NULL,
  role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER'
                  COMMENT 'ADMIN, PASTOR, LEADER, MEMBER',
  enabled         BOOLEAN NOT NULL DEFAULT TRUE,
  last_login_at   DATETIME NULL,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_member (member_id),
  CONSTRAINT fk_users_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- refresh_tokens
-- =========================================================
CREATE TABLE refresh_tokens (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  user_id         BIGINT NOT NULL,
  token_hash      VARCHAR(255) NOT NULL,
  expires_at      DATETIME NOT NULL,
  revoked         BOOLEAN NOT NULL DEFAULT FALSE,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rt_token_hash (token_hash),
  KEY idx_rt_user (user_id),
  KEY idx_rt_expires (expires_at),
  CONSTRAINT fk_rt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- bulletins
-- =========================================================
CREATE TABLE bulletins (
  id                          BIGINT NOT NULL AUTO_INCREMENT,
  service_date                DATE NOT NULL,
  presider_member_id          BIGINT NULL,
  theme                       VARCHAR(255) NULL,
  song_of_week_title          VARCHAR(255) NULL,
  song_of_week_lyrics         TEXT NULL,
  memory_verse_ref            VARCHAR(50) NULL,
  memory_verse_text_kr        TEXT NULL,
  memory_verse_text_en        TEXT NULL,
  next_week_prayer_member_id  BIGINT NULL,
  published                   BOOLEAN NOT NULL DEFAULT FALSE,
  published_at                DATETIME NULL,
  created_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_bulletins_date (service_date),
  KEY idx_bulletins_published (published),
  CONSTRAINT fk_bulletins_presider FOREIGN KEY (presider_member_id) REFERENCES members(id) ON DELETE SET NULL,
  CONSTRAINT fk_bulletins_nw_prayer FOREIGN KEY (next_week_prayer_member_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- bulletin_liturgy_roles
-- =========================================================
CREATE TABLE bulletin_liturgy_roles (
  id                  BIGINT NOT NULL AUTO_INCREMENT,
  bulletin_id         BIGINT NOT NULL,
  role_type           VARCHAR(50) NOT NULL
                      COMMENT 'LORDS_PRAYER, PRAISE, PRAYER, SCRIPTURE_READING, WELCOME, CHORAL_PRAISE, SERMON, OFFERTORY, THANKSGIVING, CLOSING_SONG, BENEDICTION',
  title               VARCHAR(255) NULL COMMENT 'e.g. song title for choral praise',
  assignee_member_id  BIGINT NULL,
  assignee_cell_id    BIGINT NULL COMMENT 'when team is assigned (Tehillah, Shoshannah)',
  assignee_label      VARCHAR(255) NULL COMMENT 'free-text fallback',
  order_idx           INT NOT NULL,
  standing            BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (id),
  KEY idx_blr_bulletin (bulletin_id),
  CONSTRAINT fk_blr_bulletin FOREIGN KEY (bulletin_id) REFERENCES bulletins(id) ON DELETE CASCADE,
  CONSTRAINT fk_blr_member FOREIGN KEY (assignee_member_id) REFERENCES members(id) ON DELETE SET NULL,
  CONSTRAINT fk_blr_cell FOREIGN KEY (assignee_cell_id) REFERENCES cells(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- bulletin_announcements
-- =========================================================
CREATE TABLE bulletin_announcements (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  bulletin_id     BIGINT NOT NULL,
  content_kr      TEXT NULL,
  content_en      TEXT NULL,
  order_idx       INT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_ba_bulletin (bulletin_id),
  CONSTRAINT fk_ba_bulletin FOREIGN KEY (bulletin_id) REFERENCES bulletins(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- bulletin_prayer_items
-- =========================================================
CREATE TABLE bulletin_prayer_items (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  bulletin_id     BIGINT NOT NULL,
  category        VARCHAR(20) NOT NULL COMMENT 'HEALING, ABROAD, OTHER',
  content_kr      TEXT NULL,
  content_en      TEXT NULL,
  order_idx       INT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_bpi_bulletin (bulletin_id),
  CONSTRAINT fk_bpi_bulletin FOREIGN KEY (bulletin_id) REFERENCES bulletins(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- bulletin_cell_attendance (only fellowship cells: REGULAR, NEWCOMER)
-- =========================================================
CREATE TABLE bulletin_cell_attendance (
  id                  BIGINT NOT NULL AUTO_INCREMENT,
  bulletin_id         BIGINT NOT NULL,
  cell_id             BIGINT NOT NULL,
  attendance_count    INT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_bca (bulletin_id, cell_id),
  KEY idx_bca_cell (cell_id),
  CONSTRAINT fk_bca_bulletin FOREIGN KEY (bulletin_id) REFERENCES bulletins(id) ON DELETE CASCADE,
  CONSTRAINT fk_bca_cell FOREIGN KEY (cell_id) REFERENCES cells(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- sermons
-- =========================================================
CREATE TABLE sermons (
  id                      BIGINT NOT NULL AUTO_INCREMENT,
  sermon_date             DATE NOT NULL,
  title_kr                VARCHAR(255) NULL,
  title_en                VARCHAR(255) NULL,
  preacher_member_id      BIGINT NULL,
  preacher_name_label     VARCHAR(100) NULL COMMENT 'guest preachers without member record',
  scripture_ref           VARCHAR(100) NOT NULL,
  scripture_text_kr       TEXT NULL,
  scripture_text_en       TEXT NULL,
  audio_url               VARCHAR(500) NULL,
  video_url               VARCHAR(500) NULL,
  transcript_kr           LONGTEXT NULL,
  transcript_en           LONGTEXT NULL,
  theme                   VARCHAR(255) NULL,
  cell_reflection_questions JSON NULL,
  bulletin_id             BIGINT NULL,
  published               BOOLEAN NOT NULL DEFAULT FALSE,
  published_at            DATETIME NULL,
  created_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sermons_date (sermon_date),
  KEY idx_sermons_published (published),
  KEY idx_sermons_bulletin (bulletin_id),
  KEY idx_sermons_preacher (preacher_member_id),
  CONSTRAINT fk_sermons_preacher FOREIGN KEY (preacher_member_id) REFERENCES members(id) ON DELETE SET NULL,
  CONSTRAINT fk_sermons_bulletin FOREIGN KEY (bulletin_id) REFERENCES bulletins(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- service_attendance
-- =========================================================
CREATE TABLE service_attendance (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  service_date    DATE NOT NULL,
  service_type    VARCHAR(50) NOT NULL COMMENT 'EWS, KWS, etc.',
  age_group       VARCHAR(50) NOT NULL
                  COMMENT 'ADULT, JESUS_GEN, SHALOM, HOSANNA, PAIDION',
  count           INT NOT NULL DEFAULT 0,
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_sa (service_date, service_type, age_group),
  KEY idx_sa_date (service_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =========================================================
-- prayer_requests (member-submitted, separate from bulletin_prayer_items)
-- =========================================================
CREATE TABLE prayer_requests (
  id              BIGINT NOT NULL AUTO_INCREMENT,
  member_id       BIGINT NOT NULL,
  category        VARCHAR(50) NULL COMMENT 'HEALING, FAMILY, WORK, SPIRITUAL, OTHER',
  content         TEXT NOT NULL,
  is_private      BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'true = pastor-only',
  status          VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                  COMMENT 'OPEN, ANSWERED, ARCHIVED',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_pr_member (member_id),
  KEY idx_pr_status (status),
  CONSTRAINT fk_pr_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
