package com.travel.ai.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link com.travel.ai.controller.KnowledgeController} 专用：multipart 缺参、超限等返回统一 JSON。
 */
@RestControllerAdvice(assignableTypes = com.travel.ai.controller.KnowledgeController.class)
public class KnowledgeControllerAdvice {

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<Map<String, String>> missingPart(MissingServletRequestPartException ex) {
        return json(HttpStatus.BAD_REQUEST, "MISSING_PART", ex.getMessage() != null ? ex.getMessage() : "missing multipart part");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException ex) {
        return json(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "上传文件超过服务器允许的大小");
    }

    private static ResponseEntity<Map<String, String>> json(HttpStatus status, String code, String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", code);
        m.put("message", message);
        return ResponseEntity.status(status).body(m);
    }
}
