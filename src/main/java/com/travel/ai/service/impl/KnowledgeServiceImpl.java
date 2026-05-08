package com.travel.ai.service.impl;

import com.travel.ai.dto.KnowledgeFileItem;
import com.travel.ai.dto.KnowledgeListResponse;
import com.travel.ai.dto.KnowledgeUploadResult;
import com.travel.ai.service.DuplicateKnowledgeException;
import com.travel.ai.service.KnowledgeFileNotFoundException;
import com.travel.ai.service.KnowledgeService;
import com.travel.ai.service.LegacyKnowledgeNotDeletableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeServiceImpl.class);

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter;
    private final JdbcTemplate jdbcTemplate;

    public KnowledgeServiceImpl(VectorStore vectorStore, TokenTextSplitter splitter, JdbcTemplate jdbcTemplate) {
        this.vectorStore = vectorStore;
        this.splitter = splitter;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public KnowledgeUploadResult uploadDocument(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
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

        byte[] rawBytes = file.getBytes();
        String contentHash = sha256Hex(rawBytes);
        String currentUser = currentUser();
        DuplicateKnowledge duplicate = findDuplicate(currentUser, contentHash);
        if (duplicate != null) {
            throw new DuplicateKnowledgeException("该文件内容已上传", duplicate.fileId(), duplicate.fileName());
        }

        String fileId = UUID.randomUUID().toString();
        String uploadedAt = Instant.now().toString();
        String content = new String(rawBytes, StandardCharsets.UTF_8);
        log.info("文件长度：{}", content.length());
        if (content.isBlank()) {
            throw new IllegalArgumentException("文件内容不能为空");
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("user_id", currentUser);
        metadata.put("file_id", fileId);
        metadata.put("filename", filename);
        metadata.put("source_name", filename);
        metadata.put("uploaded_at", uploadedAt);
        metadata.put("content_hash", contentHash);

        List<Document> chunks = splitter.apply(List.of(new Document(content, metadata))).stream()
                .map(chunk -> new Document(chunk.getText(), new LinkedHashMap<>(metadata)))
                .toList();
        log.info("分块完成，共 {} 个 chunk", chunks.size());
        if (chunks.size() > 500) {
            throw new IllegalArgumentException("文件分块数量不能超过500");
        }

        vectorStore.add(chunks);
        log.info("入库完成：{}", filename);

        return new KnowledgeUploadResult(filename, chunks.size(), fileId, contentHash);
    }

    @Override
    public KnowledgeListResponse listMine() {
        String userId = currentUser();
        String sql = """
                SELECT
                    metadata->>'file_id' AS file_id,
                    COALESCE(metadata->>'filename', metadata->>'source_name') AS filename,
                    metadata->>'uploaded_at' AS created_at,
                    metadata->>'content_hash' AS content_hash,
                    COUNT(*) AS chunk_count
                FROM vector_store
                WHERE metadata->>'user_id' = ?
                GROUP BY
                    metadata->>'file_id',
                    COALESCE(metadata->>'filename', metadata->>'source_name'),
                    metadata->>'uploaded_at',
                    metadata->>'content_hash'
                ORDER BY created_at DESC NULLS LAST, filename ASC
                """;
        List<KnowledgeFileItem> items = jdbcTemplate.query(sql, (rs, rowNum) -> {
            String fileId = rs.getString("file_id");
            String filename = rs.getString("filename");
            String createdAt = rs.getString("created_at");
            String contentHash = rs.getString("content_hash");
            int chunkCount = rs.getInt("chunk_count");
            boolean legacy = fileId == null || fileId.isBlank();
            String effectiveFileId = legacy ? legacyFileId(userId, filename, createdAt) : fileId;
            return new KnowledgeFileItem(
                    effectiveFileId,
                    filename,
                    chunkCount,
                    createdAt,
                    contentHash,
                    legacy,
                    !legacy
            );
        }, userId);
        return new KnowledgeListResponse(items);
    }

    @Override
    @Transactional
    public void deleteMine(String fileId) {
        if (fileId == null || fileId.isBlank()) {
            throw new IllegalArgumentException("fileId is required");
        }
        if (fileId.startsWith("legacy:")) {
            throw new LegacyKnowledgeNotDeletableException("旧知识数据缺少 file_id，第一版仅支持删除新上传的知识文件");
        }
        int deleted = jdbcTemplate.update("""
                DELETE FROM vector_store
                WHERE metadata->>'user_id' = ?
                  AND metadata->>'file_id' = ?
                """, currentUser(), fileId);
        if (deleted == 0) {
            throw new KnowledgeFileNotFoundException("知识文件不存在或已删除");
        }
    }

    private DuplicateKnowledge findDuplicate(String userId, String contentHash) {
        List<DuplicateKnowledge> rows = jdbcTemplate.query("""
                        SELECT metadata->>'file_id' AS file_id,
                               COALESCE(metadata->>'filename', metadata->>'source_name') AS filename
                        FROM vector_store
                        WHERE metadata->>'user_id' = ?
                          AND metadata->>'content_hash' = ?
                          AND metadata->>'file_id' IS NOT NULL
                        LIMIT 1
                        """,
                (rs, rowNum) -> new DuplicateKnowledge(rs.getString("file_id"), rs.getString("filename")),
                userId,
                contentHash);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private String currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "anonymous";
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String legacyFileId(String userId, String filename, String uploadedAt) {
        String raw = String.valueOf(userId) + "\n" + String.valueOf(filename) + "\n" + String.valueOf(uploadedAt);
        return "legacy:" + sha256Hex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private record DuplicateKnowledge(String fileId, String fileName) {
    }
}
