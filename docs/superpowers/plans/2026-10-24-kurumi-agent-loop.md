# Kurumi Agent Loop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 mcAimod 从单段脚本响应改为可接收新消息、观察工具结果、自主继续或结束的循环式 Agent。

**Architecture:** 客户端 Agent 会话持有消息队列、任务上下文、阶段变量和预算；伪代码解释器运行到 `observe()` 或结束时发出阶段事件；Controller 将事件与新消息合并送给模型。历史上下文按最近对话、任务摘要、结果索引分层提供。

**Tech Stack:** Java, NeoForge 26.2, Gson, Gradle/JUnit.

**Spec:** `docs/superpowers/specs/2026-10-24-kurumi-agent-loop-design.md`

## Global Constraints

- 保留 `/kurumi stop`、主人紧急停止的实时性和游戏线程动作限制。
- 不允许任意代码执行；保持脚本白名单、主人动作权限及现有桥接。
- 不设变量个数上限，但保留防内存耗尽保护；大数据按页/索引返回。
- 完成与中断状态严格区分。

## Review Focus

- 工具还未完成时收到新消息，不能丢失也不能并行破坏当前动作。
- `observe()` 返回很长的箱子信息，不能无声截断导致模型误判。
- `finish` 到达与新消息入队竞态，队列消息不能丢。
- 任务取消后旧模型回调不能复活任务。
- 高速重复消息、异常模型响应和长循环必须可停止且受资源预算保护。

---

### Task 1: Agent 协议与上下文状态

**Files:** 新建 `KurumiAgentSession.java`, `KurumiAgentContext.java`；修改 `KurumiDecisionParser.java`；增加对应 JUnit 测试。

**Interfaces:** 会话提供 `enqueue(sender,message)`、`drainMessages()`、`recordObservation()`、`buildModelContext()`、`finishIfIdle()`；协议明确 SCRIPT / FINISH 两类决策。

- [ ] 先测试消息按序入队、上下文压缩、finish 与消息竞态及协议校验，并确认失败。
- [ ] 实现数据模型、分层上下文与决策协议。
- [ ] 测试转绿并提交。

### Task 2: 脚本观察和变量生命周期

**Files:** `KurumiScriptParser.java`, `KurumiTaskPlanner.java`, `KurumiPseudoExecutor.java`，及相关单测。

**Interfaces:** `observe(value)` 产生阶段让出事件；运行状态可导出/恢复 JSON 变量，单任务跨阶段复用；完成或取消提供结构化状态。

- [ ] 写失败测试覆盖观察语法、变量超过16个、变量跨阶段、长结果分页、取消状态。
- [ ] 增加 `observe` 语法与暂停/结果通道，移除个数上限，保留总内存预算。
- [ ] 测试转绿并提交。

### Task 3: Controller Agent 主循环和消息排队

**Files:** `KurumiClientController.java`，相关状态/Controller 单测。

**Interfaces:** 新消息不被忙碌条件丢弃；每个执行阶段将结果与新消息交给模型；模型明确 `finish` 才结束；紧急停止作废旧 generation。

- [ ] 写失败测试覆盖消息队列、阶段完成非任务完成、finish、异常和停止。
- [ ] 接入会话、脚本阶段回调及系统提示词，替代6轮普通完成逻辑。
- [ ] 测试转绿并提交。

### Task 4: Web 监测、提示词和全套验证

**Files:** `KurumiWebMonitor.java`、`README.md`、测试文件。

- [ ] 写失败测试覆盖 Agent 阶段状态、排队消息和长输出日志。
- [ ] 面板显示观察/计划/消息队列/完成或中断，更新示例与操作说明。
- [ ] 运行 `./gradlew test` 和 `./gradlew build`，检查兼容性与所有失败。
- [ ] Review diff，提交并创建 PR，不直接合并。
