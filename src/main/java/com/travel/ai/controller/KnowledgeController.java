package com.travel.ai.controller;

import com.travel.ai.dto.KnowledgeUploadResult;
import com.travel.ai.service.DuplicateKnowledgeException;
import com.travel.ai.service.KnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) {
        try {
            KnowledgeUploadResult r = knowledgeService.uploadDocument(file);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("fileName", r.fileName());
            body.put("chunkCount", r.chunkCount());
            body.put("fileId", r.fileId());
            body.put("contentHash", r.contentHash());
            body.put("message", String.format("文档[%s]上传成功，共%d个知识块", r.fileName(), r.chunkCount()));
            return ResponseEntity.ok(body);
        } catch (DuplicateKnowledgeException ex) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "DUPLICATE_KNOWLEDGE");
            body.put("message", ex.getMessage() != null ? ex.getMessage() : "duplicate knowledge");
            body.put("fileId", ex.getFileId());
            body.put("fileName", ex.getFileName());
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_UPLOAD",
                    "message", ex.getMessage() != null ? ex.getMessage() : "invalid upload"
            ));
        } catch (IOException ex) {
            log.warn("knowledge upload io_failed: {}", ex.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "UPLOAD_IO_FAILED",
                    "message", "读取或写入上传内容失败"
            ));
        }
    }
}
