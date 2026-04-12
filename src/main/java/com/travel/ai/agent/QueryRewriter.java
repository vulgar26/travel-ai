package com.travel.ai.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 将用户自然语言问题改写成若干条「适合向量检索」的短 query。
 * <p>
 * 升级点（UPGRADE P2-1）：不盲目信任模型输出——解析失败、空行、行数不足时<b>回退/补齐</b>，
 * 并对单行长度做截断，避免异常长文本拖垮 embedding 调用。
 */
@Component
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    private final ChatClient chatClient;

    /** 模型最多产出几条检索 query（与 prompt 中「3 条」一致） */
    private static final int TARGET_QUERY_COUNT = 3;

    /** 单条 query 最大字符数，防止恶意或模型失常导致超长字符串进入 embed */
    @Value("${app.rag.rewrite.max-line-length:256}")
    private int maxLineLength;

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

    /**
     * 对外唯一入口：返回长度 1～3 的非空检索串列表；保证至少含一条可用 query（通常为原问题）。
     */
    public List<String> rewrite(String userQuestion) {
        String original = userQuestion == null ? "" : userQuestion.trim();
        if (original.isEmpty()) {
            log.warn("查询改写：用户问题为空，返回占位单条以避免下游 NPE");
            return List.of(" ");
        }

        String result;
        try {
            result = chatClient.prompt(userQuestion).call().content();
        } catch (Exception e) {
            // LLM 超时、鉴权失败、网络错误等：直接回退为原问题，仍可进行单向量检索
            log.warn("查询改写：LLM 调用失败，回退为原始问题。原因={}", e.toString());
            return padToTarget(List.of(original), original);
        }

        if (result == null || result.isBlank()) {
            log.warn("查询改写：模型返回空内容，回退为原始问题");
            return padToTarget(List.of(), original);
        }

        log.info("查询改写结果：{}", result);

        List<String> parsed = Arrays.stream(result.split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::truncateLine)
                .limit(TARGET_QUERY_COUNT)
                .collect(Collectors.toList());

        return padToTarget(parsed, original);
    }

    /**
     * 将单行截断到 {@link #maxLineLength}，避免 embedding 输入过长。
     */
    private String truncateLine(String line) {
        if (line.length() <= maxLineLength) {
            return line;
        }
        return line.substring(0, maxLineLength);
    }

    /**
     * 若模型给出的有效行数为 0，则至少使用原问题；若少于 {@link #TARGET_QUERY_COUNT}，用原问题补齐。
     * 补齐时重复原问题在向量检索上略冗余，但优于「条数不足导致召回面过窄」或下游假设固定 3 条。
     */
    private List<String> padToTarget(List<String> fromModel, String original) {
        List<String> out = new ArrayList<>();
        for (String s : fromModel) {
            if (s != null && !s.isBlank()) {
                out.add(s.trim());
            }
            if (out.size() >= TARGET_QUERY_COUNT) {
                break;
            }
        }
        if (out.isEmpty()) {
            out.add(original);
        }
        while (out.size() < TARGET_QUERY_COUNT) {
            out.add(original);
        }
        return out;
    }
}
