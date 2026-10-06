# CodeBox — AI 代码知识检索与问答助手

把个人/团队的代码片段沉淀成**可语义检索、可自然语言问答**的知识库，解决"重复造轮子、文档散落、新人上手慢"的问题。

> 这是 CodeBox 的 2.0 版本。1.x（SSM + JSP）的代码保留在 `.legacy-ssm/`，仅供对照，可以随时删除。

---

## 1. 它解决什么问题

| 传统做法 | CodeBox 2.0 |
|---|---|
| 靠关键词搜标题，搜"分页"搜不到"LIMIT OFFSET" | 自然语言提问，语义检索 + LLM 基于检索结果作答并给出引用 |
| 手动填标签、写描述，懒得填就烂尾 | 保存时 LLM 自动补标签和一句话摘要，保留人工填写的值 |
| 知识只存在个人电脑里 | REST API + Web UI，可部署给团队用 |

核心是 **RAG**：检索（向量召回）→ 拼装上下文 → 让模型**只依据检索到的片段**回答，证据不足时明确拒答，而不是编造。

---

## 2. 快速开始

### 方式 A：Docker（推荐，一条命令）

```bash
cp .env.example .env      # 填入 DEEPSEEK_API_KEY（可留空，见下）
docker compose up --build
```

打开 <http://localhost:8080>，用 **demo / codebox123** 登录。

MySQL 首次启动会自动建表并导入 32 条种子片段。
> `.env` 里的 `DEEPSEEK_API_KEY` 留空时应用照常运行，只是不生成回答、只返回检索结果。

### 方式 B：本地零依赖（内存数据库，无需 MySQL）

```bash
# PowerShell 5.1 必须给 -D 参数加引号，否则会在点号处被截断
mvn spring-boot:run "-Dspring-boot.run.profiles=h2"
# 或者直接跑 jar（推荐，没有引号问题）
java -jar target/codebox.jar --spring.profiles.active=h2
```

#### 配置 DeepSeek API Key

推荐写进 `config/application-local.yml`（该文件已被 `.gitignore` 忽略，不会提交）：

```yaml
codebox:
  llm:
    api-key: "sk-你的key"
```

读取优先级：`config/application-local.yml` > 环境变量 `DEEPSEEK_API_KEY` > `CODEBOX_LLM_API_KEY`。

**不填 Key 也能跑**：应用正常启动，检索与引用正常，只是不生成 AI 回答、不自动打标。

### 前端构建（Vue 3）

前端源码在 `frontend/`，构建产物输出到 `src/main/resources/static/`，因此**打 jar 前要先构建前端**：

```powershell
.\build.ps1 clean package -WithFrontend   # 一步搞定：构建前端 + 打 jar
```

或者手动两步：

```powershell
cd frontend
npm install --ignore-scripts    # 首次
npm run build
cd ..
mvn clean package -DskipTests
```

开发时热更新（前端 5173，`/api` 由 Vite 代理到后端 8080）：

```powershell
cd frontend
npm run dev
```

> `npm install --ignore-scripts` 是刻意的：vite 需要的 esbuild 二进制随 optionalDependencies 分发，不依赖 postinstall 下载，跳过脚本更安全也更快。
>
> 旧版原生 JS 界面保留在 **`/legacy.html`**，可以和新版对照。

### 方式 C：本地 + MySQL

```bash
mysql -u root -p < migrate-1.x-to-2.0.sql     # 建库、建表、导入种子数据
mvn clean package
java -jar target/codebox.jar
```

数据库连接可用环境变量覆盖：`CODEBOX_DB_URL` / `CODEBOX_DB_USER` / `CODEBOX_DB_PASSWORD`。

### 跑测试

```powershell
.\build.ps1 test        # 193 个 Java 测试 + 17 个前端单测 + SFC 检查，不需要数据库、不需要 API Key
```

`build.ps1` 只是把 Maven 的本地仓库指向工作区内的 `.m2-local/`。这样做是因为 Maven 默认要写 `%USERPROFILE%\.m2\repository`（在工作区之外），在受限环境里会被拒绝并报出容易误判的 `AccessDeniedException`。不想用脚本就直接 `mvn test`。

### 前端冒烟检查

后端测试**覆盖不到前端**。`.uitest/harness.js` 用一套最小 DOM 直接执行真实的 `app.js`，检查初始化不抛异常、选择器全部命中、新增弹窗/取消/拖动/清空可用。`build.ps1` 会自动带上它（没装 Node 时跳过）。

为什么需要：曾经有一处把**裸 id** 传给要求 `'#id'` 的选择器辅助函数，返回 `null` 后在**模块顶层抛异常**——该行之后所有绑定都没执行，于是「+ 新增」点不动、分隔条拖不动，而页面看起来完全正常，后端 177 个测试全绿、`node --check` 也通过。只有真的跑一遍前端代码才能发现。

> 测试如何验证 Agent 而不花钱：`ChatModel` 是接口，测试用 `ScriptedChatModel` 脚本化返回 `tool_calls`，因此**多轮工具调用、审批拦截、步数上限、工具报错回灌**全部可确定性验证，不调用真实 API、不消耗 token。

---

## 3. 技术栈

| 层 | 选型 | 说明 |
|---|---|---|
| 框架 | **Spring Boot 3.5.9** / Java 21 | 单个可执行 jar，`java -jar` 即可启动 |
| 持久层 | MyBatis 3.0.4 + MySQL 8 | 动态 SQL 用在搜索条件拼装上 |
| 模型 | **DeepSeek**（OpenAI 兼容接口） | 自动打标、摘要、问答 |
| 向量检索 | 可插拔 `EmbeddingModel` | 支持任意 OpenAI 兼容 embedding 服务；未配置时回退到内置本地向量化 |
| 前端 | **Vue 3 + Vite** | 组合式 API，产物打进 `static/`（单 jar 部署）；文本插值渲染，天然免疫 XSS |
| 部署 | Docker 多阶段构建 + Compose | 非 root 运行、带健康检查 |

**为什么不用 Spring AI / LangChain**：这个项目的 RAG 链路只有"向量化 → 余弦召回 → 拼 prompt → 调模型"四步，用 `RestClient` 直连约 200 行就够。引入框架会带来版本耦合（Spring AI 尚在快速迭代），而收益不明确。链路清晰、可测试、可控，是这个规模下的正确取舍。

---

## 4. 架构

```
                       ┌──────────────────────────────────────┐
  浏览器 (SPA)  ──────▶│  Controller  (REST + Session 鉴权)    │
                       └───────┬──────────────────────┬───────┘
                               │                      │
                    ┌──────────▼─────────┐   ┌────────▼─────────┐
                    │  SnippetService    │   │  AskController   │
                    │  CRUD / 校验 / 分页 │   │  (RAG 问答)      │
                    └───┬────────────┬───┘   └────────┬─────────┘
                        │            │                │
             ┌──────────▼───┐  ┌─────▼────────┐  ┌────▼──────────────┐
             │ SnippetEnricher│ │RetrievalService│ │ AnswerGenerator │
             │  (LLM 打标)   │  │  向量召回 Top-K │ │  防幻觉 prompt  │
             └──────┬───────┘  └─────┬────────┘  └────┬──────────────┘
                    │                │                │
             ┌──────▼────────────────▼────────────────▼──────┐
             │            ChatModel / EmbeddingModel         │
             │        (DeepSeek / OpenAI 兼容 / 本地回退)     │
             └──────────────────┬───────────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │  MyBatis → MySQL      │
                    │  users / code_snippet │
                    │  snippet_embedding    │
                    └───────────────────────┘
```

**一次"提问"的完整链路**

1. `POST /api/ask` 带问题进来，Session 解析出当前用户
2. `RetrievalService` 把问题向量化，与该用户的向量做余弦召回，取 Top-5（相似度 ≥ 0.05）
3. `AnswerGenerator` 把命中的片段编号成 `[1] [2] …` 注入 prompt
4. 系统提示词强制：**只能用 CONTEXT 内容**、每个结论标注来源编号、证据不足必须返回 `INSUFFICIENT_EVIDENCE`
5. 模型返回 `INSUFFICIENT_EVIDENCE` 时，接口把 `insufficientEvidence=true` 返回给前端，并显示"证据不足"标记
6. 前端把引用渲染成可点击的 chip，点开就是对应片段

**向量存储的取舍**：向量持久化在 `snippet_embedding` 表，同时缓存在内存 Map 里。查询走内存余弦扫描——个人片段库规模在数百到数千条，全量扫描比引入外部向量数据库更快、运维更简单。数据量真的上去之后再换 pgvector/Milvus，接口不用改。

---

## 5. API

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/auth/register` | 注册（用户名 3-50、密码 ≥ 8 位、邮箱格式校验） |
| `POST` | `/api/auth/login` | 登录，建立 Session |
| `GET` | `/api/auth/me` | 当前登录用户 |
| `POST` | `/api/auth/logout` | 退出 |
| `GET` | `/api/snippets` | 分页 + 关键词/语言/标签筛选 |
| `GET` | `/api/snippets/{id}` | 详情（累加使用次数） |
| `POST` | `/api/snippets` | 新增（LLM 自动补标签/摘要） |
| `PUT` | `/api/snippets/{id}` | 修改 |
| `DELETE` | `/api/snippets/{id}` | 删除 |
| `POST` | `/api/ask` | **自然语言问答（RAG）**。问"我的知识库有哪些内容"会返回真实统计，不走向量检索 |
| `POST` | `/api/ask/retrieve` | 只做检索、不调模型（排查召回问题用） |
| `POST` | `/api/ask/reindex` | 重建当前用户的向量索引 |
| `POST` | `/api/agent/chat` | **AI 助手（Agent + Tool Calling）**：能查、能分析、能优化、能写 |
| `POST` | `/api/agent/confirm` | 批准/拒绝 Agent 提出的写操作（Human-in-the-loop） |
| `GET` | `/api/health` | 存活探针 |

**`/api/ask` 与 `/api/agent/chat` 的区别**（这是本项目最值得讲的一处设计）：

| | `/api/ask` | `/api/agent/chat` |
|---|---|---|
| 能力性质 | **只读**：检索 + 总结 | **可读写**：通过工具操作业务 |
| 交互 | 单轮：问 → 答 | 多轮循环：理解 → 调工具 → 看结果 → 再决策 |
| 回答的 | "告诉我信息" | "帮我做事情" |

**关于 reindex**：直接写 SQL 导入的片段没有向量。**应用启动时会自动补建缺失的索引**，一般不用手动调 `/api/ask/reindex`（页面上也有「重建索引」按钮）。

**关于相似度阈值**：`MIN_SCORE = 0.18`。这个值是实测出来的——用内置本地向量化时，真正相关的查询得分约 0.5（如 `redis 分布式锁` → 0.53），而无关文本只有 0.06~0.12。阈值设成 0.05 会把纯噪声当成引用展示给用户，这是开发中实际踩到的坑。

**关于"知识库概览"类问题**：像"我的知识库有哪些内容"这种问法，在片段文本里没有任何对应词，向量检索只能返回一个随机的最近邻（实测 0.06），所以这类问题由 `StatsService` 直接统计回答，不走检索。

---

## 5.1 AI Agent 层（Tool Calling）

用户可以用一条连续对话完成一串操作：

```
"帮我找一下 MyBatis 分页代码"   → search_snippets
"分析一下第一个"                → get_snippet → analyze_snippet
"帮我优化一下"                  → optimize_snippet
"保存这个版本"                  → create_snippet（需确认）
```

**Agent 循环**：

```
user message
   → LLM（带上 tools 参数）
   → 若返回 tool_calls：执行 → 结果作为 tool 消息回灌 → 再问 LLM
   → 若返回普通回答：结束，返回 answer + trace
   → 最多 6 步，防死循环
```

**7 个工具，分两类**（架构上刻意区分，便于解释）：

| 类型 | 工具 | 风险 |
|---|---|---|
| **Business Tools**（操作业务数据） | `search_snippets` / `get_snippet` | READ |
| | `create_snippet` / `update_snippet` / `delete_snippet` | **WRITE（需确认）** |
| **Capability Tools**（调用 LLM 能力） | `analyze_snippet` / `optimize_snippet` | READ |

### 结构化输出与 Diff

`optimize_snippet` **不返回模型的原始 markdown**，而是返回结构化数据：

```json
{
  "changes": ["增加 ORDER BY", "补充总数查询"],
  "optimizedCode": "SELECT id, COUNT(*) OVER() ...",
  "diff": [{ "type": "REMOVED", "originalNumber": 1, "original": "...", "optimized": null }, ...],
  "diffSummary": { "added": 5, "removed": 1 }
}
```

**为什么在服务端算 diff 而不是前端比字符串**：
- 让"改了什么"成为**工具的契约**，任何消费方拿到的是同一份答案，而不是各家 UI 各比各的
- 后续写库时，可以对着**用户当时看到的那份 diff** 再校验一次
- 行级 LCS 是纯函数、易测（14 个单测），放服务端反而比前端更省事

**界面**：左侧业务区 7 / 右侧 AI 助手 3，中间可拖动（比例存 localStorage）。AI 面板里**每一轮对话都保留**（用户气泡 + 助手回复 + 可展开的工具调用详情 + 引用片段 + Diff 视图），不是只显示最后一轮。

**三条关键的安全设计**：

1. **`userId` 不是工具参数**。它只存在于服务端的 `ToolContext`，由 HTTP Session 注入。模型无论怎么构造 arguments 都无法访问他人数据——这是"AI 能力边界 = 注册的工具集合"的落地方式，LLM 拿不到任何 SQL 能力。
2. **Human-in-the-loop**：WRITE 工具**在 Agent 循环里永不执行**。循环捕获参数、落库为 `agent_pending_action`、然后**中断**并把决定权交给用户。用户批准时**重放数据库里存的参数**，因此模型在"确认"这一步完全不参与，不可能"用户确认了 A、实际执行了 B"。
3. **审批状态是服务端权威**：`claim()` 用条件更新 `WHERE id=? AND status='PENDING'`，并发两次确认只有一次能成功；同时校验 `pendingAction.userId == 当前登录用户`，并用 10 分钟 TTL 兜底。

**可观测性**：每次运行都产出 `trace[]`（哪一步是模型、哪一步调了哪个工具、耗时、失败原因）。没有它，Agent 就是一个黑盒——用户和面试官都无法判断模型是真的调用了工具还是凭空编造。

---

## 5.2 前端（Vue 3）

```
frontend/
├── index.html
├── vite.config.js        产物输出到 ../src/main/resources/static
├── src/
│   ├── main.js / App.vue
│   ├── api.js            fetch 封装：带 Session、结构化错误透传
│   ├── store.js          当前用户 + 对话记录（用 reactive，不引入 Pinia）
│   ├── utils.js          纯逻辑：对话历史过滤、引用去重、diff 定位、比例计算
│   ├── styles.css        主题变量与基础样式
│   ├── components/
│   │   ├── ResizableSplit.vue   左右可拖动分栏
│   │   ├── AiPanel.vue          AI 助手：多轮线程 + 工具轨迹 + 确认流
│   │   ├── ChatMessage.vue      气泡（代码块单独渲染）
│   │   ├── DiffView.vue         左右并排 diff
│   │   └── SnippetEditor.vue    新增/编辑/查看弹窗
│   └── views/
│       ├── LoginView.vue        登录 / 注册
│       └── LibraryView.vue      搜索 + 卡片网格 + 分页
└── test/
    ├── utils.test.js     17 个纯逻辑单测（node:test，无依赖）
    └── sfc-check.js      SFC 静态检查
```

**前端测试**（后端 177 个测试覆盖不到前端，所以单独做）：

| 检查 | 数量 | 说明 |
|---|---|---|
| `utils.test.js` | 17 | 对话历史的合法性过滤、引用去重、diff 定位、代码块拆分、拖动比例边界 |
| `sfc-check.js` | 8 个 SFC | script 语法、标签严格配对、模板标识符是否声明 |

两条都是 **零依赖**，直接 `node frontend/test/utils.test.js` 就能跑，`build.ps1` 会自动带上。

**为什么不引 Pinia / Vue Router**：共享状态只有"当前用户"和"对话记录"，用 `reactive` 足够；未登录/已登录是两棵互斥的树，组件切换即可，不需要路由。多一个依赖就多一层概念，收益为零。

---
## 6. 安全性：相对 1.x 修了什么

| 问题（1.x） | 现状（2.0） |
|---|---|
| **存储型 XSS**：详情页 `${snippet.content}` 原样输出，任何片段都能注入 `<script>` | 后端只返回 JSON；前端全部用 `textContent` / `createTextNode` 渲染，用户内容永远不被当作 HTML 解析 |
| **删除走 GET**：`/snippet/delete?id=` 可被预取、爬虫、`<img src>` 触发 | 改为 `DELETE /api/snippets/{id}`；有测试断言 GET 不可达删除逻辑 |
| **无 CSRF 防护** | Session Cookie 设 `SameSite=Lax` + `HttpOnly`；写操作不再是简单 GET |
| **密码用无盐 MD5** | BCrypt（cost 10），注册时编码、登录时 `matches` 校验；种子账号的哈希也是真实生成并验证过的 |
| **`incrementUseCount` 不校验归属**，可给他人片段刷计数 | SQL 层加 `AND user_id = #{userId}`，越权在数据库层被挡住 |
| **无参数校验**，`?pageSize=0` 会导致除零 → `LIMIT 0, 2147483647` 报错 | `pageSize` 夹在 1–100，`pageNum` 下限 1；Bean Validation 校验所有入参并返回字段级错误 |
| **异常堆栈直接吐给前端**（含 SQL） | 全局异常处理器：业务异常返回结构化 JSON，未知异常只回"服务器内部错误"，堆栈只进日志 |
| **JDBC 口令硬编码进仓库** | 全部走环境变量，仓库只留 `.env.example` |
| **Session 固定攻击** | 登录成功后 `invalidate()` 旧 Session 再建新的 |
| **无测试** | 193 个测试覆盖校验、鉴权、越权、分页边界、RAG 降级，Agent 的多轮工具调用、审批拦截、步数上限、跨用户确认拒绝，**以及 7 个真实业务 Tool 的参数与边界**（缺参/类型错误/超长/不存在 ID/越权/零行影响） |

---

## 7. 项目结构

```
frontend/                  ★ Vue 3 前端（构建产物输出到 static/）
├── src/components/        ResizableSplit / AiPanel / ChatMessage / DiffView / SnippetEditor
├── src/views/             LoginView / LibraryView
├── src/{api,store,utils}.js
└── test/                  17 个纯逻辑单测 + 8 个 SFC 静态检查

src/main/resources/static/
├── index.html             Vue 入口（构建产物）
├── assets/*               Vue 打包产物（文件名带 hash）
├── legacy.html            旧版原生 JS 界面（保留用于对照）
└── legacy-app.js

src/main/java/com/codebox/
├── CodeBoxApplication.java
├── controller/          REST 接口 + 统一异常处理
│   ├── BaseController    Session 鉴权基类
│   ├── AuthController / SnippetController / AskController / HealthController
│   └── ApiException / GlobalExceptionHandler
├── service/
│   ├── AuthService       BCrypt 注册/登录
│   └── SnippetService    CRUD、分页边界、归属校验
├── rag/
│   ├── RetrievalService  向量索引 + 余弦召回 + reindex
│   ├── AnswerGenerator   防幻觉问答
│   ├── SnippetEnricher   LLM 自动打标/摘要
│   ├── EmbeddingModel / LocalHashEmbeddingModel / EmbeddingClient
│   └── VectorMath        序列化、余弦相似度
├── llm/
│   ├── LlmProperties / ChatModel
│   └── DeepSeekChatModel  DeepSeek 客户端（单轮 + Tool Calling，失败即降级）
├── agent/                ★ AI Agent 层
│   ├── dto/               ChatMessage / ToolCall / ToolSpec / AssistantTurn / AgentResponse
│   ├── tool/
│   │   ├── AgentTool / RiskLevel / ToolContext / ToolSchema / ToolJson
│   │   ├── ToolRegistry   按 name 注册与调用，异常统一转成错误结果
│   │   ├── business/       search / get / create / update / delete
│   │   └── capability/     analyze / optimize（内含 CodeAnalyzer）
│   └── service/
│       ├── SnippetAgent    ★ Agent 循环（6 步上限、写操作拦截、trace）
│       ├── PendingActionService  审批状态机（归属校验、TTL、幂等）
│       └── AgentPrompt     系统提示词与约束
├── mapper/               MyBatis 接口（XML 在 resources/mapper）
├── entity/ dto/
└── resources/
    ├── static/           前端 SPA
    ├── mapper/*.xml
    ├── application.yml / application-h2.yml
    └── schema.sql / schema-h2.sql
```

---

## 8. 设计取舍记录（面试可讲）

- **为什么不用 ORM（JPA）**：搜索条件是多维动态拼接，MyBatis 的动态 SQL 比 Criteria API 更直观可控；而且能精确控制"搜索"与"计数"共用同一段 WHERE，避免两者条件漂移导致分页错乱——这正是 1.x 里那个 bug 的根源。
- **为什么 embedding 可插拔**：DeepSeek 没有公开的 embedding 接口。如果强依赖某个 embedding 服务，项目就"必须有 Key 才能跑"。做成接口后：没有 Key 用内置向量化（能跑、能演示），配了 Key 就是真正的语义检索，接口不变。
- **为什么 LLM 失败要降级而不是报错**：模型是外部依赖，会超时、会限流。打标失败不该让"保存片段"这个动作失败；问答失败也该把检索结果还给用户。所以 `DeepSeekChatModel` 出错返回空串，调用方按"没有回答"处理。
- **为什么 SQL 初始化要显式写 `encoding: UTF-8`**：Windows 中文环境下平台默认字符集是 GBK，种子脚本里的中文会被按 GBK 解读后写进库，变成一库乱码。这个坑是实测踩出来的（接口返回的字节流才是真相，控制台显示乱码有时只是终端代码页问题，两者必须分清），已写进 `application.yml` 注释。
- **为什么 embedding 的 upsert 不用 `ON DUPLICATE KEY UPDATE`**：这是 MySQL 方言，`h2` demo profile 会直接抛语法错误，导致向量一条都没落库、语义检索静默失效。改成"先按主键删除再插入"的可移植写法，两种数据库都能跑。
- **为什么给 `ChatModel` 加方法而不是改签名**：Agent 需要"传 tools、拿 tool_calls、循环多轮"，而原来的 `complete(system, user)` 三样都做不到。但直接改签名会连带打断 `AnswerGenerator` 和 `SnippetEnricher`。所以把新能力做成 `default` 方法（并 `default supportsTools()=false`），**旧调用方一行都不用动**，53 个既有测试也全部保持通过。
- **为什么把 analyze/optimize 也注册成 Tool**：它们本可以藏在提示词里让模型直接输出分析结果。注册成工具后，**trace 里能明确看到 `analyze_snippet` 这一步**，行为可观测、可解释；同时在代码上用 `business/` 与 `capability/` 两个包把"操作业务数据"和"调用 AI 能力"分开，架构叙述更清晰。
- **为什么 WRITE 工具不放进 `ToolRegistry.invoke` 的自动执行路径**：如果只靠提示词约束"删除前要问用户"，模型完全可以不遵守。把拦截写在**循环控制流里**（`risk() == WRITE` 就中断），安全性就不依赖模型的自觉性——这是提示词约束和代码约束的本质区别。
- **Maven 本地仓库为什么指向工作区内**：`%USERPROFILE%\.m2\repository` 在工作区之外，受限环境下写入被拒，报出的 `AccessDeniedException` 极易被误判成网络问题。`build.ps1` 用 `-Dmaven.repo.local=./.m2-local` 绕开，同时保证验证可复现。

---

## 9. 已知限制

- 向量检索是**全量内存扫描**，适合万级以内；再大需要换向量数据库。
- 内置 `LocalHashEmbeddingModel` 是词法相似度（字符 bigram + 哈希），**不是真正的语义模型**，只保证 demo 可跑；中文短查询召回率明显低于真实 embedding 服务。
- Session 存在单机内存里，多实例部署需要改成 Redis 或粘性会话。
- 前端没有语法高亮（1.x 用 highlight.js）。加回来时注意：高亮库输出的是 HTML，必须对它做转义或使用其安全 API，否则又会引入 XSS。
- 没有做限流；`/api/ask` 与 `/api/agent/chat` 每次调用都会打模型，公开部署前需要加配额。
- **会话历史尚未持久化**：`history` 目前由前端持有并逐轮回传，刷新页面即丢失。已规划 `chat_session` / `chat_message` 两张表（P5）。注意 `agent_pending_action` 是**安全控制状态**，与聊天记录是两回事，不要合并。
- **更换 embedding 模型后需要重建索引**：向量维度不同就不可比较，检索层会**跳过宽度不匹配的向量并打印告警**（而不是拿它们算出无意义的相似度）。换模型（例如从内置本地向量化切到 `bge-m3`）后调用 `/api/ask/reindex` 重建即可。这条防护是补上的：在此之前"缓存里混着两种宽度的向量"是完全静默的，只会表现为排序莫名其妙。

---
