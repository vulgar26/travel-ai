设计并实现基于查询改写的多路召回链路：通过独立 ChatClient 将用户输入改写为 3 个不同角度的检索 query，多路并行检索后合并去重，相比直接检索显著提升相关文档覆盖率与召回稳定性。
自定义实现 Spring AI VectorStore 接口：基于 JDBC + pgvector 将 Embedding 向量持久化到 PostgreSQL，替代内存 SimpleVectorStore，保证重启不丢数据并深入理解向量化存储与余弦相似度检索的底层实现。
补齐项目工程化基线与安全边界：将 DashScope/天气等敏感配置环境变量化（密钥外置），引入 SLF4J + MDC 贯穿 requestId 日志链路，并在知识库入库时携带用户 metadata 支持按用户隔离检索，降低越权访问风险。
引入 Spring Security + JWT，保护聊天与知识上传接口，基于 SecurityContext 将 RAG 检索与向量 metadata 绑定到登录用户，消除多用户越权访问风险。
为 `/travel/chat` SSE 链路设计并实现端到端稳健性：使用 Bucket4j + Caffeine 按用户/IP 维度做每分钟限流（429 JSON 规范化返回），在 LLM 与天气工具中引入统一超时与错误降级（Reactor timeout + onErrorResume、OkHttp 超时配置），并通过精细化配置 Spring Security dispatcher 匹配消除异步收尾阶段的 AccessDenied 噪音日志，提升异常场景下的可观测性与用户体验。
为服务接入 Spring Boot Actuator 健康探活：暴露 `/actuator/health` 与 `/actuator/info`，开启 health probes 子路径，并通过 Spring Security 白名单确保匿名可用；匿名仅返回聚合状态、详情在鉴权后展开，降低信息泄露风险。
基于 Spring Security + JWT 保护业务接口，将 RAG 向量检索与 ChatMemory 与登录用户绑定，并叠加 Bucket4j 限流与 LLM/外部工具超时降级，形成可演示的最小上线安全与稳定性闭环。
接入 Spring Boot Actuator（health/info、liveness/readiness probes）与匿名探活白名单，支撑编排与负载均衡健康检查，敏感详情通过 `when_authorized` 控制展示。
SSE 使用 `ServerSentEvent` 区分 `data` 与 `comment` 心跳、共享上游避免重复 LLM 调用，并在客户端断开时通过取消订阅与日志确认资源释放路径。
以 Flyway 管理 `pgvector` 版本化建表迁移（`V1__init_pgvector.sql`），并用 Testcontainers 集成测试验证迁移/Redis/health，在 GitHub Actions 的 CI 中自动跑 `mvn test` 保证可部署可复现。