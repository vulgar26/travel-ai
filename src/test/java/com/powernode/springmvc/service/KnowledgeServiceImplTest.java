package com.powernode.springmvc.service;

import com.powernode.springmvc.service.impl.KnowledgeServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeServiceImplTest {

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

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());

        List<Document> chunks = captor.getValue();
        assertNotNull(chunks);
        assertFalse(chunks.isEmpty());
        assertTrue(result.contains(String.valueOf(chunks.size())));
    }
}