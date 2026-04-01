package com.powernode.springmvc.service.impl;

import com.powernode.springmvc.service.KnowledgeService;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

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
        System.out.println("文件长度：" + content.length());
        if (content.isBlank()) {
            throw new IllegalArgumentException("文件内容不能为空");
        }


        // 包装成Document
        List<Document> documents = List.of(new Document(content));

        // 分块
        List<Document> chunks = splitter.apply(documents);
        System.out.println("分块完成，共 " + chunks.size() + " 个chunk");
        if (chunks.size() > 500) {
            throw new IllegalArgumentException("文件分块数量不能超过500");
        }

        // 向量化+存入知识库
        vectorStore.add(chunks);
        System.out.println("入库完成：" + filename);

        return String.format("文档[%s]上传成功，共%d个知识块", filename, chunks.size());
    }
}