# OryxOS Website

这是 OryxOS 的静态项目页，不包含运行时服务。

## 当前已实现的核心能力

- Java 21 / Spring Boot 3 单体运行时，九个 Maven 模块、一个可执行 JAR。
- DeepSeek、Kimi Provider 显式映射，以及 OryxOS 自有 ReAct 循环。
- Session、LLM 调用和 Tool 调用 SQLite 持久化与审计。
- 三层 `MemoryService` 统一门面，以及 Markdown、SQLite、Mem0 三档长期记忆后端。
- 九个内置 Tool、统一 `ToolRegistry`、白名单 Sandbox、MCP 与 Java Plugin 接入。
- Scheduler、十二个 CLI 命令和十个 `/api/v1` REST 操作；CLI、REST、Scheduler 汇入同一 `AgentService`。

## 扩展阶段能力

多租户、SSO、RBAC、完整审计查询后台、容器或 microVM 强隔离、集群高可用、跨节点 Agent 协作与向量检索不属于当前核心版本，不能把它们描述为已交付能力。

## 本地预览

```bash
python -m http.server 8080 --directory website
```

访问 `http://localhost:8080`。该目录可直接部署到 GitHub Pages、Nginx 或其他静态站点托管服务。
