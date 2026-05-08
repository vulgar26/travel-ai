package com.travel.ai.service;

import com.travel.ai.service.impl.KnowledgeServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    void uploadDocument_splits_and_adds_file_level_metadata_to_vectorstore() throws Exception {
        VectorStore vectorStore = mock(VectorStore.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any())).thenReturn(List.of());
        TokenTextSplitter splitter = new TokenTextSplitter();

        KnowledgeServiceImpl service = new KnowledgeServiceImpl(vectorStore, splitter, jdbcTemplate);

        String text = "这是一份成都旅行知识，用于验证文件级 metadata 与分块入库。".repeat(50);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "chengdu.txt",
                "text/plain",
                bytes
        );

        var result = service.uploadDocument(file);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());

        List<Document> chunks = captor.getValue();
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        assertEquals(chunks.size(), result.chunkCount());
        assertEquals("chengdu.txt", result.fileName());
        assertNotNull(result.fileId());
        assertEquals(sha256Hex(bytes), result.contentHash());

        Object fileId = chunks.get(0).getMetadata().get("file_id");
        Object uploadedAt = chunks.get(0).getMetadata().get("uploaded_at");
        Object contentHash = chunks.get(0).getMetadata().get("content_hash");
        assertEquals("demo", chunks.get(0).getMetadata().get("user_id"));
        assertEquals("chengdu.txt", chunks.get(0).getMetadata().get("filename"));
        assertEquals("chengdu.txt", chunks.get(0).getMetadata().get("source_name"));

        for (Document chunk : chunks) {
            assertEquals(fileId, chunk.getMetadata().get("file_id"));
            assertEquals(uploadedAt, chunk.getMetadata().get("uploaded_at"));
            assertEquals(contentHash, chunk.getMetadata().get("content_hash"));
        }
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
