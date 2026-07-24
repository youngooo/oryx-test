// OryxOS standalone website interactions and translations.
const messages = {
    zh: {
        skip:"跳到主要内容",menu:"打开菜单",navCapabilities:"核心能力",navArchitecture:"架构",navQuickstart:"快速开始",navRoadmap:"路线图",
        heroEyebrow:"开源 · Java 原生 · 私有可控",heroTitleLead:"让每一个 Agent",heroTitleAccent:"可靠地运行起来",
        heroCopy:"OryxOS 是面向企业私有部署的 AI Agent 运行底座。一个自足目录定义一个 Agent，一套内核统一管理模型、推理、记忆、工具、会话与审计。",
        getStarted:"快速开始",viewGithub:"查看 GitHub",runtimeReady:"就绪",runtimeTrigger:"统一触发",runtimeCapability:"受控能力",
        metricModules:"Maven 模块",metricJar:"可执行 JAR",metricTriggers:"统一触发入口",metricJava:"Java 基线",
        capKicker:"核心运行时",capTitle:"五大能力，一个运行内核",capCopy:"业务 Agent 只描述任务和边界；重复、复杂的运行基础设施由 OryxOS 统一提供。",
        providerCopy:"通过显式映射接入 DeepSeek、Kimi 与 OpenAI-compatible 模型，保持模型选择清晰、可控。",
        reactCopy:"自研 Reason → Act → Observe 循环，统一完成上下文组装、模型调用和工具结果回填。",
        memoryCopy:"以统一门面管理会话记忆和长期记忆，让 Agent 跨会话保留真正有价值的上下文。",
        toolCopy:"内置 Tool、MCP Tool 与 Java Tool 统一注册，并在白名单 Sandbox 中执行和审计。",
        webCopy:"通过稳定 REST API 把 Agent 能力接入业务系统，与 CLI 和定时任务复用同一执行链路。",
        archKicker:"系统化设计",archTitle:"统一入口，受控执行",archCopy:"CLI、Web Service 与定时任务进入同一个 AgentService。ReAct 引擎按需调度 Provider、Memory 和 Tool，每一次关键调用都可被记录和审计。",
        archItem1:"同步阻塞核心链路，配合 Java 21 虚拟线程",archItem2:"一目录一 Agent，定义可读、可维护、可版本管理",archItem3:"路径、命令和域名白名单守住工具执行边界",viewArchitecture:"查看完整架构图",
        agentKicker:"一目录一 Agent",agentTitle:"Agent 应该像代码一样清晰",agentCopy:"无需为每个 Agent 重写后端服务。一个自足目录承载任务定义、技能、脚本和参考资料。",executionFlow:"执行链路",
        quickKicker:"快速开始",quickTitle:"从一个 JAR 开始",quickCopy:"当前版本是可运行的工程骨架。使用 JDK 21 和 Maven 构建，即可启动官网及基础健康检查接口。",
        quickNotice:"OryxOS 处于早期开发阶段，核心 Agent 运行能力仍在持续实现，暂不建议用于生产环境。",copy:"复制",copied:"已复制",
        roadmapKicker:"路线图",roadmapTitle:"从骨架到可靠运行时",roadmapCopy:"项目按纵向链路推进，每一阶段先形成可运行、可测试的结果。",
        roadmapDone:"已完成",roadmapCurrent:"当前阶段",roadmapNext:"下一阶段",roadmapLater:"随后",roadmapFoundation:"工程基础",
        roadmapFoundationCopy:"九模块 Maven 工程、Boot 与 CLI 主入口、官网和基础 API。",roadmapRuntimeCopy:"打通模型接入、自研循环和最小 Agent 执行链路。",
        roadmapCapabilityCopy:"加入持久记忆、统一工具注册、Sandbox 和调用审计。",roadmapIntegrationCopy:"完善 REST API，跑通三个端到端 Agent 示例。",
        ctaKicker:"一起构建",ctaTitle:"一起构建可信赖的 Agent OS",ctaCopy:"查看代码、阅读设计，或者从一个 Issue 开始参与 OryxOS。",exploreGithub:"前往 GitHub",
        footerCopy:"Java 原生、私有可控的 AI Agent OS。",footerProject:"项目",footerResources:"资源",footerCommunity:"社区",readme:"README",docs:"设计文档",fullArchitecture:"OryxOS 完整架构"
    },
    en: {
        skip:"Skip to main content",menu:"Open menu",navCapabilities:"Capabilities",navArchitecture:"Architecture",navQuickstart:"Quick Start",navRoadmap:"Roadmap",
        heroEyebrow:"Open Source · Java-native · Private by Design",heroTitleLead:"Make every Agent",heroTitleAccent:"run with confidence",
        heroCopy:"OryxOS is an AI Agent runtime for private enterprise deployments. One self-contained directory defines an Agent; one kernel manages models, reasoning, memory, tools, sessions, and audit.",
        getStarted:"Get Started",viewGithub:"View on GitHub",runtimeReady:"READY",runtimeTrigger:"Unified Triggers",runtimeCapability:"Governed Runtime",
        metricModules:"Maven Modules",metricJar:"Executable JAR",metricTriggers:"Unified Triggers",metricJava:"Java Baseline",
        capKicker:"CORE RUNTIME",capTitle:"Five capabilities. One runtime.",capCopy:"Business Agents describe their tasks and boundaries. OryxOS provides the complex, repeatable runtime foundation.",
        providerCopy:"Connect DeepSeek, Kimi, and OpenAI-compatible models through explicit mappings that keep model selection visible and controlled.",
        reactCopy:"An in-house Reason → Act → Observe loop assembles context, invokes models, and feeds tool results back into the session.",
        memoryCopy:"A unified facade manages conversational and long-term memory, preserving the context that matters across sessions.",
        toolCopy:"Built-in, MCP, and Java tools share one registry, with execution and audit governed by an allowlist sandbox.",
        webCopy:"Stable REST APIs connect Agent capabilities to business systems while sharing the same execution path as CLI and schedules.",
        archKicker:"DESIGNED AS A SYSTEM",archTitle:"One entry. Controlled execution.",archCopy:"CLI, Web Service, and schedules converge on AgentService. The ReAct engine orchestrates Provider, Memory, and Tool as needed, recording every critical call for audit.",
        archItem1:"A synchronous core execution path powered by Java 21 virtual threads",archItem2:"One directory per Agent — readable, maintainable, and versionable",archItem3:"Path, command, and host allowlists protect tool execution boundaries",viewArchitecture:"View full architecture",
        agentKicker:"ONE DIRECTORY, ONE AGENT",agentTitle:"Agents should be as clear as code",agentCopy:"No custom backend for every Agent. A self-contained directory holds its mission, skills, scripts, and references.",executionFlow:"EXECUTION FLOW",
        quickKicker:"QUICK START",quickTitle:"Start with one JAR",quickCopy:"The current release is a runnable project foundation. Build with JDK 21 and Maven to launch the homepage and baseline health API.",
        quickNotice:"OryxOS is in early development. Core Agent runtime capabilities are still being implemented and are not yet recommended for production.",copy:"Copy",copied:"Copied",
        roadmapKicker:"ROADMAP",roadmapTitle:"From foundation to reliable runtime",roadmapCopy:"Development follows a vertical path, producing a runnable and testable outcome at every stage.",
        roadmapDone:"COMPLETED",roadmapCurrent:"CURRENT",roadmapNext:"NEXT",roadmapLater:"THEN",roadmapFoundation:"Foundation",
        roadmapFoundationCopy:"Nine Maven modules, Boot and CLI entry points, the homepage, and baseline APIs.",roadmapRuntimeCopy:"Connect model providers, the in-house loop, and a minimal Agent execution path.",
        roadmapCapabilityCopy:"Add persistent memory, a unified tool registry, sandboxing, and invocation audit.",roadmapIntegrationCopy:"Complete the REST API and validate three end-to-end Agent demos.",
        ctaKicker:"BUILD WITH US",ctaTitle:"Build a dependable Agent OS with us",ctaCopy:"Explore the code, read the design, or start contributing with an Issue.",exploreGithub:"Explore GitHub",
        footerCopy:"A Java-native, private and controllable AI Agent OS.",footerProject:"Project",footerResources:"Resources",footerCommunity:"Community",readme:"README",docs:"Design Docs",fullArchitecture:"OryxOS Full Architecture"
    }
};

const root=document.documentElement;
const header=document.querySelector(".site-header");
const menuButton=document.querySelector(".menu-toggle");
const navLinks=document.querySelector(".nav-links");
const languageButton=document.querySelector(".language-toggle");
let language=localStorage.getItem("oryxos-language")||(navigator.language.startsWith("zh")?"zh":"en");

function applyLanguage(next){
    language=next;
    root.lang=next==="zh"?"zh-CN":"en";
    document.title=next==="zh"?"OryxOS — AI Agent 操作系统":"OryxOS — AI Agent OS";
    document.querySelectorAll("[data-i18n]").forEach(element=>{const value=messages[next][element.dataset.i18n];if(value)element.textContent=value});
    languageButton.innerHTML=next==="zh"?'<span class="language-active">中</span><span class="language-divider">/</span><span>EN</span>':'<span>中</span><span class="language-divider">/</span><span class="language-active">EN</span>';
    localStorage.setItem("oryxos-language",next);
}
applyLanguage(language);
languageButton.addEventListener("click",()=>applyLanguage(language==="zh"?"en":"zh"));

function closeMenu(){navLinks.classList.remove("open");menuButton.setAttribute("aria-expanded","false");document.body.classList.remove("menu-open")}
menuButton.addEventListener("click",()=>{const open=navLinks.classList.toggle("open");menuButton.setAttribute("aria-expanded",String(open));document.body.classList.toggle("menu-open",open)});
navLinks.querySelectorAll("a").forEach(link=>link.addEventListener("click",closeMenu));
function syncHeader(){header.classList.toggle("scrolled",window.scrollY>24)}
syncHeader();window.addEventListener("scroll",syncHeader,{passive:true});

const observer=new IntersectionObserver(entries=>entries.forEach(entry=>{if(entry.isIntersecting){entry.target.classList.add("visible");observer.unobserve(entry.target)}}),{threshold:.12});
document.querySelectorAll(".reveal").forEach((element,index)=>{element.style.transitionDelay=`${Math.min(index%5,3)*60}ms`;observer.observe(element)});

const snippets={
    agent:["AGENT.md",'<span class="token-muted">---</span>\n<span class="token-key">name:</span> daily-weather\n<span class="token-key">provider:</span> deepseek\n<span class="token-key">tools:</span> [http_get, notify]\n<span class="token-key">schedule:</span> <span class="token-string">"0 30 7 * * *"</span>\n<span class="token-muted">---</span>\n\n<span class="token-title"># Daily Weather Agent</span>\n\nCheck today\'s weather, create a practical\noutfit suggestion, then notify the user.'],
    skill:["skills/clothing.md",'<span class="token-title"># Outfit Guidance</span>\n\nConsider temperature, wind, rain, and UV.\nPrefer practical layers and concise advice.'],
    script:["scripts/notify.sh",'<span class="token-muted">#!/usr/bin/env bash</span>\n\nMESSAGE=<span class="token-string">"$1"</span>\ncurl -X POST <span class="token-string">"$WEBHOOK"</span> \\\n  -d <span class="token-string">"{\\"text\\":\\"$MESSAGE\\"}"</span>'],
    reference:["REFERENCE.md",'<span class="token-title"># Weather Reference</span>\n\n- Use the configured city timezone.\n- Include severe-weather warnings first.\n- Never invent unavailable measurements.']
};
document.querySelectorAll(".tree-file").forEach(button=>button.addEventListener("click",()=>{document.querySelector(".tree-file.active").classList.remove("active");button.classList.add("active");const[name,code]=snippets[button.dataset.file];document.querySelector("#active-file").textContent=name;document.querySelector("#agent-code").innerHTML=code}));

const copyButton=document.querySelector("[data-copy]");
copyButton.addEventListener("click",async()=>{const commands="git clone https://github.com/youngooo/oryx-test.git\ncd oryx-test\nmvn clean package\njava -jar oryxos-boot/target/oryxos-boot-0.1.0-SNAPSHOT.jar";try{await navigator.clipboard.writeText(commands);copyButton.querySelector("span").textContent=messages[language].copied;setTimeout(()=>copyButton.querySelector("span").textContent=messages[language].copy,1500)}catch(_){copyButton.querySelector("span").textContent=messages[language].copy}});

const dialog=document.querySelector(".architecture-dialog");
document.querySelectorAll("[data-open-architecture]").forEach(button=>button.addEventListener("click",()=>dialog.showModal()));
document.querySelector("[data-close-dialog]").addEventListener("click",()=>dialog.close());
dialog.addEventListener("click",event=>{if(event.target===dialog)dialog.close()});
