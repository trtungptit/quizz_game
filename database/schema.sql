-- QUIZ game schema — MySQL 8.0.16+ (CHECK constraints enforced).
-- Fresh-install schema, not an in-place migration. Never drop a real database to apply it.
-- Charset/collation chosen explicitly for Vietnamese text and case-insensitive usernames.
CREATE DATABASE IF NOT EXISTS quiz_game
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE quiz_game;

CREATE TABLE users (
  user_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(20) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  total_wins INT UNSIGNED NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id),
  CONSTRAINT uq_users_username UNIQUE (username),
  CONSTRAINT chk_users_username CHECK (username REGEXP '^[A-Za-z0-9_]{3,20}$')
) ENGINE=InnoDB;

CREATE TABLE question_sets (
  set_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  owner_id BIGINT UNSIGNED NOT NULL,
  title VARCHAR(255) NOT NULL,
  topic VARCHAR(100) NOT NULL DEFAULT '',
  description TEXT NOT NULL,
  -- Existing protocol publishes every saved set; drafts remain client-local.
  visibility ENUM('public') NOT NULL DEFAULT 'public',
  publish_status ENUM('published') NOT NULL DEFAULT 'published',
  deleted_at TIMESTAMP NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (set_id),
  KEY ix_question_sets_owner (owner_id),
  CONSTRAINT fk_question_sets_owner FOREIGN KEY (owner_id) REFERENCES users(user_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE questions (
  question_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  set_id BIGINT UNSIGNED NOT NULL,
  type ENUM('SINGLE','MULTI','TYPE') NOT NULL,
  prompt VARCHAR(500) NOT NULL,
  image_ref VARCHAR(500) NULL,
  time_limit_seconds SMALLINT UNSIGNED NOT NULL DEFAULT 20,
  position SMALLINT UNSIGNED NOT NULL,
  PRIMARY KEY (question_id),
  UNIQUE KEY uq_questions_set_position (set_id, position),
  CONSTRAINT chk_questions_time CHECK (time_limit_seconds BETWEEN 5 AND 120),
  CONSTRAINT fk_questions_set FOREIGN KEY (set_id) REFERENCES question_sets(set_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE question_options (
  option_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  question_id BIGINT UNSIGNED NOT NULL,
  option_index TINYINT UNSIGNED NOT NULL,
  option_text VARCHAR(200) NOT NULL,
  is_correct BOOLEAN NOT NULL DEFAULT FALSE,
  PRIMARY KEY (option_id),
  UNIQUE KEY uq_options_question_index (question_id, option_index),
  UNIQUE KEY uq_options_identity (option_id, question_id),
  CONSTRAINT chk_options_index CHECK (option_index BETWEEN 0 AND 3),
  CONSTRAINT fk_options_question FOREIGN KEY (question_id) REFERENCES questions(question_id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE accepted_answers (
  accepted_answer_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  question_id BIGINT UNSIGNED NOT NULL,
  answer_text VARCHAR(200) NOT NULL,
  -- Preserve accents: protocol ignores case/whitespace, not Vietnamese diacritics.
  normalized_text VARCHAR(200) COLLATE utf8mb4_0900_as_ci NOT NULL,
  PRIMARY KEY (accepted_answer_id),
  UNIQUE KEY uq_accepted_answers_normalized (question_id, normalized_text),
  CONSTRAINT fk_accepted_answers_question FOREIGN KEY (question_id) REFERENCES questions(question_id)
    ON UPDATE CASCADE ON DELETE CASCADE
) ENGINE=InnoDB;

-- Room membership and online status are runtime state owned by the server.
-- Persist room/match history as needed, but never trust this table to reconstruct online sessions.
CREATE TABLE rooms (
  room_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  room_code CHAR(6) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  name VARCHAR(30) NOT NULL,
  host_id BIGINT UNSIGNED NOT NULL,
  status ENUM('waiting','playing','finished','cancelled') NOT NULL DEFAULT 'waiting',
  set_id BIGINT UNSIGNED NULL,
  num_questions SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  time_override_seconds SMALLINT UNSIGNED NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (room_id),
  UNIQUE KEY uq_rooms_code (room_code),
  KEY ix_rooms_host (host_id),
  CONSTRAINT chk_rooms_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 30),
  CONSTRAINT chk_rooms_config CHECK ((set_id IS NULL AND num_questions = 0) OR (set_id IS NOT NULL AND num_questions >= 1)),
  CONSTRAINT chk_rooms_time CHECK (time_override_seconds IS NULL OR time_override_seconds BETWEEN 5 AND 60),
  CONSTRAINT chk_room_code CHECK (room_code REGEXP '^[A-HJ-NP-Z2-9]{6}$'),
  CONSTRAINT fk_rooms_host FOREIGN KEY (host_id) REFERENCES users(user_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_rooms_set FOREIGN KEY (set_id) REFERENCES question_sets(set_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE room_members (
  room_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  joined_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  ready BOOLEAN NOT NULL DEFAULT FALSE,
  left_at TIMESTAMP(3) NULL,
  -- Historical rows do not reserve the user; NULL values may repeat in a unique index.
  active_user_id BIGINT UNSIGNED GENERATED ALWAYS AS
    (CASE WHEN left_at IS NULL THEN user_id ELSE NULL END) STORED,
  PRIMARY KEY (room_id, user_id),
  UNIQUE KEY uq_room_members_active_user (active_user_id),
  KEY ix_room_members_user_active (user_id, left_at),
  CONSTRAINT fk_room_members_room FOREIGN KEY (room_id) REFERENCES rooms(room_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_room_members_user FOREIGN KEY (user_id) REFERENCES users(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE invitations (
  invitation_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  room_id BIGINT UNSIGNED NOT NULL,
  sender_id BIGINT UNSIGNED NOT NULL,
  receiver_id BIGINT UNSIGNED NOT NULL,
  status ENUM('pending','accepted','rejected','expired','joined_other','room_full','cancelled') NOT NULL DEFAULT 'pending',
  sent_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  -- Set delivered_at/expires_at only when this invitation becomes the delivered queue head.
  delivered_at TIMESTAMP(3) NULL,
  expires_at TIMESTAMP(3) NULL,
  responded_at TIMESTAMP(3) NULL,
  pending_receiver_id BIGINT UNSIGNED GENERATED ALWAYS AS
    (CASE WHEN status = 'pending' THEN receiver_id ELSE NULL END) STORED,
  PRIMARY KEY (invitation_id),
  UNIQUE KEY uq_invites_room_pending_receiver (room_id, pending_receiver_id),
  CONSTRAINT chk_invites_not_self CHECK (sender_id <> receiver_id),
  CONSTRAINT chk_invites_delivery CHECK (
    (delivered_at IS NULL AND expires_at IS NULL) OR
    (delivered_at IS NOT NULL AND expires_at IS NOT NULL AND expires_at > delivered_at)),
  KEY ix_invites_receiver_queue (receiver_id, status, sent_at, invitation_id),
  KEY ix_invites_room_pending (room_id, status, receiver_id),
  CONSTRAINT fk_invites_room FOREIGN KEY (room_id) REFERENCES rooms(room_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_invites_sender FOREIGN KEY (sender_id) REFERENCES users(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_invites_receiver FOREIGN KEY (receiver_id) REFERENCES users(user_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE matches (
  match_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  room_id BIGINT UNSIGNED NOT NULL,
  status ENUM('pending','in_progress','completed','draw','cancelled') NOT NULL DEFAULT 'pending',
  started_at TIMESTAMP NULL,
  ended_at TIMESTAMP NULL,
  winner_id BIGINT UNSIGNED NULL,
  PRIMARY KEY (match_id),
  -- Each START_GAME creates a new match; a room may host many successive matches.
  KEY ix_matches_room (room_id),
  CONSTRAINT fk_matches_room FOREIGN KEY (room_id) REFERENCES rooms(room_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_matches_winner FOREIGN KEY (winner_id) REFERENCES users(user_id)
    ON UPDATE CASCADE ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE match_players (
  match_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  final_score INT UNSIGNED NOT NULL DEFAULT 0,
  correct_count SMALLINT UNSIGNED NOT NULL DEFAULT 0,
  finish_time_ms BIGINT UNSIGNED NULL,
  player_status ENUM('FINISHED','QUIT','DISCONNECTED') NOT NULL DEFAULT 'FINISHED',
  PRIMARY KEY (match_id, user_id),
  KEY ix_match_players_user (user_id, match_id),
  CONSTRAINT fk_match_players_match FOREIGN KEY (match_id) REFERENCES matches(match_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_match_players_user FOREIGN KEY (user_id) REFERENCES users(user_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE rounds (
  round_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  match_id BIGINT UNSIGNED NOT NULL,
  set_id BIGINT UNSIGNED NOT NULL,
  round_number SMALLINT UNSIGNED NOT NULL,
  status ENUM('pending','in_progress','completed') NOT NULL DEFAULT 'pending',
  started_at TIMESTAMP NULL,
  ended_at TIMESTAMP NULL,
  PRIMARY KEY (round_id),
  -- Keep the original rounds table, but the current protocol has one question set per match.
  -- Replaying creates another match, not round_number = 2 in the same match.
  UNIQUE KEY uq_rounds_one_per_match (match_id),
  UNIQUE KEY uq_rounds_identity (round_id, match_id),
  CONSTRAINT chk_rounds_single_round CHECK (round_number = 1),
  UNIQUE KEY uq_rounds_match_number (match_id, round_number),
  CONSTRAINT fk_rounds_match FOREIGN KEY (match_id) REFERENCES matches(match_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_rounds_set FOREIGN KEY (set_id) REFERENCES question_sets(set_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE round_questions (
  round_question_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  round_id BIGINT UNSIGNED NOT NULL,
  question_id BIGINT UNSIGNED NOT NULL,
  position SMALLINT UNSIGNED NOT NULL,
  -- Timing belongs to each player's submission slot, never this shared question row.
  PRIMARY KEY (round_question_id),
  UNIQUE KEY uq_round_questions_position (round_id, position),
  UNIQUE KEY uq_round_questions_identity (round_question_id, question_id, round_id),
  CONSTRAINT fk_round_questions_round FOREIGN KEY (round_id) REFERENCES rounds(round_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_round_questions_question FOREIGN KEY (question_id) REFERENCES questions(question_id)
    ON UPDATE CASCADE ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE submissions (
  submission_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  round_question_id BIGINT UNSIGNED NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  question_id BIGINT UNSIGNED NOT NULL,
  match_id BIGINT UNSIGNED NOT NULL,
  -- One round per match; resolved by the server on insertion.
  round_id BIGINT UNSIGNED NOT NULL,
  answer_text VARCHAR(200) NULL,
  -- Create this row when QUESTION is sent, then update it on answer/timeout.
  question_sent_at TIMESTAMP(3) NULL,
  deadline_at TIMESTAMP(3) NULL,
  submitted_at TIMESTAMP(3) NULL,
  is_correct BOOLEAN NULL,
  awarded_points INT UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (submission_id),
  UNIQUE KEY uq_submissions_once (round_question_id, user_id),
  UNIQUE KEY uq_submissions_identity (submission_id, question_id),
  KEY ix_submissions_user (user_id),
  CONSTRAINT chk_submissions_deadline CHECK (deadline_at IS NULL OR (question_sent_at IS NOT NULL AND deadline_at > question_sent_at)),
  CONSTRAINT fk_submissions_round_question FOREIGN KEY (round_question_id) REFERENCES round_questions(round_question_id)
    ON UPDATE CASCADE ON DELETE CASCADE,
  CONSTRAINT fk_submissions_user FOREIGN KEY (user_id) REFERENCES users(user_id)
    ON UPDATE CASCADE ON DELETE RESTRICT,
  CONSTRAINT fk_submissions_question_identity FOREIGN KEY (round_question_id, question_id, round_id)
    REFERENCES round_questions(round_question_id, question_id, round_id)
    ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_submissions_participant FOREIGN KEY (match_id, user_id)
    REFERENCES match_players(match_id, user_id) ON UPDATE RESTRICT ON DELETE RESTRICT,
  CONSTRAINT fk_submissions_round_match FOREIGN KEY (round_id, match_id)
    REFERENCES rounds(round_id, match_id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;

-- SINGLE has one row, MULTI has multiple rows; TYPE has none.
-- Redundant question_id is intentional: composite FKs reject options from another question.
CREATE TABLE submission_options (
  submission_id BIGINT UNSIGNED NOT NULL,
  question_id BIGINT UNSIGNED NOT NULL,
  option_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (submission_id, option_id),
  CONSTRAINT fk_submission_options_submission FOREIGN KEY (submission_id, question_id)
    REFERENCES submissions(submission_id, question_id) ON UPDATE RESTRICT ON DELETE CASCADE,
  CONSTRAINT fk_submission_options_question FOREIGN KEY (option_id, question_id)
    REFERENCES question_options(option_id, question_id) ON UPDATE RESTRICT ON DELETE RESTRICT
) ENGINE=InnoDB;
