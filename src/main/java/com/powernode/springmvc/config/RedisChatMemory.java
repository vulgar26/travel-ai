package com.powernode.springmvc.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powernode.springmvc.dto.SimpleMsg;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Component
public class RedisChatMemory implements ChatMemory {
    private static final String KEY_PREFIX = "travel:chat:memory:";
    private static final int MAX_MESSAGES = 20;
    private static final long EXPIRE_DAYS = 1;

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    public RedisChatMemory(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = new ObjectMapper();
    }

    private String buildKey(String conversationId) {
        var context = org.springframework.security.core.context.SecurityContextHolder.getContext();
        String username = context.getAuthentication() != null
                ? context.getAuthentication().getName()
                : "anonymous";
        return KEY_PREFIX + username + ":" + conversationId;
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        String key = buildKey(conversationId);

        List<SimpleMsg> simpleHistory = getSimpleHistory(conversationId);

        List<SimpleMsg> newSimpleList = messages.stream().map(msg -> {
            SimpleMsg sm = new SimpleMsg();
            sm.role = msg instanceof UserMessage ? "user" : "assistant";
            sm.content = msg.getText();
            return sm;
        }).collect(Collectors.toList());

        simpleHistory.addAll(newSimpleList);

        while (simpleHistory.size() > MAX_MESSAGES) {
            simpleHistory.remove(0);
        }

        try {
            String json = objectMapper.writeValueAsString(simpleHistory);
            stringRedisTemplate.opsForValue().set(key, json, EXPIRE_DAYS, TimeUnit.DAYS);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("保存对话失败", e);
        }
    }

    // 读取简单消息列表（按 userId + conversationId 维度隔离）
    private List<SimpleMsg> getSimpleHistory(String conversationId) {
        String key = buildKey(conversationId);
        String json = stringRedisTemplate.opsForValue().get(key);
        log.debug("从Redis原始数据: {}", json);
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<SimpleMsg>>() {});
        } catch (Exception e) {
            // 旧脏数据直接清空，不报错
            log.error("历史数据格式异常，清空重建: {}", e.getMessage());
            stringRedisTemplate.delete(key);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        List<SimpleMsg> simpleHistory = getSimpleHistory(conversationId);
        // 转回 SpringAI Message
        return simpleHistory.stream().map(sm -> {
            if ("user".equals(sm.role)) {
                return new UserMessage(sm.content);
            } else {
                return new AssistantMessage(sm.content);
            }
        }).collect(Collectors.toList());
    }

    @Override
    public void clear(String conversationId) {
        stringRedisTemplate.delete(buildKey(conversationId));
    }
}