SET NAMES utf8mb4;

-- ============================================================
-- CodeBox MySQL Initialization
-- ============================================================

-- ============================================================
-- 1. users
-- ============================================================

CREATE TABLE IF NOT EXISTS users (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- ============================================================
-- 2. code_snippet
-- ============================================================

CREATE TABLE IF NOT EXISTS code_snippet (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    language VARCHAR(30) NOT NULL,
    tags VARCHAR(200),
    summary VARCHAR(255),
    use_count INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- ============================================================
-- 3. snippet_embedding
-- ============================================================

CREATE TABLE IF NOT EXISTS snippet_embedding (
    snippet_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    model VARCHAR(100) NOT NULL,

    -- MySQL VARCHAR has a row/column length limitation.
    -- Embedding vectors can be much larger, so TEXT is used here.
    vector TEXT NOT NULL,

    dimensions INT NOT NULL,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- ============================================================
-- 4. agent_pending_action
-- ============================================================

CREATE TABLE IF NOT EXISTS agent_pending_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    tool_call_id VARCHAR(64) NOT NULL,
    tool_name VARCHAR(64) NOT NULL,

    -- Tool arguments may contain relatively large JSON.
    -- TEXT avoids MySQL VARCHAR length limitation.
    arguments TEXT NOT NULL,

    status VARCHAR(16) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,

    CONSTRAINT fk_pending_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE CASCADE
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;


-- ============================================================
-- 5. Demo user
-- ============================================================

INSERT INTO users (
    username,
    password,
    email
)
SELECT
    'demo',
    '$2a$10$.GZnFPl.hH6kQmorDa6p6uMKqP4zpnEbnR0oe3EYJ.O69f6I2ALv.',
    'demo@codebox.local'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM users
    WHERE username = 'demo'
);


-- ============================================================
-- 6. Demo snippets
-- ============================================================

INSERT INTO code_snippet (
    user_id,
    title,
    content,
    language,
    tags,
    summary
)
SELECT
    u.id,
    'Calculate Order Total',
    'public BigDecimal calculateOrderTotal(List<OrderItem> items) {
    BigDecimal total = BigDecimal.ZERO;

    for (OrderItem item : items) {
        total = total.add(
            item.getPrice().multiply(
                BigDecimal.valueOf(item.getQuantity())
            )
        );
    }

    return total;
}',
    'Java',
    'Java,金额,计算',
    '计算订单总金额的基础 Java 方法'
FROM users u
WHERE u.username = 'demo'
  AND NOT EXISTS (
      SELECT 1
      FROM code_snippet s
      WHERE s.user_id = u.id
        AND s.title = 'Calculate Order Total'
  );


INSERT INTO code_snippet (
    user_id,
    title,
    content,
    language,
    tags,
    summary
)
SELECT
    u.id,
    'User Login Validation',
    'public boolean validateLogin(String username, String password) {
    if (username == null || password == null) {
        return false;
    }

    User user = userService.findByUsername(username);

    return user != null && passwordEncoder.matches(
        password,
        user.getPassword()
    );
}',
    'Java',
    'Java,登录,安全',
    '用户登录信息校验示例'
FROM users u
WHERE u.username = 'demo'
  AND NOT EXISTS (
      SELECT 1
      FROM code_snippet s
      WHERE s.user_id = u.id
        AND s.title = 'User Login Validation'
  );


INSERT INTO code_snippet (
    user_id,
    title,
    content,
    language,
    tags,
    summary
)
SELECT
    u.id,
    'Find User By Username',
    'public User findUserByUsername(String username) {
    if (username == null || username.isBlank()) {
        return null;
    }

    return userMapper.findByUsername(username);
}',
    'Java',
    'Java,MyBatis,用户查询',
    '根据用户名查询用户信息'
FROM users u
WHERE u.username = 'demo'
  AND NOT EXISTS (
      SELECT 1
      FROM code_snippet s
      WHERE s.user_id = u.id
        AND s.title = 'Find User By Username'
  );


INSERT INTO code_snippet (
    user_id,
    title,
    content,
    language,
    tags,
    summary
)
SELECT
    u.id,
    'List User Snippets',
    'SELECT id, title
FROM code_snippet
WHERE user_id = ?
ORDER BY create_time DESC
LIMIT ? OFFSET ?;',
    'SQL',
    'SQL,查询,分页',
    '根据用户查询代码片段并进行分页'
FROM users u
WHERE u.username = 'demo'
  AND NOT EXISTS (
      SELECT 1
      FROM code_snippet s
      WHERE s.user_id = u.id
        AND s.title = 'List User Snippets'
  );