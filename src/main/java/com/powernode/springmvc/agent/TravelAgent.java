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
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
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

    public Flux<String> chat(String conversationId, String userMessage) {
        String requestId = UUID.randomUUID().toString();
        MDC.put("requestId", requestId);
        log.info("调用AI，conversationId={}, requestId={}, message={}", conversationId, requestId, userMessage);
        List<String> queries = queryRewriter.rewrite(userMessage);

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
        log.info("检索到 {} 条知识，queries={}", docs.size(), queries);

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        String promptWithContext = context.isEmpty()
                ? userMessage
                : "【景点参考信息】\n" + context + "\n\n【用户问题】\n" + userMessage;

        log.info("最终 prompt 字符数={}", promptWithContext.length());

        return chatClient.prompt(promptWithContext)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content()
                .doFinally(signalType -> MDC.remove("requestId"));
    }
}
