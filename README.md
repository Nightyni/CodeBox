# CodeBox

### AI 代码知识库与智能助手

CodeBox 是一个面向开发者的 AI 代码知识库与智能助手。

通过 **LLM、RAG、Agent、Tool Calling** 等能力，将传统代码知识库与 AI 结合，让用户可以通过自然语言完成代码搜索、分析、优化以及代码库管理。

---

## ✨ 项目简介

在日常开发过程中，代码、工具方法和解决方案会不断积累，但当需要再次使用时，往往需要依赖关键词搜索或者凭记忆查找。

CodeBox 希望让这个过程变得更加自然：

> **把代码存下来，也让 AI 帮你找到、理解和优化它。**

用户可以将常用代码保存到知识库，并通过自然语言与 AI 交互。

例如：

```text
帮我找一下和用户登录有关的代码
```

```text
分析 Calculate Order Total 有什么问题
```

```text
帮我优化 Calculate Order Total
```

AI 会根据用户需求理解任务，并选择合适的能力完成操作。

---

## 🚀 核心功能

### 📚 代码知识库

提供代码片段的统一管理能力：

- 代码保存
- 分类与标签
- 代码搜索
- 代码详情
- AI 检索

将日常开发中的代码经验沉淀为个人知识库。

### 🔍 AI 智能检索

用户不需要记住准确的代码名称或关键词，只需要描述自己的需求。

例如：

```text
找一下和用户登录有关的代码
```

系统会通过 RAG 检索知识库中的相关代码，并将检索结果提供给 AI。

```text
用户描述需求
      ↓
理解用户意图
      ↓
检索代码知识库
      ↓
返回相关代码
```

### 🤖 AI 代码分析

针对知识库中的代码，可以直接让 AI 进行分析。

例如：

```text
分析 Calculate Order Total 有什么问题
```

AI 会结合实际代码，从代码逻辑、可读性、健壮性等方面进行分析。

### ✨ AI 代码优化

用户可以直接让 AI 对知识库中的代码进行优化。

```text
优化 Calculate Order Total
```

系统会生成优化后的代码，并通过 Diff 展示修改前后的差异。

相比直接返回一段新的代码，用户可以更加直观地看到 AI 修改了什么。

### 🧠 Agent

CodeBox 不只是：

```text
用户 → LLM → 回答
```

而是通过 Agent 将 AI 与实际业务能力连接起来。

```text
用户
 ↓
Agent
 ↓
理解任务
 ↓
选择 Tool
 ↓
执行操作
 ↓
获取结果
 ↓
继续判断 / 返回结果
```

目前 Agent 支持：

- 代码搜索
- 代码查询
- 代码分析
- 代码优化
- 代码创建
- 代码修改
- 代码删除

### 🔐 人机协同

对于创建、修改、删除等会影响数据的操作，Agent 不会直接执行，而是先生成待确认操作。

```text
用户请求
   ↓
Agent 判断
   ↓
生成操作
   ↓
用户确认
   ↓
执行操作
```

在保留 AI 自动化能力的同时，让用户能够控制关键的数据修改。

---

## 🧩 AI 设计

CodeBox 将多个 AI 能力组合到一个完整的业务流程中：

```text
                       CodeBox

                          │
             ┌────────────┼────────────┐
             ↓            ↓            ↓
            RAG         Agent     Tool Calling
             │            │            │
             └────────────┼────────────┘
                          ↓
                         LLM
                          │
                          ↓
                     代码知识库
```

### RAG

让 AI 能够基于代码知识库中的实际内容进行检索和回答。

基本流程：

```text
代码片段
   ↓
Embedding
   ↓
向量检索
   ↓
召回相关代码
   ↓
LLM
   ↓
生成回答
```

### Agent

负责理解用户任务，并根据任务决定下一步需要执行什么操作。

例如：

```text
“帮我找一下用户登录代码”
        ↓
     搜索代码

“分析第一个代码”
        ↓
     查询代码
        ↓
     分析代码

“帮我优化一下”
        ↓
     优化代码
```

### Tool Calling

将 LLM 与实际业务能力连接起来。

Agent 可以通过 Tool 调用知识库中的实际业务能力，而不是单纯依赖模型生成文本。

### Human-in-the-loop

对于写入数据库的操作，引入用户确认机制。

AI 负责理解和提出操作，用户负责最终确认。

---

## 🎯 产品设计

CodeBox 的核心思路不是简单增加一个 AI 聊天窗口，而是让 AI 真正参与到代码知识管理的业务流程中。

传统代码知识库：

```text
保存
 ↓
搜索
 ↓
查看
```

CodeBox：

```text
描述需求
   ↓
AI 理解
   ↓
知识检索
   ↓
分析 / 优化 / 操作
   ↓
返回结果
```

因此，CodeBox 希望让代码知识库从一个单纯的存储和查询工具，逐渐变成一个可以与用户协作的智能助手。

---

## 🏗 技术架构

```text
                    Vue 3
                      │
                      ↓
                Spring Boot
                      │
          ┌───────────┼───────────┐
          ↓           ↓           ↓
        MySQL        RAG        Agent
                      │           │
                      │      Tool Calling
                      │           │
                      └─────┬─────┘
                            ↓
                           LLM
                            │
                            ↓
                      DeepSeek API
```

---

## 🛠 技术栈

### 前端

- Vue 3
- Vite

### 后端

- Java 21
- Spring Boot
- MyBatis
- MySQL

### AI

- DeepSeek API
- LLM
- RAG
- Embedding
- Tool Calling
- Agent Loop
- Human-in-the-loop

---

## 🚀 快速开始

### 环境要求

```text
Java 21+
Node.js
MySQL 8+
```

### 1. 获取项目

```bash
git clone https://github.com/Nightyni/CodeBox.git
cd CodeBox
```

### 2. 配置数据库

项目使用 MySQL。

在：

```text
config/application-local.yml
```

中配置数据库连接信息。

例如：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/codebox?useSSL=false&serverTimezone=Asia/Shanghai&characterEncoding=utf8&allowPublicKeyRetrieval=true
    username: root
    password: 你的MySQL密码
```

### 3. 配置 AI

CodeBox 的 AI 分析、AI 优化、Agent 等功能需要 DeepSeek API。

可以在 `config/application-local.yml` 中配置：

```yaml
codebox:
  llm:
    api-key: 你的 DeepSeek API Key
```

### 4. 启动后端

```bash
mvn clean package
```

```bash
java -jar target/codebox.jar --spring.config.additional-location=optional:./config/application-local.yml
```

后端默认运行：

```text
http://localhost:8080
```

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行：

```text
http://localhost:5173
```
### 6.脚本
```bash
start-codebox.bat
```

---

## 👤 默认账号

```text
账号：demo
密码：123456
```
---

## 📌 项目定位

CodeBox 是一次 **AI + 传统业务系统** 的实践。

项目重点探索：

- 如何让 LLM 理解真实业务需求
- 如何通过 RAG 使用业务知识
- 如何让 Agent 调用实际业务能力
- 如何让 AI 参与真实的数据操作流程
- 如何通过 Human-in-the-loop 控制 AI 的关键操作

项目最终希望实现：

> **让 AI 不只是回答问题，而是真正参与用户的工作流程。**

---

## 👨‍💻 项目作者

**Nightyni**

GitHub：  
https://github.com/Nightyni