# ARCHITECTURE — 最简链路说明（Day 3）

本项目的核心是“一条可解释的 RAG 链路”：**改写 → 检索 → 拼上下文 → 流式生成**。

## 1. 请求链路（文本流程图）

客户端请求（SSE）  
→ `TravelController`：`GET /travel/chat/{conversationId}?query=...`  
→ `TravelAgent.chat(conversationId, userMessage)`  
→ `QueryRewriter.rewrite(userMessage)`（生成 3 条检索 query）  
→ `VectorStore.similaritySearch(...)`（对每条 query 检索 TopK，合并/去重/限制条数）  
→ 拼接 `promptWithContext`（将检索文本注入到最终 prompt）  
→ `ChatClient.prompt(promptWithContext)`（携带 `conversationId` 作为 chat memory key）  
→ SSE 流式返回 token/content 给客户端

## 2. 单链路约束（为什么要这样做）

- 每次请求仅保留 **一套** “检索 + 上下文注入”逻辑，避免重复检索导致的成本与延迟不可控。
- 检索到的文本必须进入最终 prompt（否则 RAG 只是“检索了但没用上”）。

## 3. 关键可观测点（最小版）

一次请求至少记录以下信息（不打印完整隐私内容）：

- 检索条数：`docs.size()`
- 最终 prompt 长度：`promptWithContext.length()`

## 4. 相关源码入口

- 对话 SSE 入口：`src/main/java/com/powernode/springmvc/controller/TravelController.java`
- RAG 链路实现：`src/main/java/com/powernode/springmvc/agent/TravelAgent.java`
- 查询改写：`src/main/java/com/powernode/springmvc/agent/QueryRewriter.java`
- 向量检索：`src/main/java/com/powernode/springmvc/config/PgVectorStore.java`

