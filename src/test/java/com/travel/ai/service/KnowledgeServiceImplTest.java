package com.travel.ai.service;

import com.travel.ai.service.impl.KnowledgeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 知识上传单测：上传逻辑从 {@link org.springframework.security.core.context.SecurityContext} 读取当前用户写入 metadata。
 * 本类为「纯 Mockito 单测」不启动 Spring 容器，故在 {@link #bindDemoUser()} 中手动挂载认证信息，
 * 语义上等价于集成环境下的已登录用户 demo（升级方案 P4-1）。
 */
class KnowledgeServiceImplTest {

    @BeforeEach
    void bindDemoUser() {
        var auth = new UsernamePasswordAuthenticationToken(
                "demo",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadDocument_splits_and_adds_to_vectorstore() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        TokenTextSplitter splitter = new TokenTextSplitter();

        KnowledgeServiceImpl service = new KnowledgeServiceImpl(vectorStore, splitter);

        String text = "这是一个测试文档，用于验证分块与入库调用。".repeat(50);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.txt",
                "text/plain",
                text.getBytes(StandardCharsets.UTF_8)
        );

        String result = service.uploadDocument(file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());

        List<Document> chunks = captor.getValue();
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        assertTrue(result.contains(String.valueOf(chunks.size())));

        // 与 SecurityContext 中用户名一致，保证向量检索可按 user_id 隔离
        assertEquals("demo", chunks.get(0).getMetadata().get("user_id"));
    }
}
