-- H2 schema for the `h2` profile: run the app with zero external services.
-- Usage: mvn spring-boot:run -Dspring-boot.run.profiles=h2

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
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE snippet_embedding (
  snippet_id  BIGINT PRIMARY KEY,
  user_id     BIGINT NOT NULL,
  model       VARCHAR(100) NOT NULL,
  vector      VARCHAR(100000) NOT NULL,
  dimensions  INT NOT NULL,
  update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Demo account: demo / codebox123  (BCrypt, cost 10)
INSERT INTO users (username, password, email) VALUES
  ('demo', '$2a$10$xkgKFdApYxurKAeqvG/HROrnbiH11daezeVYSDUp9CnfS7itxwiIe', 'demo@codebox.local');

INSERT INTO code_snippet (user_id, title, content, language, tags, summary) VALUES
  (1, '分页查询与总数统计', 'SELECT id, title FROM code_snippet WHERE user_id = ? ORDER BY create_time DESC LIMIT ? OFFSET ?;', 'SQL', '分页,查询', '按用户分页查询，条件需与计数 SQL 保持一致'),
  (1, 'Java 8 Stream 条件过滤', 'List<User> adults = users.stream().filter(u -> u.getAge() >= 18).collect(Collectors.toList());', 'Java', 'Stream,集合,Java8', '用 Stream 过滤集合并收集为 List'),
  (1, 'Redis 分布式锁', 'Boolean ok = redis.opsForValue().setIfAbsent(key, token, Duration.ofSeconds(30));', 'Java', 'Redis,分布式锁', '基于 setIfAbsent 实现带过期时间的分布式锁'),
  (1, 'MyBatis 动态条件查询', 'AND title LIKE CONCAT(''%'', #{keyword}, ''%'')', 'XML', 'MyBatis,动态SQL', 'MyBatis 动态拼接可选查询条件');

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