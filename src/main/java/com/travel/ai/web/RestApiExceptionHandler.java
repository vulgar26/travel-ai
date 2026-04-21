package com.travel.ai.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局 REST 异常：与鉴权 JSON、{@link KnowledgeControllerAdvice} 同形 {@code error + message}。
 * <p>
 * 不捕获泛型 {@link Exception}，避免吞掉应修复的编程错误。
 */
@RestControllerAdvice
public class RestApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> responseStatus(ResponseStatusException ex) {
        HttpStatusCode code = ex.getStatusCode();
        int status = code.value();
        String error = code instanceof HttpStatus hs ? hs.name() : "HTTP_" + status;
        String message = ex.getReason() != null && !ex.getReason().isBlank()
                ? ex.getReason()
                : error;
        return json(code, error, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> notReadable(HttpMessageNotReadableException ex) {
        return json(HttpStatus.BAD_REQUEST, "INVALID_JSON_BODY",
                ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "Malformed JSON body");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, String>> missingParam(MissingServletRequestParameterException ex) {
        String msg = "Required request parameter '" + ex.getParameterName() + "' is missing";
        return json(HttpStatus.BAD_REQUEST, "MISSING_REQUIRED_PARAMETER", msg);
    }

    private static ResponseEntity<Map<String, String>> json(HttpStatusCode status, String error, String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", error);
        m.put("message", message);
        return ResponseEntity.status(status).body(m);
    }
}
