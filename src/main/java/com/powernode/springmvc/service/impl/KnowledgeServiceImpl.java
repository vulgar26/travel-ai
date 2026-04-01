package com.powernode.springmvc.service.impl;

import com.powernode.springmvc.service.KnowledgeService;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;

    public KnowledgeServiceImpl(VectorStore vectorStore, TokenTextSplitter splitter) {
        this.vectorStore = vectorStore;
        this.splitter = splitter;
    }

    @Override
    public String uploadDocument(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();

        if (filename == null) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        if (!filename.toLowerCase().endsWith(".txt")) {
            throw new IllegalArgumentException("仅支持上传 .txt 文件");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > 1024 * 1024 * 10) {
            throw new IllegalArgumentException("文件大小不能超过10MB");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase("text/plain")) {
            throw new IllegalArgumentException("文件类型必须为 text/plain");
        }
        // 直接读取文件内容为字符串
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        log.info("文件长度：{}", content.length());
        if (content.isBlank()) {
            throw new IllegalArgumentException("文件内容不能为空");
        }


        // 包装成Document（带最小 metadata，用于后续按用户/来源过滤）
        Map<String, Object> metadata = new HashMap<>();
        String currentUser = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication() != null
                ? org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName()
                : "anonymous";
        metadata.put("user_id", currentUser);
        metadata.put("source_name", filename);
        metadata.put("uploaded_at", Instant.now().toString());
        List<Document> documents = List.of(new Document(content, metadata));

        // 分块
        List<Document> chunks = splitter.apply(documents);
        log.info("分块完成，共 {} 个chunk", chunks.size());
        if (chunks.size() > 500) {
            throw new IllegalArgumentException("文件分块数量不能超过500");
        }

        // 向量化+存入知识库
        vectorStore.add(chunks);
        log.info("入库完成：{}", filename);

        return String.format("文档[%s]上传成功，共%d个知识块", filename, chunks.size());
    }
}