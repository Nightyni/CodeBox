-- =====================================================================
-- CodeBox 2.0 - migrate an existing CodeBox 1.x database, or seed fresh data
--
-- Run:  mysql -u root -p < migrate-1.x-to-2.0.sql
--
-- What it does:
--   1. renames 'user' -> 'users' (avoids the reserved-word trap)
--   2. widens ids to BIGINT and adds the LLM summary column + embedding table
--   3. resets the demo accounts to BCrypt hashes of 'codebox123'
--   4. inserts the 32 seed snippets carried over from CodeBox 1.x
--
-- NOTE: docker/mysql-init/01-schema-and-seed.sql is the same content minus the
-- CREATE DATABASE / USE lines (the container creates the DB itself). Keep the
-- two in sync when you change the schema.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS codebox
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE codebox;

CREATE TABLE IF NOT EXISTS users (
  id          BIGINT PRIMARY KEY AUTO_INCREMENT,
  username    VARCHAR(50)  NOT NULL UNIQUE,
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
  summary     VARCHAR(255),
  use_count   INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_snippet_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_snippet_user_created (user_id, create_time),
  INDEX idx_snippet_language (language)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
-- Demo account. Password: codebox123
-- Hash is BCrypt (cost 10), generated at build time - NOT md5.
-- Regenerate with: new BCryptPasswordEncoder().encode("codebox123")
-- ---------------------------------------------------------------------
INSERT INTO users (username, password, email) VALUES
  ('demo', '$2a$10$xkgKFdApYxurKAeqvG/HROrnbiH11daezeVYSDUp9CnfS7itxwiIe', 'demo@codebox.local'),
  ('admin', '$2a$10$xkgKFdApYxurKAeqvG/HROrnbiH11daezeVYSDUp9CnfS7itxwiIe', 'admin@codebox.local')
ON DUPLICATE KEY UPDATE email = VALUES(email);

SET @demo_user_id = (SELECT id FROM users WHERE username = 'demo');

-- ---------------------------------------------------------------------
-- Seed snippets (carried over from CodeBox 1.x)
-- summary is left NULL: it gets filled by the LLM on first edit,
-- or run the re-index endpoint to enrich the whole library at once.
-- ---------------------------------------------------------------------
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Java 8 Stream 过滤集合', 'List<User> adultUsers = users.stream().filter(user -> user.getAge() >= 18).collect(Collectors.toList());', 'Java', 'Stream,集合,Java8');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Java 读取文件内容', 'try (BufferedReader br = new BufferedReader(new FileReader("file.txt"))) { String line; while ((line = br.readLine()) != null) { System.out.println(line); } } catch (IOException e) { e.printStackTrace(); }', 'Java', 'IO,文件操作');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Spring Boot 定时任务', '@Scheduled(cron = "0 0 9 * * MON")\\npublic void scheduledTask() {\\n    System.out.println("每周一上午9点执行");\\n}', 'Java', 'Spring,定时任务');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Java 单例模式-双重检查锁', 'public class Singleton {\\n    private static volatile Singleton instance;\\n    private Singleton() {}\\n    public static Singleton getInstance() {\\n        if (instance == null) {\\n            synchronized (Singleton.class) {\\n                if (instance == null) {\\n                    instance = new Singleton();\\n                }\\n            }\\n        }\\n        return instance;\\n    }\\n}', 'Java', '设计模式,单例');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Java 多线程-线程池创建', 'ExecutorService executor = Executors.newFixedThreadPool(10);\\nexecutor.submit(() -> {\\n    System.out.println("任务执行");\\n});\\nexecutor.shutdown();', 'Java', '多线程,线程池');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Java 日期格式化', 'LocalDateTime now = LocalDateTime.now();\\nDateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");\\nString formatted = now.format(formatter);', 'Java', '日期,工具类');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'MyBatis Plus 分页查询', 'Page<User> page = new Page<>(pageNum, pageSize);\\nLambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();\\nwrapper.eq(User::getStatus, 1);\\nPage<User> result = userMapper.selectPage(page, wrapper);', 'Java', 'MyBatisPlus,分页');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Spring Security 密码加密', '@Bean\\npublic PasswordEncoder passwordEncoder() {\\n    return new BCryptPasswordEncoder();\\n}\\nString encodedPassword = passwordEncoder().encode("123456");', 'Java', 'Spring,安全');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Java 冒泡排序算法', 'public void bubbleSort(int[] arr) {\\n    for (int i = 0; i < arr.length - 1; i++) {\\n        for (int j = 0; j < arr.length - 1 - i; j++) {\\n            if (arr[j] > arr[j + 1]) {\\n                int temp = arr[j];\\n                arr[j] = arr[j + 1];\\n                arr[j + 1] = temp;\\n            }\\n        }\\n    }\\n}', 'Java', '算法,排序');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Java 异常处理最佳实践', 'try {\\n    // 业务代码\\n} catch (SpecificException e) {\\n    log.error("业务异常", e);\\n    throw new BusinessException(e.getMessage());\\n} catch (Exception e) {\\n    log.error("系统异常", e);\\n    throw new SystemException("系统繁忙");\\n}', 'Java', '异常处理,最佳实践');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'JavaScript 异步/等待', 'async function fetchData() {\\n    try {\\n        const response = await fetch("/api/users");\\n        const data = await response.json();\\n        console.log(data);\\n    } catch (error) {\\n        console.error("请求失败:", error);\\n    }\\n}', 'JavaScript', '异步,API');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'React 组件示例', 'function Welcome(props) {\\n    return <h1>Hello, {props.name}!</h1>;\\n}\\nfunction App() {\\n    return (\\n        <div>\\n            <Welcome name="Alice" />\\n            <Welcome name="Bob" />\\n        </div>\\n    );\\n}', 'JavaScript', 'React,组件');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Vue 3 组合式 API', 'import { ref, onMounted } from "vue";\\nexport default {\\n    setup() {\\n        const count = ref(0);\\n        const increment = () => count.value++;\\n        onMounted(() => {\\n            console.log("组件已挂载");\\n        });\\n        return { count, increment };\\n    }\\n}', 'JavaScript', 'Vue,组合式API');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'JavaScript 防抖函数', 'function debounce(func, delay) {\\n    let timer;\\n    return function(...args) {\\n        clearTimeout(timer);\\n        timer = setTimeout(() => func.apply(this, args), delay);\\n    };\\n}\\nconst debouncedSearch = debounce(searchInput, 300);', 'JavaScript', '工具函数,防抖');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'JavaScript 深拷贝', 'function deepClone(obj) {\\n    if (obj === null || typeof obj !== "object") return obj;\\n    if (obj instanceof Date) return new Date(obj);\\n    if (obj instanceof Array) return obj.map(item => deepClone(item));\\n    const clonedObj = {};\\n    for (let key in obj) {\\n        if (obj.hasOwnProperty(key)) {\\n            clonedObj[key] = deepClone(obj[key]);\\n        }\\n    }\\n    return clonedObj;\\n}', 'JavaScript', '工具函数,深拷贝');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'JavaScript 数组排序', 'const numbers = [3, 1, 4, 1, 5, 9];\\nnumbers.sort((a, b) => a - b);\\nconst users = [{name: "张三", age: 25}, {name: "李四", age: 20}];\\nusers.sort((a, b) => a.age - b.age);', 'JavaScript', '数组,排序');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Python 列表推导式', 'squares = [x**2 for x in range(10)]\\nevens = [x for x in range(20) if x % 2 == 0]\\npairs = [(x, y) for x in [1,2,3] for y in [3,1,4] if x != y]', 'Python', '列表推导式,Python技巧');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Python 装饰器', 'def timer(func):\\n    def wrapper(*args, **kwargs):\\n        start = time.time()\\n        result = func(*args, **kwargs)\\n        end = time.time()\\n        print(f"{func.__name__} took {end-start:.2f}s")\\n        return result\\n    return wrapper\\n\\n@timer\\ndef slow_function():\\n    time.sleep(1)', 'Python', '装饰器,高级特性');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Python 快速排序', 'def quick_sort(arr):\\n    if len(arr) <= 1:\\n        return arr\\n    pivot = arr[len(arr) // 2]\\n    left = [x for x in arr if x < pivot]\\n    middle = [x for x in arr if x == pivot]\\n    right = [x for x in arr if x > pivot]\\n    return quick_sort(left) + middle + quick_sort(right)', 'Python', '算法,排序');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Python 爬虫示例', 'import requests\\nfrom bs4 import BeautifulSoup\\nurl = "https://example.com"\\nresponse = requests.get(url)\\nsoup = BeautifulSoup(response.text, "html.parser")\\ntitles = soup.find_all("h1")\\nfor title in titles:\\n    print(title.text)', 'Python', '爬虫,网络请求');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Python 数据处理-pandas', 'import pandas as pd\\ndf = pd.read_csv("data.csv")\\ndf.dropna(inplace=True)\\ndf["date"] = pd.to_datetime(df["date"])\\nresult = df.groupby("category")["value"].sum()', 'Python', '数据分析,pandas');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Python Flask 路由', 'from flask import Flask, jsonify, request\\napp = Flask(__name__)\\n\\n@app.route("/api/users", methods=["GET"])\\ndef get_users():\\n    users = [{"id": 1, "name": "Alice"}]\\n    return jsonify(users)\\n\\nif __name__ == "__main__":\\n    app.run(debug=True)', 'Python', 'Flask,Web框架');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'SQL 复杂联表查询', 'SELECT u.username, u.email, COUNT(s.id) as snippet_count, SUM(s.use_count) as total_uses\\nFROM user u\\nLEFT JOIN code_snippet s ON u.id = s.user_id\\nWHERE u.create_time >= "2024-01-01"\\nGROUP BY u.id\\nORDER BY total_uses DESC\\nLIMIT 10;', 'SQL', '联表,分组,聚合');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'SQL 窗口函数-RANK', 'SELECT name, department, salary, RANK() OVER (PARTITION BY department ORDER BY salary DESC) as rank_in_dept FROM employees;', 'SQL', '窗口函数,排名');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'SQL 递归查询-CTE', 'WITH RECURSIVE cte AS (\\n    SELECT id, name, parent_id, 1 as level\\n    FROM categories\\n    WHERE parent_id IS NULL\\n    UNION ALL\\n    SELECT c.id, c.name, c.parent_id, cte.level + 1\\n    FROM categories c\\n    INNER JOIN cte ON c.parent_id = cte.id\\n)\\nSELECT * FROM cte;', 'SQL', '递归,CTE');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'SQL 索引优化', 'CREATE INDEX idx_user_status_time ON user(status, create_time);\\n-- 错误写法\\nSELECT * FROM user WHERE DATE(create_time) = "2024-01-01";\\n-- 正确写法\\nSELECT * FROM user WHERE create_time >= "2024-01-01" AND create_time < "2024-01-02";', 'SQL', '索引,优化');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Flexbox 居中布局', '.container {\\n    display: flex;\\n    justify-content: center;\\n    align-items: center;\\n    height: 100vh;\\n}', 'HTML/CSS', 'Flexbox,布局,居中');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Spring Bean 配置', '<?xml version="1.0" encoding="UTF-8"?>\\n<beans xmlns="http://www.springframework.org/schema/beans"\\n       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"\\n       xsi:schemaLocation="http://www.springframework.org/schema/beans\\n       http://www.springframework.org/schema/beans/spring-beans.xsd">\\n\\n    <bean id="userService" class="com.codebox.service.UserServiceImpl">\\n        <property name="userMapper" ref="userMapper"/>\\n    </bean>\\n\\n</beans>', 'XML', 'Spring,配置');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Go 语言并发示例', 'package main\\nimport (\\n    "fmt"\\n    "time"\\n)\\nfunc worker(id int, jobs <-chan int, results chan<- int) {\\n    for job := range jobs {\\n        fmt.Printf("Worker %d processing job %d\\n", id, job)\\n        time.Sleep(time.Second)\\n        results <- job * 2\\n    }\\n}\\nfunc main() {\\n    jobs := make(chan int, 100)\\n    results := make(chan int, 100)\\n    for w := 1; w <= 3; w++ {\\n        go worker(w, jobs, results)\\n    }\\n    for j := 1; j <= 5; j++ {\\n        jobs <- j\\n    }\\n    close(jobs)\\n}', 'Go', '并发,Channel');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'TypeScript 类型定义', 'interface User {\\n    id: number;\\n    name: string;\\n    email?: string;\\n    readonly createdAt: Date;\\n}\\ntype ApiResponse<T> = {\\n    code: number;\\n    message: string;\\n    data: T;\\n};\\nfunction fetchUser(id: number): Promise<ApiResponse<User>> {\\n    return fetch(`/api/users/${id}`).then(res => res.json());\\n}', 'TypeScript', '类型定义,接口');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Docker 基础命令', 'docker build -t myapp:latest .\\ndocker run -d -p 8080:8080 --name myapp myapp:latest\\ndocker logs -f myapp\\ndocker exec -it myapp /bin/bash\\ndocker stop myapp\\ndocker rm myapp', 'Docker', '容器,DevOps');
INSERT INTO code_snippet (user_id, title, content, language, tags) VALUES
  (@demo_user_id, 'Git 常用命令', 'git checkout -b feature/new-feature\\ngit branch -d old-branch\\ngit log --oneline --graph\\ngit commit --amend -m "修正提交信息"\\ngit reset --soft HEAD~1\\ngit reset --hard HEAD~1\\ngit stash save "临时保存"\\ngit stash pop\\ngit merge feature --no-ff', 'Git', '版本控制,命令');

SELECT COUNT(*) AS total_users    FROM users;
SELECT COUNT(*) AS total_snippets FROM code_snippet;
SELECT language, COUNT(*) AS n FROM code_snippet GROUP BY language ORDER BY n DESC;





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