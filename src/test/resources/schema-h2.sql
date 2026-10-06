-- H2 schema for tests. Mirrors src/main/resources/schema.sql using portable DDL.
-- The MyBatis XML is not exercised against H2 in these tests, so no MySQL
-- dialect emulation is needed here.

DROP TABLE IF EXISTS snippet_embedding;
DROP TABLE IF EXISTS code_snippet;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL UNIQUE,
  password    VARCHAR(100) NOT NULL,
  email       VARCHAR(100),
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE code_snippet (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id     BIGINT NOT NULL,
  title       VARCHAR(100) NOT NULL,
  content     VARCHAR(20000) NOT NULL,
  language    VARCHAR(30)  NOT NULL,
  tags        VARCHAR(200),
  summary     VARCHAR(255),
  use_count   INT DEFAULT 0,
  create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_snippet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE snippet_embedding (
  snippet_id  BIGINT PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  model       VARCHAR(100) NOT NULL,
  vector      VARCHAR(100000) NOT NULL,
  dimensions  INT NOT NULL,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE agent_pending_action (
  id           BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id      BIGINT NOT NULL,
  tool_call_id VARCHAR(64)  NOT NULL,
  tool_name    VARCHAR(64)  NOT NULL,
  arguments    VARCHAR(20000) NOT NULL,
  status       VARCHAR(16)  NOT NULL,
  create_time  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  expires_at   TIMESTAMP NOT NULL,
  CONSTRAINT fk_pending_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);