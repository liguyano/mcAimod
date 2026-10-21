# mcAimod V0.1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Minecraft 26.2 + NeoForge 中实现玩家状态感知、连续多轮聊天和 OpenAI/DeepSeek/Ollama 可切换的首版 AI Companion。

**Architecture:** NeoForge 服务端命令收集当前玩家世界快照，构建提示词与玩家隔离的历史消息，通过异步的 Chat Completions 适配器调用配置的模型；完成后安全返回服务器主线程。所有 Provider、会话和上下文纯 Java 类型都与 NeoForge 事件/命令层隔离，以便未来 NPC 复用。

**Tech Stack:** Java 25, NeoForge 26.2, ModDevGradle 2.0.148, Gradle Wrapper (official MDK), Java HttpClient, Gson, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-10-21-mcaimod-v0.1-design.md`

## Global Constraints

- Minecraft `26.2`; NeoForge `26.2.0.93` as official template baseline; Java toolchain `25`.
- Mod ID `mcaimod`; Java package `com.liguyano.mcaimod`; version `0.1.0`.
- Support integrated single-player server and dedicated NeoForge server.
- Default nearby entity scan radius `16` blocks, max `12` entries, loaded entities only.
- Per-player history `10` complete user/assistant turns, in-memory, UUID keyed, clearable.
- Never block game Tick with HTTP I/O; never access mutable Minecraft world objects from worker threads.
- API credentials stored outside source/JAR and excluded from logs; only `https://` or trusted local loopback `http://` endpoints.
- No NPC entity, custom GUI, audio, autonomous actions, arbitrary tool execution, streaming, or persistent chat history in V0.1.

## Review Focus

1. Empty/whitespace-only or excessive (more than `1000` characters) prompt must be rejected before I/O — Task 6 test.
2. HTTP `401`, `429`, malformed JSON, non-2xx, and timeout must return safe error messages and never append history — Tasks 4 and 6 tests.
3. Two UUIDs speaking concurrently must not mix chat history or active model selection — Task 3 test.
4. A disconnect or a second request during first request must not deliver stale/duplicate answers — Task 6 test.
5. Inapplicable weather/biome fields in alternative dimensions, unloaded entities, and large nearby crowds must be bounded/marked unknown — Task 2 test.

---

### Task 1: Initialize official NeoForge MDK and test framework

**Files:**
- Create: `build.gradle`, `settings.gradle`, `gradle.properties`, `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `.gitignore`, `.gitattributes`, `src/main/templates/META-INF/neoforge.mods.toml`.
- Create: `src/main/java/com/liguyano/mcaimod/AICompanionMod.java`.
- Create: `src/test/java/com/liguyano/mcaimod/SmokeTest.java`.

**Interfaces:**
- Produces: `AICompanionMod.MOD_ID = "mcaimod"`, NeoForge @Mod entrypoint and Gradle test task.
- Source: `NeoForgeMDKs/MDK-26.2-ModDevGradle`, adapting `examplemod` to `mcaimod`. Keep the official Gradle Wrapper binary/scripts unchanged.

- [ ] **Step 1: Add a JUnit 5 smoke test** that asserts `AICompanionMod.MOD_ID.equals("mcaimod")`.
- [ ] **Step 2: Run** `./gradlew test`. Expected: initial failing test / incomplete source, not a silently skipped test.
- [ ] **Step 3: Add official MDK scaffold and update the new entrypoint**; JUnit Jupiter dependency `testImplementation 'org.junit.jupiter:junit-jupiter:5.12.2'`, `test { useJUnitPlatform() }`.
- [ ] **Step 4: Run** `./gradlew test build`. Expected: BUILD SUCCESSFUL and output JAR in `build/libs`.
- [ ] **Step 5: Commit** `chore: bootstrap NeoForge 26.2 mod project`.

### Task 2: Immutable player/world snapshots

**Files:**
- Create: `src/main/java/com/liguyano/mcaimod/context/ActorSnapshot.java`.
- Create: `src/main/java/com/liguyano/mcaimod/context/NearbyEntity.java`.
- Create: `src/main/java/com/liguyano/mcaimod/context/GameContextCollector.java`.
- Test: `src/test/java/com/liguyano/mcaimod/context/ActorSnapshotTest.java`.

**Interfaces:**
- Produces: `record ActorSnapshot(UUID actorId, String actorType, Instant capturedAt, String dimension, int x, int y, int z, String biome, String weather, long dayTime, float health, float maxHealth, int food, String mainHand, String offHand, List<String> armor, List<String> effects, List<NearbyEntity> nearby)`.
- Produces: `record NearbyEntity(String type, double distance)`.
- Produces: `GameContextCollector.capture(ServerPlayer player): ActorSnapshot` on the server thread.

- [ ] **Step 1: Test** immutable snapshots, maximum 12 nearest entities, input list copying, and `unknown`/not-applicable values.
- [ ] **Step 2: Run** `./gradlew test --tests '*ActorSnapshotTest'`; expected RED.
- [ ] **Step 3: Implement** records with validation and collector with loaded-entity 16-block radius and nearest-first cap; do not load chunks.
- [ ] **Step 4: Run** targeted test and build; expected GREEN.
- [ ] **Step 5: Commit** `feat: capture bounded player world context`.

### Task 3: Per-player short-term conversation management

**Files:**
- Create: `src/main/java/com/liguyano/mcaimod/chat/ChatTurn.java`.
- Create: `src/main/java/com/liguyano/mcaimod/chat/ConversationManager.java`.
- Test: `src/test/java/com/liguyano/mcaimod/chat/ConversationManagerTest.java`.

**Interfaces:**
- Produces: `record ChatTurn(String user, String assistant)`.
- Produces: `ConversationManager.history(UUID): List<ChatTurn>`, `append(UUID, ChatTurn): void`, `clear(UUID): void`, `tryBegin(UUID): boolean`, `finish(UUID): void`, `isActive(UUID): boolean`, `selectedProfile(UUID): String`, `selectProfile(UUID,String): void`.

- [ ] **Step 1: Test** 10-turn truncation, defensive snapshots, UUID isolation, separate selected profiles, and single in-flight request per UUID.
- [ ] **Step 2: Run** `./gradlew test --tests '*ConversationManagerTest'`; expected RED.
- [ ] **Step 3: Implement** bounded synchronized (or concurrent) per-player sessions without leaking mutable lists.
- [ ] **Step 4: Run** tests and build; expected GREEN.
- [ ] **Step 5: Commit** `feat: isolate bounded conversation sessions`.

### Task 4: Configurable model profiles and HTTP provider

**Files:**
- Create: `src/main/java/com/liguyano/mcaimod/model/ModelProfile.java`, `ModelProfiles.java`, `ChatMessage.java`, `ModelProvider.java`, `OpenAiCompatibleProvider.java`, `ModelException.java`.
- Create: `config/mcaimod-models.example.json`.
- Test: `src/test/java/com/liguyano/mcaimod/model/OpenAiCompatibleProviderTest.java`, `ModelProfilesTest.java`.

**Interfaces:**
- Produces: `record ChatMessage(String role, String content)`.
- Produces: `record ModelProfile(String name, URI baseUrl, String modelId, String apiKeyEnv, Duration timeout, int maxOutputTokens)`.
- Produces: `ModelProfiles.load(Path configFile): ModelProfiles (path `config/mcaimod-models.json`, document how to copy shipped example)`, `get(String name): Optional<ModelProfile>`, `names(): Set<String>`.
- Produces: `interface ModelProvider { CompletableFuture<String> chat(ModelProfile profile, List<ChatMessage> messages); }`.

- [ ] **Step 1: Use local JDK `HttpServer` tests** for exact JSON roles/model path, proper Bearer key only from env, 200 response, 401, 429, malformed JSON, timeout, and invalid/untrusted endpoint URLs.
- [ ] **Step 2: Run** targeted provider/profile tests; expected RED.
- [ ] **Step 3: Implement** Java HttpClient + Gson using Chat Completions `stream=false`; allow HTTPS or explicit `localhost/127.0.0.1/[::1]` loopback HTTP; redacted errors and bounded response size.
- [ ] **Step 4: Run** targeted tests and build; expected GREEN.
- [ ] **Step 5: Commit** `feat: support switchable chat completion models`.

### Task 5: Prompt composer and context minimization

**Files:**
- Create: `src/main/java/com/liguyano/mcaimod/chat/PromptBuilder.java`.
- Test: `src/test/java/com/liguyano/mcaimod/chat/PromptBuilderTest.java`.

**Interfaces:**
- Produces: `PromptBuilder.build(ActorSnapshot snapshot, List<ChatTurn> history, String userText, boolean shareExactCoordinates): List<ChatMessage>`.

- [ ] **Step 1: Test** instruction + snapshot + last 10 user/assistant turns + final user message; no exact coordinates when disabled; unavailable fields marked unknown; special characters in user text preserved as content rather than instructions.
- [ ] **Step 2: Run** targeted test; expected RED.
- [ ] **Step 3: Implement** stable system instruction that game snapshot is authoritative at capture time and no unobserved facts/actions may be asserted.
- [ ] **Step 4: Run** targeted tests; expected GREEN.
- [ ] **Step 5: Commit** `feat: build safe context-aware prompts`.

### Task 6: End-to-end chat commands and async orchestration

**Files:**
- Create: `src/main/java/com/liguyano/mcaimod/chat/AIChatService.java`.
- Create: `src/main/java/com/liguyano/mcaimod/command/AIChatCommand.java`.
- Modify: `src/main/java/com/liguyano/mcaimod/AICompanionMod.java`.
- Test: `src/test/java/com/liguyano/mcaimod/chat/AIChatServiceTest.java` (pure Java fakes, no live `ServerPlayer` required).

**Interfaces:**
- Consumes: `GameContextCollector.capture`, `PromptBuilder.build`, `ConversationManager`, `ModelProfiles`, `ModelProvider.chat`.
- Produces: `AIChatService.ask(UUID actorId, ActorSnapshot snapshot, String text): CompletionStage<String>`, `clear(UUID)`, `selectProfile(UUID,String)`, `status(UUID)`; Minecraft command layer owns player lookup, snapshot capture, and server-thread feedback.
- Commands: `/ai <greedy_text>`, `/ai clear`, `/ai status`, `/ai model list`, `/ai model use <profile>`.

- [ ] **Step 1: Test** empty/whitespace/over-1000 characters, per-player 4-second rate limit, busy sessions, timeout/failure not persisted, disconnect response suppression, correct player feedback scheduling and profile authorization.
- [ ] **Step 2: Run** targeted tests; expected RED.
- [ ] **Step 3: Implement** command registration with `RegisterCommandsEvent`, server-thread snapshot, async provider call, server executor callback; keep user message and result on correct UUID.
- [ ] **Step 4: Run** `./gradlew test build`; expected GREEN; manually verify `./gradlew runClient` and `./gradlew runServer` where supported.
- [ ] **Step 5: Commit** `feat: expose AI chat commands in Minecraft`.

### Task 7: Documentation, security checks, and packaging

**Files:**
- Create/modify: `README.md`, `.gitignore`, `.github/workflows/build.yml`, `config/mcaimod-models.example.json`.
- Test: `src/test/java/com/liguyano/mcaimod/ReleaseSafetyTest.java`.

**Interfaces:**
- Produces: end-user setup examples for Windows, Linux dedicated servers and Java 25; CI job verifies Gradle tests/build.

- [ ] **Step 1: Test** no hard-coded token or credential leakage in distributed files; docs describe model profiles, env vars, and server-side configuration.
- [ ] **Step 2: Run** test, expected RED if safety/docs gaps remain.
- [ ] **Step 3: Document** setup (`./gradlew runClient`, `./gradlew build`), Windows `.\gradlew.bat` variants, generated JAR, Ollama/OpenAI/DeepSeek profile examples; CI JDK 25 build.
- [ ] **Step 4: Run** `./gradlew test build`, inspect output JAR contents, verify CI/Gradle success.
- [ ] **Step 5: Commit** `docs: explain model configuration and validate release`.

## Execution Notes

- Do all implementation on a feature branch `feat/ai-companion-v0.1`; preserve the approved spec and this plan on `main`.
- Prefer a local checkout for Gradle tests; use the connected GitHub integration for branch/PR publication if direct git pushes are unavailable.
- Mark tests/build explicitly `not run` if network or JDK provisioning makes them impossible; never claim compilation without successful command output.
- After successful implementation, create a PR rather than directly merge into `main`.
