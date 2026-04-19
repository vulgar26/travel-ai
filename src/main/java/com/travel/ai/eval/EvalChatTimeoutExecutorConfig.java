package com.travel.ai.eval;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 评测 {@link EvalChatController} 整段 {@code app.agent.total-timeout} 包裹用线程池（与 Servlet 线程隔离，便于 {@code Future#get} 中断）。
 */
@Configuration
public class EvalChatTimeoutExecutorConfig {

    @Bean(name = "evalChatTimeoutExecutor", destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor evalChatTimeoutExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setThreadNamePrefix("eval-chat-timeout-");
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(16);
        ex.setQueueCapacity(100);
        ex.setDaemon(true);
        ex.initialize();
        return ex;
    }
}
