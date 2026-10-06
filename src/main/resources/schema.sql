-- =====================================================================
-- CodeBox 2.0 schema (MySQL 8)
-- Fresh install. For an existing CodeBox 1.x database use migrate-1.x-to-2.0.sql
-- =====================================================================

CREATE DATABASE IF NOT EXISTS codebox
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE codebox;

-- Table name is `users` (plural): avoids the reserved-word trap of `user`.
CREATE TABLE IF NOT EXISTS users (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL UNIQUE,
  -- BCrypt hash (60 chars). Never store MD5/SHA1 or plaintext here.
  password    VARCHAR(100) NOT NULL,
  email       VARCHAR(100),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS code_snippet (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  title       VARCHAR(100) NOT NULL,
  content     TEXT NOT NULL,
  language    VARCHAR(30)  NOT NULL,
  tags        VARCHAR(200),
  -- LLM-generated one-line summary (nullable: enrichment is best-effort)
  summary     VARCHAR(255),
  use_count   INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_snippet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_snippet_user_created (user_id, create_time),
  INDEX idx_snippet_language (language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Persisted embeddings so retrieval survives a restart without re-calling the model.
CREATE TABLE IF NOT EXISTS snippet_embedding (
  snippet_id  BIGINT PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  model       VARCHAR(100) NOT NULL,
  vector      MEDIUMTEXT   NOT NULL,
  dimensions  INT NOT NULL,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_embedding_snippet FOREIGN KEY (snippet_id) REFERENCES code_snippet(id) ON DELETE CASCADE,
  INDEX idx_embedding_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


-- ---------------------------------------------------------------------
-- Agent write-actions awaiting user approval (human-in-the-loop).
-- Persisted server-side so approval replays the captured arguments
-- verbatim rather than trusting anything the client sends back.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_pending_action (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT NOT NULL,
  tool_call_id VARCHAR(64)  NOT NULL,
  tool_name    VARCHAR(64)  NOT NULL,
  arguments    TEXT         NOT NULL,
  status       VARCHAR(16)  NOT NULL,
  create_time  DATETIME DEFAULT CURRENT_TIMESTAMP,
  expires_at   DATETIME     NOT NULL,
  CONSTRAINT fk_pending_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_pending_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;