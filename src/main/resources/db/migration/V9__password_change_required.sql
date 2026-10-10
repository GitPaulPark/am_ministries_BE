SET NAMES utf8mb4;

-- =========================================================
-- users.password_change_required
-- When true, the frontend forces the user through /me/password on
-- their next login. Flipped to false by AuthService.changePassword.
-- Set to true for seeded / bootstrapped admin accounts so a known
-- default never survives a real deployment.
-- =========================================================
ALTER TABLE users
  ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE
  AFTER enabled;
