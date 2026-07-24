# OryxOS Website

OryxOS 的独立中英文项目官网。它是一个无框架、无运行时依赖的静态网站，不会打包进 `oryxos-boot`。

## 本地运行

在仓库根目录执行：

```bash
python -m http.server 8080 --directory website
```

然后访问：

```text
http://localhost:8080
```

也可以将 `website/` 目录直接部署到 GitHub Pages、Nginx、对象存储或其他静态网站托管服务。

## 目录结构

```text
website/
├── assets/
│   ├── architecture.png
│   ├── architecture.svg
│   └── oryxos-logo.svg
├── app.js
├── index.html
└── styles.css
```

页面支持：

- 中文和英文自动识别与手动切换；
- 桌面端、平板和移动端响应式布局；
- OryxOS 核心能力、架构、Agent 目录模型和路线图展示；
- 架构图放大、命令复制和 Agent 文件切换交互。
