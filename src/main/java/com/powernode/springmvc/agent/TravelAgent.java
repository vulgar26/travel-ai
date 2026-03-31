package com.powernode.springmvc.agent;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.powernode.springmvc.config.RedisChatMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class TravelAgent {

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
                .defaultAdvisors(
                        RetrievalAugmentationAdvisor.builder()
                                .documentRetriever(VectorStoreDocumentRetriever.builder()
                                        .vectorStore(vectorStore)
                                        .topK(3)
                                        .build())
                                .build()
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
        System.out.println("调用AI，conversationId：" + conversationId + "，message：" + userMessage);
        List<String> queries = queryRewriter.rewrite(userMessage);
        List<Document> docs = queries.stream()
                .flatMap(query -> vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(2)
                                .build()
                ).stream())
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
        System.out.println("检索到 " + docs.size() + " 条知识，queries: " + queries);

        String context = docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        String promptWithContext = context.isEmpty()
                ? userMessage
                : "【景点参考信息】\n" + context + "\n\n【用户问题】\n" + userMessage;

        return chatClient.prompt(userMessage)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .stream()
                .content();
    }
}
