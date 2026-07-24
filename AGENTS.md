# OryxOS 仓库协作指南

本文档供在本仓库中工作的 AI 编码助手和贡献者使用。开始任务前先阅读本文，再按需阅读 `docs/` 中的需求与技术方案。

## 1. 项目目标

OryxOS 是基于 Java 的、面向企业私有部署场景的 Agent OS。当前目标是先完成单机运行时内核，让多个 Agent 能在同一底座上可靠运行，并共享模型接入、ReAct、Memory、Tool、Web Service、Session 和审计能力。

当前阶段不追求一次性交付完整的分布式企业平台。多租户、SSO、RBAC、完整审计查询、容器或 microVM 沙箱、集群高可用及跨节点 Agent 协作属于后续阶段。

## 2. 当前仓库状态

- 当前仓库以需求和技术文档为主，代码工程尚未初始化。
- 不要假设 Maven 模块、Java 包、数据库脚本或测试已经存在；修改前必须检查实际文件。
- 初始化工程时应严格依据最新技术方案，不要从旧描述恢复 `.oryxos/profiles/*.yaml` 模型。
- 不要把文档中的规划描述成已经实现的能力。

## 3. 文档优先级

出现歧义时按以下顺序判断：

1. `docs/TechnicalSolution.md`：最新架构、模块职责和阶段边界。
2. `docs/DemandAnalysis.md`：功能范围、非功能指标和验收标准。
3. `docs/AiProgrammingGuide.md`：实施顺序和 AI 编程协作方式。
4. `docs/CliGuide.md`：CLI 的用户体验和命令契约。
5. `docs/oryxos.md`：产品定位和对外概述。
6. `docs/IndustryResearch.md`：行业背景和长期方向。
7. `docs/oryx-labs.md`：社区定位。

如果代码、需求和技术方案发生实质变化，应同步更新相关文档，不能只改代码。

## 4. 不可违背的技术决策

- 使用 JDK 21、Spring Boot 3.x 和 Maven 多模块工程。
- 核心工程保持单体应用、单可执行 JAR 部署。
- 使用 Spring MVC 配合 Java 21 virtual thread，核心链路采用同步阻塞模型。
- Spring AI/Spring AI Alibaba 只负责 Provider 协议适配、Function Calling 格式转换和 Tool Schema 生成。
- 禁用 Spring AI 自动 Tool 执行。Tool 调度只能由 OryxOS 的 `ReActLoop` 和 `ToolExecutor` 完成，避免重复调用。
- ReAct 循环由 OryxOS 自行实现，不能替换成外部 Agent 框架的黑盒循环。
- 多 Provider 必须维护 provider name 到 `ChatModel` 的显式映射，不能仅按 Bean 类型扫描推断。
- 内置 Tool、MCP Tool 和 Java Plugin Tool 统一适配为 `OryxTool`，注册到 `ToolRegistry`。
- Sandbox 采用接口先行设计。核心阶段实现 `WhitelistSandbox`，不能使用已废弃的 Java `SecurityManager`。
- Session、Tool 调用和 LLM 调用写入 `.oryxos/oryxos.db`。`tool_invocations` 与 `llm_calls` 从核心阶段开始落库。
- 长期记忆通过 `LongTermMemoryStore` 隔离存储实现，上层只能依赖接口。
- 密钥通过环境变量或独立安全配置注入，禁止明文写入 `AGENT.md`、MCP 配置、源码、测试数据或日志。

## 5. Agent 定义模型

OryxOS 使用“一目录一 Agent”，不使用独立的 Profile YAML：

```text
.oryxos/
├── agents/
│   └── <name>/
│       ├── AGENT.md
│       ├── skills/       # 可选，所属 Agent 的子指令
│       ├── scripts/      # 可选，确定性脚本
│       └── REFERENCE.md  # 可选，参考资料
├── memory/
│   └── MEMORY.md
├── logs/
├── mcp_servers.yaml
├── AGENTS.md
├── SOUL.md
├── USER.md
└── oryxos.db
```

- `AGENT.md` frontmatter 描述 Provider、Tool、Channel、通知目标、定时规则和循环参数。
- `AGENT.md` 正文描述 Agent 的任务、行为和边界，并注入 system prompt。
- `AgentLoader` 从 frontmatter 派生运行时 `Profile`；Profile 是内核对象，不是用户单独维护的文件。
- `skills/*.md` 只属于当前 Agent，由模型通过 `read_file` 按需读取；核心阶段没有全局 Skill 索引。
- `scripts/` 通过 Shell Tool 执行。允许执行脚本意味着信任 Agent 作者，不能把应用层白名单描述为强安全隔离。
- 已加载 Agent 的正文和子资源下一轮读取即可生效；新增、删除 Agent 或修改 frontmatter、调度规则，需要显式重载工作区或重启核心阶段服务。

根目录的本文件 `AGENTS.md` 是仓库开发规则，不是 OryxOS 运行时中的业务 Agent 定义。

## 6. 五大核心能力

1. **Provider**：统一接入 LLM，至少验证 DeepSeek 和 Kimi。
2. **ReAct**：驱动 Prompt 组装、LLM 调用、Tool 执行、结果回填和循环终止。
3. **Memory**：统一门面管理会话记忆和长期记忆；情景记忆属于扩展阶段。
4. **Tool**：核心阶段包含 9 个内置 Tool：
   - `read_file`
   - `write_file`
   - `list_dir`
   - `shell`
   - `http_get`
   - `http_post`
   - `save_memory`
   - `recall_memory`
   - `notify`
5. **Web Service**：通过 REST API 暴露会话、Agent、Profile、Memory、Tool 和系统状态。

CLI、Web Service 和 `AgentScheduler` 是三种触发源，必须汇入同一条 `AgentService` 链路，不能复制 Agent 执行逻辑。

## 7. Maven 模块边界

工程初始化时使用以下 9 个模块：

| 模块 | 主要职责 |
|------|----------|
| `oryxos-core` | 核心模型、接口、ReAct、Prompt、AgentService、AgentLoader、ContextLoader、Scheduler |
| `oryxos-provider` | ProviderService、ChatModel 显式映射、Function Calling 适配 |
| `oryxos-memory` | MemoryService、LongTermMemoryStore、MemoryTools |
| `oryxos-tool` | 内置 Tool、MCP Client、ToolRegistry、Sandbox、通知适配 |
| `oryxos-channel-cli` | CLI Channel |
| `oryxos-web` | Spring MVC、核心 REST API、统一异常响应、OpenAPI |
| `oryxos-storage` | SQLite、JPA Repository、Session 与审计持久化 |
| `oryxos-cli` | Picocli 主入口和 12 个子命令 |
| `oryxos-boot` | Spring Boot 主类、自动配置和最终依赖聚合 |

不要把内置 Tool、MCP Tool 和 Plugin Tool 拆成多个 Maven 模块。不要把 `AGENT.md` 或 Agent 目录建模成 Tool。

## 8. 实施顺序

按依赖推进，而不是同时铺开全部模块：

```text
Provider → ReAct → Memory 与 Tool → Web Service → 集成验收
```

- 优先建立最小可运行的纵向链路。
- 每完成一个阶段，先运行测试并验证可演示结果，再继续扩展。
- 新增抽象前先确认是否有至少两个真实实现或明确的扩展边界，避免无依据的过度设计。
- 不要擅自加入需求未要求的工作流编排、多 Agent 委托、SSE、RBAC、向量数据库或分布式基础设施。

## 9. 核心接口与行为约束

- `ReActLoop` 必须受 `max_iterations` 限制，默认 10。
- 每轮 LLM 响应和 Tool 结果必须按顺序追加到 Session。
- Prompt 应包含 `AGENT.md` 正文、Bootstrap、Memory、截断后的历史和当前 Agent 可用 Tool。
- Prompt 中必须提供当前日期时间，保证定时任务中的“今天”等表达有确定含义。
- Tool 执行前完成参数校验和 Sandbox 检查，成功与失败都写审计记录。
- 同一个定时任务在单进程中不能重叠执行；核心阶段使用任务级进程内锁，不得声称这是分布式锁。
- 文件路径必须标准化后再校验工作区边界，防止 `../` 路径穿越。
- HTTP 白名单必须校验解析后的 host，不能使用字符串前缀判断。
- Shell 白名单至少校验实际执行命令；不得把未经解析的整段命令直接视为安全。
- API 统一使用 `ApiResponse` 响应结构和全局异常处理。

## 10. 核心 API 与 CLI 契约

核心 REST API 共 10 个：

- `POST /api/v1/sessions`
- `POST /api/v1/sessions/{id}/messages`
- `GET /api/v1/sessions/{id}`
- `DELETE /api/v1/sessions/{id}`
- `POST /api/v1/agents/{name}/invoke`
- `GET /api/v1/profiles`
- `GET /api/v1/memory`
- `GET /api/v1/tools`
- `GET /api/v1/health`
- `GET /api/v1/info`

核心 CLI 共 12 个子命令：

- `init`
- `status`
- `chat`
- `serve`
- `gateway`
- `profile list`
- `profile create`
- `profile show`
- `profile delete`
- `provider list`
- `tool list`
- `session list`

`profile` 是为保持 CLI 契约而保留的命令组名称，实际管理的是 Agent 目录及其派生 Profile。

## 11. 测试与验收

新增或修改功能时，至少覆盖：

- 正常路径、输入校验、失败路径和边界条件。
- ReAct 无 Tool、单 Tool、多轮 Tool、达到最大迭代次数。
- Tool 成功、Sandbox 拒绝、可重试失败和不可重试失败。
- Session 持久化及跨重启恢复。
- API 状态码、统一响应结构和资源不存在场景。
- 路径穿越、命令白名单、HTTP 域名白名单等安全测试。

最终必须跑通三个端到端 Demo：

1. 每日天气：光杆 `AGENT.md`，定时查询天气、生成穿搭建议并通知。
2. 每日科技日报：`AGENT.md + skills/`，按需读取子指令、调用 MCP、结合 Memory 并通知。
3. 每日 GitHub 日报：`AGENT.md + scripts/`，通过 Shell Tool 执行脚本、处理输出并通知。

三个 Demo 都必须同时支持定时触发和人工补跑，并能够通过 API 查询 Session 与调用记录。

## 12. 修改工作流

执行任务时遵循以下顺序：

1. 阅读相关需求、技术方案和现有实现。
2. 明确改动属于核心阶段还是扩展阶段。
3. 保持模块依赖方向，不绕过接口直接访问实现。
4. 先修改最小必要范围，再补测试和文档。
5. 运行与改动范围匹配的测试；无法运行时明确说明原因。
6. 检查凭证、日志、路径、并发和审计风险。
7. 总结改了什么、验证了什么、仍有哪些限制。

不要覆盖无关的用户改动，不要为了“整理”而重写与任务无关的文件。
