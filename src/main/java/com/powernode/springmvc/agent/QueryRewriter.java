package com.powernode.springmvc.agent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class QueryRewriter {

    private final ChatClient chatClient;

    private static final String REWRITE_PROMPT = """
            你是一个搜索query优化专家。
            用户输入一个旅游相关的问题，你需要将其改写为3个不同角度的检索query。
            要求：
            1. 每个query简洁精准，适合向量检索
            2. 三个query角度不同，覆盖更广
            3. 只输出3个query，每行一个，不要编号，不要其他内容
            
            示例：
            用户问题：成都好玩的地方
            输出：
            成都著名景点推荐
            成都热门旅游胜地
            成都必去打卡景点
            """;

    public QueryRewriter(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem(REWRITE_PROMPT)
                .build();
    }

    public List<String> rewrite(String userQuestion) {
        String result = chatClient.prompt(userQuestion)
                .call()
                .content();

        System.out.println("查询改写结果：" + result);

        return Arrays.stream(result.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .limit(3)
                .collect(Collectors.toList());
    }
}
