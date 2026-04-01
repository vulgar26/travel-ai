🗺️ Travel AI Planner - 智能出行规划助手

基于 Spring Boot 3 + Spring AI Alibaba 构建的 AI 出行规划平台。

项目亮点

1、RAG知识库检索：支持文档动态上传入库，自动分块向量化\
       文档动态上传入库，是通过将用户上传的文件分块，实现长文档转化为多个短文段，再使用vectorStore将分块后的文档向量化，最后存入知识库。这样可以减轻AI读取文档的负担，也可以是RAG检索更为精确，用户得到的回复更为准确。

2、查询改写多路召回：用AI将用户问题改写为3个检索query，提升召回准确率\
       查询改写的实现是通过一个独立的ChatClient调用ai，将用户的输入改写为三个不同方向检索的query。比如用户问“成都有什么好玩”，AI会改写为“成都著名景点”、“成都著名美食”、“成都网红打卡点”这三个query，再分别去向量库检索，合并结果并去重。这样比直接用原始问题检索的覆盖面更广，召回的相关内容更为全面。

3、Function Calling：集成实时天气API，AI自动判断调用时机\
       实现和风天气接口，集成实时天气API，动态获取指定城市的实时天气信息，包括温度、天气状况、湿度等等信息，用于出行规划。并且通过TravelAgent自动判断是否需要调用WeatherTool，例如“成都有什么好玩的”与“明天去成都，规划出行”，前者不会调用，后者则会调用以获取实时天气与未来七天天气预测。

4、Redis持久化对话记忆：多轮对话上下文跨重启保留\
       通过Redis缓存存储多轮对话上下文，拼接对话内容，通过String数据结构保存。在同一用户对话时获取历史对话内容，合并当前输入内容形成prompt，使得LLM可以生成用户个性化内容，并且缓存在Redis中可以实现持久化保存，重启系统仍能获取。并且对比将记忆存在内存中，Redis缓存读取速度更快，占用内存更小，更高效。

5、SSE流式输出：逐字返回，提升用户体验\
       通过逐字返回的SSE流式输出，能够让用户在第一时间获取到信息，早读早理解，提升用户的体验，减少等待的时间。

6、向量持久化：自实现VectorStore接口\
      通过JDBC + pgvector将向量数据持久化到PostgreSQL，解决了内存存储重启丢失的问题，理解了Embedding向量化和余弦相似度检索的底层实现。

技术栈\
Spring Boot 3.3.5\
Spring AI Alibaba 1.1.2.0\
MySQL + Redis\
通义千问 qwen3.5-plus

核心功能\
 RAG知识库\
上传txt文档自动入库：
POST /knowledge/upload\
智能对话：
GET /travel/chat/{conversationId}?query=你的问题

快速启动

配置（环境变量）

为避免敏感信息进入仓库，本项目使用环境变量注入密钥（也可使用本地覆盖文件）。

| 变量名 | 说明 | 是否必需 |
| --- | --- | --- |
| `SPRING_AI_DASHSCOPE_API_KEY` | DashScope / 通义千问 API Key（Spring AI） | 是 |
| `WEATHER_API_KEY` | 天气服务 API Key | 视功能而定 |

本地开发两种方式（二选一）：

1. 方式1（推荐）：直接设置环境变量后启动  
2. 方式2：在 `src/main/resources/application-local.yml` 填入真实值（该文件已在 `.gitignore` 中忽略，勿提交）

快速启动

1. 配置 `application.yml` 中的数据库、Redis（不要在此文件写入真实 API Key）
2. 配置 `SPRING_AI_DASHSCOPE_API_KEY`（以及可选的 `WEATHER_API_KEY`），或使用本地覆盖文件 `application-local.yml`
3. 启动 Redis（本地或 Docker 均可）
4. 运行 `TravelAiApplication`
