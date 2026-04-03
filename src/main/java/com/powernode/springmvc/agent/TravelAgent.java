package com.powernode.springmvc.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.powernode.springmvc.config.RedisChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class TravelAgent {

    private static final Logger log = LoggerFactory.getLogger(TravelAgent.class);

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final QueryRewriter queryRewriter;
    private final WeatherTool weatherTool;

    @Autowired
    private RedisChatMemory chatMemory;

    /** 长连接空闲时定期发送 SSE 注释行（comment），避免网关/代理因无数据而断开 */
    @Value("${app.sse.heartbeat-seconds:15}")
    private int sseHeartbeatSeconds;

    private static final  String SYSTEM_PROMPT = """
            你是一个专业的出行规划助手。
            当用户提供出发地、目的地、时间、预算等信息时，你需要：
            1. 分析用户的出行需求
            2. 提供多种出行方案（飞机、高铁、自驾等）
            3. 对每种方案给出大致费用、时长、优缺点
            4. 根据用户预算和偏好推荐最适合的方案
            请用清晰的结构化格式回答，方便用户阅读。
            """;

    public TravelAgent(ChatClient.Builder builder,
                       RedisChatMemory chatMemory,
                       VectorStore vectorStore,
                       QueryRewriter queryRewriter,
                       WeatherTool weatherTool) {
        this.vectorStore = vectorStore;
        this.queryRewriter = queryRewriter;
        this.weatherTool = weatherTool;
        this.chatClient = builder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .defaultTools(weatherTool)
                .defaultOptions(
                        DashScopeChatOptions.builder()
                                .topP(0.7)
                                .build()
                )
                .build();
    }

    public Flux<ServerSentEvent<String>> chat(String conversationId, String userMessage) {
        // 为了防止外部 LLM 长时间无响应，这里增加一个整体超时时间
        Duration llmTimeout = Duration.ofSeconds(20);

        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        log.info("调用AI，conversationId={}, requestId={}, message={}", conversationId, requestId, userMessage);
        long tRewrite0 = System.nanoTime();
        List<String> queries = queryRewriter.rewrite(userMessage);
        long rewriteMs = (System.nanoTime() - tRewrite0) / 1_000_000L;

        // 按 user_id 做隔离：从 SecurityContext 中获取当前用户
        String currentUser = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName()
                : "anonymous";

        Filter.Expression userFilter = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("user_id"),
                new Filter.Value(currentUser)
        );

        long tRetrieve0 = System.nanoTime();
        List<Document> docs = queries.stream()
                .flatMap(query -> vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(2)
                                .filterExpression(userFilter)
                                .build()
                ).stream())
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
        long retrieveMs = (System.nanoTime() - tRetrieve0) / 1_000_000L;
        log.info("检索到 {} 条知识，queries={}", docs.size(), queries);
        log.info("[perf] rewrite_ms={} retrieve_ms={} doc_count={} requestId={}", rewriteMs, retrieveMs, docs.size(), requestId);

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        String promptWithContext = context.isEmpty()
                ? userMessage
                : "【景点参考信息】\n" + context + "\n\n【用户问题】\n" + userMessage;

        log.info("最终 prompt 字符数={}", promptWithContext.length());

        AtomicLong llmStartNs = new AtomicLong();
        AtomicBoolean firstLlmToken = new AtomicBoolean(true);

        // 内容流：先 share，便于「正文」与「心跳」共用同一套上游订阅，避免重复调用 LLM
        Flux<String> contentFlux = chatClient.prompt(promptWithContext)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .timeout(llmTimeout)
                .onErrorResume(throwable -> {
                    log.error("调用 AI 超时或出错，conversationId={}, requestId={}, error={}", conversationId, requestId, throwable.toString());
                    return Flux.just("【系统提示】当前 AI 响应较慢或出现异常，请稍后重试。");
                })
                .doOnSubscribe(s -> llmStartNs.set(System.nanoTime()))
                .doOnNext(chunk -> {
                    if (firstLlmToken.compareAndSet(true, false)) {
                        long ttftMs = (System.nanoTime() - llmStartNs.get()) / 1_000_000L;
                        log.info("[perf] llm_first_token_ms={} requestId={}", ttftMs, requestId);
                    }
                })
                .doFinally(signal -> {
                    if (llmStartNs.get() != 0L) {
                        long wallMs = (System.nanoTime() - llmStartNs.get()) / 1_000_000L;
                        log.info("[perf] llm_stream_wall_ms={} signal={} requestId={}", wallMs, signal, requestId);
                    }
                })
                .share();

        Flux<ServerSentEvent<String>> tokenEvents = contentFlux.map(chunk ->
                ServerSentEvent.<String>builder().data(chunk).build());

        // 可解释性：首包先输出本轮命中的检索片段（与 LLM 正文分离，便于前端/日志观察）
        String citationBlock = buildCitationBlock(docs);
        Flux<ServerSentEvent<String>> citationFlux = Flux.just(
                ServerSentEvent.<String>builder().data(citationBlock).build()
        );

        // 心跳：按 SSE 规范使用 comment 行（: keepalive），不占用 data 通道，前端可忽略
        Flux<ServerSentEvent<String>> keepAlive = Flux.interval(Duration.ofSeconds(Math.max(1, sseHeartbeatSeconds)))
                .takeUntilOther(contentFlux.then())
                .map(tick -> ServerSentEvent.<String>builder().comment("keepalive").build());

        return Flux.concat(citationFlux, Flux.merge(tokenEvents, keepAlive))
                .doOnCancel(() -> log.info("SSE 订阅已取消（多为客户端断开），conversationId={}, requestId={}", conversationId, requestId))
                .doFinally(signalType -> MDC.remove("requestId"));
    }

    /**
     * 将本轮用于拼 prompt 的检索结果，以纯文本块形式前置输出（SSE 首段 data）。
     */
    private String buildCitationBlock(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return "【引用片段】\n（本轮未命中知识库）\n\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【引用片段】（共 ").append(docs.size()).append(" 条）\n");
        int i = 1;
        for (Document d : docs) {
            String id = d.getId() != null ? d.getId() : "(无id)";
            Object src = d.getMetadata() != null ? d.getMetadata().get("source_name") : null;
            String preview = d.getText() != null ? d.getText() : "";
            if (preview.length() > 200) {
                preview = preview.substring(0, 200) + "…";
            }
            sb.append("[").append(i++).append("] id=").append(id);
            if (src != null) {
                sb.append(" 来源=").append(src);
            }
            sb.append("\n").append(preview).append("\n\n");
        }
        sb.append("────────\n");
        return sb.toString();
    }
}
