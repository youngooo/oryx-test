# OryxOS CLI 使用文档

OryxOS 打包为一个可执行 JAR，所有操作都通过 `oryxos` 命令的子命令完成：在终端里跟 Agent 对话、启动服务、查询配置和状态。本文覆盖核心阶段的 12 个子命令。

> CLI 是消息进出的门，不是干活的人——它不想、不调模型、不执行工具，这些全在引擎（ReAct 循环）里。

---

## 1. 构建与运行

```bash
# 构建 fat jar（仓库根目录）
mvn -pl oryxos-boot -am package -DskipTests

# 运行（下文所有 oryxos 命令均指这个别名）
alias oryxos='java -jar /path/to/oryxos/oryxos-boot/target/oryxos-boot-1.0.0-SNAPSHOT.jar'

oryxos --help      # 总览
oryxos --version   # 版本与 JVM/OS 信息
```

**运行目录约定**：CLI 在**当前目录**寻找 `.oryxos/` 工作区，SQLite 数据库固定为 `.oryxos/oryxos.db`。请固定在同一个项目目录下运行各命令，否则会连接到另一个工作区。

---

## 2. 快速开始（5 分钟）

```bash
mkdir my-agent && cd my-agent
oryxos init                        # ① 初始化工作区
oryxos profile create weather      # ② 创建一个 Agent
oryxos profile list                # ③ 确认它在
export DEEPSEEK_API_KEY=sk-xxx     # ④ 配置模型凭证（环境变量，绝不明文写文件）
oryxos chat --profile weather      # ⑤ 开聊
```

```text
已连接 Agent [weather]，输入 /quit 退出。
> 今天天气怎么样？
（Agent 回复……）
> /quit
再见。
```

隔天再进来，`oryxos chat --profile weather` 会**续用同一条会话**，上次聊过什么还在。

---

## 3. 命令总览

| 命令 | 类型 | 作用 |
|------|------|------|
| `oryxos init` | 轻 | 初始化 `.oryxos/` 工作区 |
| `oryxos status` | 轻 | 查看工作区与数据文件状态 |
| `oryxos chat [--profile <name>]` | **重** | 交互式对话（默认 profile：`default`） |
| `oryxos serve [--port 8080]` | **重** | 启动包含核心 REST 端点的 HTTP API 服务 |
| `oryxos gateway` | **重** | 守护进程模式（多 Channel 挂载属扩展阶段） |
| `oryxos profile list` | 轻 | 列出全部 Profile |
| `oryxos profile create <name>` | 轻 | 创建 Profile（最小模板，不覆盖已有） |
| `oryxos profile show <name>` | 轻 | 查看 Profile 内容 |
| `oryxos profile delete <name>` | 轻 | 删除 Profile |
| `oryxos provider list` | 轻 | 列出实例声明的 Provider |
| `oryxos tool list` | 轻 | 列出可用工具（20 节起为实时清单） |
| `oryxos session list` | 轻 | 列出会话概览 |

**轻/重的区别**：轻命令直接读写文件或只读查库，**不启动 Spring**、秒级返回（实测约 0.35s）；重命令要调模型、跑引擎，才付出 2~4 秒的完整运行时启动代价。判断标准就一条：这个命令要不要调模型/跑引擎。

所有命令都支持 `--help`；打错命令会得到统一报错和纠正建议（如 `Did you mean: oryxos session or oryxos serve?`），不会抛堆栈。

---

## 4. 逐命令说明

### 4.1 init——初始化工作区

```bash
oryxos init
```

在当前目录创建：

```text
.oryxos/
├── agents/              # 每个子目录定义一个 Agent
│   └── default/
│       └── AGENT.md     # frontmatter 是运行配置，正文是任务指令
├── memory/
│   └── MEMORY.md        # 长期记忆
├── logs/
├── mcp_servers.yaml     # MCP server 配置
├── AGENTS.md            # Bootstrap：项目级 Agent 行为说明
├── SOUL.md              # Bootstrap：默认 Agent 人格
├── USER.md              # Bootstrap：用户偏好（只读）
└── oryxos.db            # Session、调用审计等关系型数据
```

**幂等**：重复运行不覆盖任何已有文件，放心多敲。

### 4.2 status——看一眼状态

```bash
oryxos status
```

输出工作区是否初始化、Profile/Skill 数量、SQLite 库是否已创建。排查"为什么 chat 不认我的 Agent"先看这里。

### 4.3 chat——交互式对话（核心命令）

```bash
oryxos chat                    # 用 default Agent
oryxos chat --profile weather  # 用指定 Agent
```

- 每行输入交给 ReAct 引擎处理，回复打印到终端；
- **`/quit` 退出**（前后空白不影响）；Ctrl-D（EOF）等同退出；空行自动跳过；
- 会话身份 = `渠道:用户:Agent` 三元组（渠道固定 `cli`，用户取系统用户名）。同一身份**永远续用同一条会话**，跨重启历史不丢；
- 前置条件：Profile 存在、对应 Provider 的环境变量已配置（见 §5），否则启动即点名报错、不进入对话。

### 4.4 serve / gateway——常驻模式

```bash
oryxos serve --port 8080   # 启动核心 REST API
oryxos gateway             # 守护进程（多 Channel 挂载属扩展阶段）
```

三种运行模式（chat/serve/gateway）共享同一套 Agent 定义、派生 Profile 和会话存储，差异只是消息从哪里进入。Ctrl-C 退出。

### 4.5 profile 四件——Agent 管理

```bash
oryxos profile create ops-agent   # 创建 .oryxos/agents/ops-agent/AGENT.md
oryxos profile show ops-agent     # 打印 AGENT.md 及派生运行配置
oryxos profile list               # 列出全部
oryxos profile delete ops-agent   # 删除（不存在则报错点名）
```

`profile create` 是兼容保留的 CLI 名称，实际创建的是一个 Agent 目录。模板的 frontmatter 包含 provider、tools、mcp_servers、channels、notify_channels、schedules、bootstrap 和 settings 等运行字段，正文描述 Agent 的任务。正文和上下文文件在下一轮对话生效；新增目录或修改调度配置需要重载工作区或重启核心阶段服务。

### 4.6 provider list / tool list / session list——三张清单

```bash
oryxos provider list   # 实例声明的 Provider（name + base-url，读打包配置）
oryxos tool list       # 可用工具清单（20 节 ToolRegistry 就位后为实时注册表）
oryxos session list    # 会话概览：session_id / profile / status / last_active_at
```

`session list` 直连当前工作区的 `.oryxos/oryxos.db` 只读查询；数据库还没创建时提示“暂无会话”。

---

## 5. 配置与凭证

**凭证只走环境变量，绝不明文写进任何文件**（宪法约束）：

```bash
export DEEPSEEK_API_KEY=sk-xxx    # Provider 凭证
```

- 实例级 Provider 清单声明在打包配置（`application.yml` 的 `oryxos.providers` 段）；Profile 里只写 `provider.name` + `model` 引用它；
- 环境变量缺失时，重命令启动会**点名报错**（`provider deepseek 的 api-key 未配置或环境变量未解析`），不会静默跑过。

---

## 6. 会话机制（为什么"聊过的都记得"）

- 会话身份由三元组 `渠道:用户:Agent` 唯一决定，拼接只发生在系统内部一处——CLI 进来的是 `cli:<你的系统用户名>:<profile>`，Web 进来是 `web:...`，互不串扰；
- 对话历史（用户消息 / 模型响应 / 工具结果）按序累积，整体存入 SQLite 的 `sessions` 表；
- 进程重启、换运行模式，同一三元组进来都能拿回完整历史；
- 每次调模型只带最近 N 轮历史（Profile 的 `max_history_turns`，默认 20），上下文不会无限膨胀。

---

## 7. 常见问题

| 现象 | 原因与处理 |
|------|-----------|
| chat 启动报 `api-key 未配置或环境变量未解析` | 对应 Provider 的环境变量没设，`export DEEPSEEK_API_KEY=...` 后重试 |
| chat 报 Profile 不存在 | `oryxos profile list` 确认名字；没有就 `profile create`；注意运行目录是否对 |
| `session list` 显示暂无会话，但明明聊过 | 换了项目目录——数据库在当前工作区的 `.oryxos/oryxos.db`，请回到原项目目录 |
| 轻命令也很慢 | 确认跑的不是 chat/serve/gateway；轻命令不启动 Spring，正常应亚秒返回 |
| 启动日志想确认存储装配 | chat 启动日志应有 `Found 3 JPA repository interfaces`（0 说明装配残缺，属 bug） |
| 想换模型 | 修改对应 `AGENT.md` frontmatter 的 `provider.model`，然后重载工作区或重启服务 |

---

## 8. 能力边界（核心阶段）

- CLI、核心 REST API、定时任务均进入同一条 `AgentService` 链路；
- 文件、Shell、HTTP、Memory 与通知工具由统一 `ToolRegistry` 管理；
- 企业微信、飞书、钉钉、Slack 等完整入站 IM Channel 放在扩展阶段；
- API 认证、SSE、WebSocket、RBAC、限流以及容器级沙箱放在扩展阶段。

---

*技术细节见 `docs/TechnicalSolution.md` §8.4、§8.6、§8.7 和 §9.2。*
